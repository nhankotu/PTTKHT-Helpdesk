# Thiết kế RAG hai bước với guardrail

Trạng thái: đã triển khai v1 trong workspace theo yêu cầu làm cả thiết kế và code. Phần “Quyết định triển khai v1” cuối tài liệu cập nhật các lựa chọn bảo thủ hơn sau code review và được ưu tiên khi khác mô tả ban đầu. Chưa gọi provider thật để đánh giá chất lượng. Model 1 chỉ gọi một lần để làm rõ đầu vào; model 2 có guardrail riêng. Không lặp để tăng điểm tự chấm.

## Mục tiêu và lựa chọn

Giúp câu không dấu, viết tắt và câu nhiều ý tìm đúng KB CellphoneS, đồng thời giữ hành vi hỏi thêm/chuyển nhân viên khi thiếu dữ kiện hoặc nguồn.

Chọn pipeline cố định, Java kiểm tra từng đầu ra. Phương án một model tự sửa nhiều lượt bị loại vì trái giới hạn một lần và không bảo đảm sửa đúng. Phương án chỉ tăng top-K đã thử: đủ bằng chứng tăng 35/40 lên 36/40 nhưng còn thiếu xử lý đầu vào. Đây là lựa chọn kỹ thuật đề xuất theo hướng người dùng đã nêu.

Giai đoạn đầu chỉ tạo gợi ý cho agent duyệt; chưa gửi trả lời tự động, chưa truy cập hệ thống đơn hàng, chưa cho model gọi tool, URL, SQL hay sửa dữ liệu nghiệp vụ. Không bổ sung model chấm thứ ba.

## Luồng xử lý

```text
Xác thực + kiểm tra quyền ticket + lấy ngữ cảnh PUBLIC
  -> G0: giới hạn đầu vào, kiểm tra yêu cầu vượt quyền/injection
  -> Model 1 (tối đa 1 lần): chuẩn hóa + trích dữ kiện + tách ý
  -> G1: kiểm tra schema, provenance, dữ kiện và độ mơ hồ
       -> hỏi thêm / fallback nếu không qua
  -> Retrieve bằng câu gốc + truy vấn chuẩn hóa cho từng ý
  -> G2: lọc quyền/phiên bản, gộp, giới hạn context, kiểm tra nguồn sơ bộ
       -> không nguồn / quá giới hạn / thiếu dữ kiện bắt buộc: dừng
  -> Model 2 (tối đa 1 lần): trả lời / hỏi thêm / từ chối theo tài liệu
  -> G3: kiểm tra đầu ra, nguồn từng ý, trạng thái nguồn và dữ liệu nhạy cảm
       -> trả gợi ý cho agent duyệt hoặc fallback
```

Một lần là mỗi logical request: tắt retry tự động ở cả hai adapter; JSON hỏng cũng không gọi lại model để sửa. Dùng request ID chống xử lý lặp do client retry, gắn với actor/ticket/input hash; request khác không được dùng nhầm kết quả. Lượt khách trả lời câu hỏi làm rõ là một logical request mới và được xử lý lại từ đầu.

Giới hạn này tính theo HTTP attempt, không chỉ response thành công. Lưu claim stage bền vững trước khi phát HTTP, unique theo request ID/stage và cập nhật nguyên tử để chặn worker chạy đồng thời. Sau crash/timeout không biết provider đã nhận hay chưa: stage đã claim không được gọi lại; request đó trả fallback có mã INTERRUPTED_OR_UNKNOWN. Chấp nhận trường hợp claim xong nhưng chưa kịp gọi provider cũng phải fallback. Không tuyên bố exactly-once ở provider; mục tiêu là at-most-once attempt từ hệ thống cho mỗi stage/request.

## Model 1: chuẩn hóa có giới hạn

Input: câu gốc và hội thoại PUBLIC của đúng ticket mà actor có quyền xem, mỗi message có ID. Không gửi INTERNAL, token đăng nhập, thông tin khách khác. Không gửi KB cho bước này. Server giữ câu gốc bất biến.

Đầu ra JSON nghiêm ngặt:

```json
{
  "decision": "READY",
  "normalizedQuestion": "Tôi muốn hỏi phí giao hàng.",
  "intents": [{"id": "i1", "code": "SHIPPING_FEE", "query": "chính sách phí giao hàng"}],
  "facts": [],
  "missingFields": ["orderValue", "membershipTier"],
  "ambiguities": [],
  "confidence": 0.9
}
```

`decision`: READY / ASK_CLARIFICATION / OUT_OF_SCOPE. Tối đa 3 ý; intent code thuộc danh mục nghiệp vụ do server quản lý, có UNKNOWN. `facts` chỉ gồm field/value và evidence `{messageId, start, end, quote}` trỏ đúng đoạn câu gốc/PUBLIC. Giá trị là dữ kiện khách khai, chưa phải thông tin đã xác minh bởi cửa hàng. Model không trả chain-of-thought.

Cho phép thêm dấu, sửa lỗi gõ, mở rộng viết tắt rõ nghĩa và liên kết đại từ với ngữ cảnh rõ ràng. Ví dụ “ship” thành “phí giao hàng”. **Không tự thêm giá trị đơn, cấp thành viên, ngày nhận, lỗi nhà sản xuất hay kết quả phê duyệt đổi trả.** Mơ hồ có nhiều cách hiểu thì giữ trong `ambiguities`, không ép chọn để đạt điểm cao.

G1 do Java thực thi: reject field lạ/sai kiểu/quá dài; xác minh message ID, offsets và quote; giữ số, đơn vị, phủ định và điều kiện quan trọng. Số liệu trong normalizedQuestion phải có provenance; việc đổi đơn vị chỉ qua hàm chuẩn hóa có kiểm thử. Kiểm tra này phát hiện nhiều lỗi nhưng không chứng minh tương đương ngữ nghĩa hoàn toàn.

Áp dụng cùng kiểm tra cho toàn bộ output model 1, đặc biệt `intents[].query`; không chỉ kiểm tra normalizedQuestion. Mỗi query mang danh sách fact reference đã xác minh. Nghiêm cấm số/điều kiện mới trong query hoặc các field văn bản khác; query không đạt thì reject cả plan. Model 2 chỉ nhận các field đã qua kiểm tra, không nhận JSON thô hay phần giải thích bị loại của model 1.

Server quản lý danh mục field và quy tắc dữ kiện bắt buộc theo intent và loại câu hỏi. Hỏi chính sách chung có thể READY dù thiếu thông tin cá nhân; hỏi mức phí cụ thể cần hỏi dữ kiện làm thay đổi mức phí. Không tin riêng danh sách missingFields của model; G2 bổ sung điều kiện từ chính sách có cấu trúc đã duyệt. Intent UNKNOWN hoặc chưa có quy tắc an toàn thì chuyển agent nếu không thể trả chính sách chung.

Điểm confidence chỉ là tín hiệu phụ. Đề xuất thử ngưỡng 0.8 trên tập dev, cấu hình được; schema/provenance/ambiguity sai vẫn dừng dù confidence=1.0. Điểm thấp thì hỏi làm rõ bằng mẫu server hoặc chuyển agent; không chạy model 1 lần hai.

## Retrieve và G2: đủ nguồn trước khi trả lời

Tìm bằng câu gốc và tối đa 3 truy vấn ý đã qua G1. Mỗi truy vấn lấy tối đa 10 đoạn, gộp theo chunk ID/version. Dữ liệu kỳ vọng trong evaluation tuyệt đối không đi vào truy xuất.

Luôn lọc quyền KB phù hợp actor và chế độ trả lời; chỉ active + PUBLISHED. Nếu tương lai AUTO_REPLY thì chỉ PUBLIC + allow_auto_reply. Không cho model đổi filter hay cung cấp chunk ID để đọc thêm. Kiểm tra quyền ticket phải nằm ở server, không suy quyền từ nội dung câu hỏi.

Giai đoạn đầu dùng cách gộp theo thứ hạng (RRF), tie-break theo version ID/chunk index/ID để ổn định; chưa tuyên bố có semantic reranker. Context tối đa 10 đoạn và ngân sách cấu hình ban đầu 12.000 token; giữ nguyên bảng và chú thích đi cùng. Đảm bảo mỗi intent có ứng viên; nếu giới hạn buộc bỏ nguồn bắt buộc thì fallback, không âm thầm cắt mất điều kiện.

Với các nhóm chính sách đã có cấu trúc do người quản trị duyệt, kiểm tra các section và field bắt buộc theo intent. Bản đánh giá có nhãn không được dùng làm bảng định tuyến production. Không coi điểm keyword >=0.15 hay “mỗi intent có một đoạn” là chứng minh đủ nghĩa. G2 là kiểm tra sơ bộ; model 2 phải quyết định lại từ nội dung và G3 hậu kiểm. Nguồn mâu thuẫn/không rõ hiệu lực thì chuyển agent, không tự lấy số mới nhất.

### Policy registry v1 có giới hạn

Chỉ dùng file cấu hình versioned trong repo, schema kiểm tra khi khởi động; chưa xây rule engine hoặc UI quản trị. Mỗi rule gắn article code + content SHA-256 đã duyệt + section/content hash; resolve sang chunk/version thực tế, không dùng DB ID cố định. KB đổi hash, thu hồi hoặc thiếu rule thì vô hiệu hóa rule liên quan và fallback với RULE_SOURCE_MISMATCH; không tiếp tục dùng điều kiện cũ. Quy tắc được trích và review từ KB, không đọc test_cases/reference_answer/evidence của bộ đánh giá.

| Intent v1 | Nguồn/nhóm section | Dữ kiện dùng khi trả lời trường hợp cụ thể |
|---|---|---|
| CANCEL_ORDER | Điều kiện và phương thức hủy giao dịch | received; nếu đã nhận thì không áp dụng nhánh hủy trước nhận |
| SHIPPING_FEE | Chi phí vận chuyển, gồm bảng và chú thích | orderValue, membershipTier, bulkyItem; chỉ hỏi field chưa biết mà có thể làm đổi kết quả của nhánh đang xét |
| RETURN_PACKAGING | Hướng dẫn gửi trả sản phẩm | Có thể trả hướng dẫn chung mà không cần số ngày/hạng thành viên |
| RETURN_SHIPPING | Điều kiện đổi trả, nhánh chi phí | manufacturerFaultConfirmed, returnApproved, returnReason khi kết luận ai chịu phí; phân biệt khách khai với cửa hàng đã xác minh |
| ONLINE_RETURN | Chính sách đổi mới mua từ xa | purchaseChannel, elapsedDays và mốc bắt đầu tính ngày khi kết luận áp dụng thời hạn/hỗ trợ |
| GIFT_PREPAYMENT | Giao hàng cho người nhận quà, bảng thanh toán trước | giftOrder và các điều kiện của dòng bảng tương ứng |

Các intent khác đi vào OTHER_POLICY: chỉ gợi ý trích chính sách chung cho agent, không tính mức phí hoặc xác nhận đủ điều kiện cho cá nhân. Danh mục field trên đóng; builder định nghĩa kiểu/enum và mẫu hỏi tương ứng. Rule v1 chỉ kiểm tra nhánh điều kiện, presence và các số/đơn vị có trong đoạn nguồn đã khóa; chưa xây bộ tính phí/hoàn tiền. Đáp án tính toán hoặc suy luận vượt các rule này phải fallback. Hạn chế này giữ phạm vi triển khai nhỏ và không biến đối chiếu quote thành lời hứa kiểm chứng ngữ nghĩa tự động.

Câu 30 cần giữ ít nhất nhóm đóng gói, phí gửi trả và điều kiện hỗ trợ đơn online. Nếu không chứng minh đủ cả ba thì không đưa ra kết luận đầy đủ cho trường hợp khách.

## Model 2 và G3: câu trả lời có bằng chứng

Input tách rõ system instruction, câu gốc, bản chuẩn hóa đã kiểm tra, dữ kiện có provenance và các đoạn KB có ID/version. Nội dung khách, đầu ra model 1 và KB đều là dữ liệu không đáng tin, không có quyền ghi đè instruction. Model không có tool hoặc quyền truy cập thêm.

Output JSON gồm `decision` ANSWER / ASK_CLARIFICATION / FALLBACK, `answerParts` theo intent ID, mỗi phần có text và `supports` gồm chunk ID, version ID, quote chính xác; thêm `missingFields`, `reasonCode`, confidence. Không nhận URL tự tạo làm nguồn. Server dựng citation/link từ metadata KB. Tối đa 8 phần, tổng text 4.000 ký tự; schema không cho field lạ.

G3 kiểm tra schema và consistency theo decision. ANSWER cần đủ mọi intent bắt buộc, không còn missingField quyết định kết quả, mỗi phần có support thuộc context và quote tồn tại nguyên văn trong chunk. Re-read nguồn trước khi lưu để loại nguồn vừa thu hồi/thay quyền. Không lưu draft khi kiểm tra thất bại. ASK chỉ hỏi field trong danh mục, không được kèm kết luận mức phí/đủ điều kiện; câu hỏi làm rõ dựng bằng mẫu server. FALLBACK không kèm đáp án khẳng định.

Citation tồn tại và quote khớp **không chứng minh text được nguồn hỗ trợ về nghĩa**. Với số tiền, ngày, tỷ lệ và điều kiện có cấu trúc, Java đối chiếu thêm theo policy rules đã duyệt. Trường hợp suy luận chưa kiểm tra được giữ ở gợi ý cần agent duyệt, không tự gửi cho khách. Không cam kết chống được mọi prompt injection hay hallucination.

Kiểm tra output cho yêu cầu lộ bí mật, dữ liệu khách khác, lời khẳng định đã hủy đơn/hoàn tiền/thao tác hệ thống dù không có tool. Render dạng text hoặc Markdown an toàn, không HTML tùy ý, ảnh từ xa hay link do model phát sinh. Không đưa secret vào context để trông chờ bộ lọc che lại.

## Lỗi, ngân sách và tích hợp repo

Giữ AiHelpdeskFacade là điểm điều phối. Thêm interface QueryPreprocessor, QueryPlanValidator, EvidenceAssembler và DraftValidator; Gemini adapter chỉ gọi model và parse schema. Model 1/2 có promptVersion, model config và giới hạn output riêng. Có thể dùng chung GeminiClient sau khi bổ sung schema validation, giới hạn response và timeout/circuit breaker. Chế độ mock không được giả nhận kết quả model thật.

Ngân sách đề xuất: model 1 tối đa 5 giây, model 2 tối đa 12 giây, tổng request 25 giây gồm retrieve/validation, dùng deadline chung và circuit breaker. Input khách tối đa 8.000 ký tự; lịch sử theo budget, giữ nguyên message được chọn và provenance. Context quá dài không được cắt âm thầm dữ kiện liên quan; hỏi lại hoặc chuyển agent. Giới hạn token/provider cần kiểm tra khi triển khai adapter.

Timeout/429/5xx/JSON lỗi của model 1 hoặc 2: dừng và lưu lý do, không retry, không bỏ qua gate để đi tiếp. G0/G1 không qua thì model 2 có số lượt gọi bằng 0. Không đủ KB thì không gọi model 2. G3 không qua thì không trả draft lỗi.

Cần bổ sung kết quả `CLARIFICATION_REQUIRED` riêng, cập nhật enum/DB constraint/migration nếu có, DTO/OpenAPI và FE; không gán hỏi thêm thành SUGGESTED có citation giả. Phân biệt fallback nghiệp vụ và FAILED do vận hành. Lưu metadata stage, code lý do, model/promptVersion, latency, usage, số lượt gọi, source IDs/version và quyết định gate với request ID. Provenance gắn vào record được phân quyền, không log toàn văn prompt, thông tin nhạy cảm hoặc API key.

Các khoảng trống hiện tại cần xử lý khi triển khai: guard chỉ dò marker; parser Gemini còn ép kiểu và clamp điểm thay vì reject; citation check chưa chứng minh semantic grounding; cần kiểm tra lại quyền/phiên bản ở ranh giới lưu; GroundedRagGenerator mock chỉ dùng ba đoạn đầu. Không coi những kiểm tra hiện có là guardrail mới đã hoàn thành.

## Ví dụ và tiêu chí kiểm thử

| Input/tình huống | Kết quả cần đạt |
|---|---|
| “minh moi dat hang chua nhan, gio huy dc ko shop?” | Giữ điều kiện chưa nhận, chuẩn hóa câu hỏi hủy; chỉ kết luận theo KB |
| “shop tính ship bao nhiêu?” | Hỏi giá trị đơn/hạng thành viên khi cần báo mức phí cụ thể; không tự điền |
| Câu 30 đóng gói + phí đổi trả + online | Không mất ý, đủ nguồn theo từng phần hoặc fallback |
| Model 1 đổi 10 ngày thành 15 ngày, thêm “đã xác nhận lỗi” | G1 chặn, không gọi model 2 |
| Model 1 trả JSON lỗi hoặc confidence=1 với provenance giả | Dừng, không tự sửa bằng lượt gọi mới |
| Injection trong câu gốc, bản chuẩn hóa hoặc KB | Không đổi quyền, không tool, không lộ secret; bộ test gồm biến thể dấu/Unicode và đo false positive |
| Model 2 bịa ID/quote, dùng nguồn vừa thu hồi | G3 chặn, không lưu gợi ý trả lời |
| Quote đúng nhưng kết luận trái điều kiện, đổi phủ định/số tiền | Rule chặn nơi có policy cấu trúc; kiểm thử đánh giá ngữ nghĩa/agent review cho phần còn lại |
| Một intent có nguồn, ý còn lại thiếu | Không trả lời đầy đủ giả; hỏi thêm nếu thiếu dữ kiện khách, fallback nếu thiếu KB |
| Timeout/429/5xx hoặc client gửi lặp request ID | Giới hạn mỗi stage <=1 call, lỗi có lý do, không nhân đôi draft |
| User không có quyền ticket/KB hoặc có INTERNAL note | Chặn trước khi gọi model; payload không chứa dữ liệu bị cấm |

Đánh giá riêng: chuẩn hóa không đổi nghĩa, phát hiện thiếu field, recall theo từng ý, quyết định trả lời/hỏi/fallback, chất lượng và grounding của đáp án, số lượt gọi/latency/cost. Dùng bộ 50 câu hiện tại làm regression; bổ sung dev và holdout độc lập theo family, không chỉnh ngưỡng theo holdout. Không báo tỷ lệ retrieval 90% là độ chính xác trả lời. Acceptance chức năng: mọi case gate/error trên có test âm, hai stage <=1 call, mọi ANSWER được kiểm tra nguồn; thử adversarial không phải chứng minh an toàn tuyệt đối.

## Tham khảo

[OWASP LLM Prompt Injection Prevention](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html): kiểm tra input/output, tách instruction và dữ liệu, giới hạn quyền, coi output model là dữ liệu chưa tin cậy. Các lựa chọn và giới hạn đã triển khai được ghi ở mục v1 bên dưới.

## Quyết định triển khai v1 sau code review

- API giữ đường dẫn cũ nhưng bắt buộc client gửi `Idempotency-Key` trước lần POST đầu; không sinh key chỉ ở response. Key đang chạy/gián đoạn hoặc cached context không còn hợp lệ trả 409, không gọi lại model. 3-argument facade chỉ còn dùng cho isolated legacy tests/baseline; production yêu cầu phương thức có key.
- Hai bước chọn `mock`, `gemini`, `openai-compatible` độc lập qua adapter SPI; xem [model-adapters-design.md](2026-11-24-model-adapters-design.md) và [hướng dẫn chạy](../../../backend/docs/RAG_PIPELINE.md).
- G1 kiểm tra unit, typed fact, numeric factRefs và ID cố định `i1..i3`, `f1..f20`. Provenance/plan đã xác minh và source hashes được lưu trong audit DB có phân quyền qua ticket, không log raw prompt.
- Model 2 v1 chỉ chọn quote; `text` phải bằng quote hoặc các quote nối newline, không chấp nhận diễn giải/kết luận cá nhân. Backend dựng bản tham khảo từ nguyên section chứa quote để giữ điều kiện/bảng/chú thích, giới hạn 16.000 ký tự và 60.000 UTF-8 bytes. Không triển khai bộ tính phí/xác nhận đủ điều kiện. Đây là cách thực thi policy-only cho mọi intent, kể cả OTHER_POLICY, thay vì trông chờ model tuân thủ prompt.
- Coverage kiểm tra nguồn theo từng intent, không dùng union chung. Quote snapshot và intentIds được lưu/trả trong citation; agent vẫn cần đánh giá mức liên quan. Không tuyên bố kiểm chứng ngữ nghĩa tổng quát.
- Fingerprint, kiểm tra thu hồi/scope/content và khóa nguồn bao phủ toàn bộ context gửi model 2, kể cả đoạn không được cite. Khóa chỉ trên các chunk liên quan, query timeout 3 giây. Lịch sử redacts answer/citations của draft không còn xác minh được nguồn; feedback bị chặn. Draft legacy thiếu snapshot không được coi là actionable.
- Nguồn có instruction trong article code/heading/content bị loại trước model; intent/fact IDs không phải free text. Metadata source cũng nằm trong fingerprint.
- Query quá dài chỉ lưu marker/hash/char count cho fallback, tránh tràn cột TEXT. customQuery API giữ giới hạn 2.000 ký tự; query ghép lịch sử 8.000, tổng sources 16.000 ký tự. Context dùng giới hạn 24.000 ký tự, chưa dùng tokenizer để chứng minh 12.000 token.
- Flyway V3–V5 bổ sung clarification outcome, stage claims/audit/fingerprint và citation snapshots; không sửa V1/V2. Token usage không có từ provider ghi null; model/prompt/latency/stage decisions được lưu.
- Những lựa chọn trên phục vụ chế độ agent duyệt; chưa có AUTO_REPLY, provider thật, semantic reranker hay kết quả đánh giá LLM trên 50 câu. Artifact top-5/top-10 trước đó là baseline lịch sử, không đại diện pipeline mới.
