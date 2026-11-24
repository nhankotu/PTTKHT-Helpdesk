# RAG hai bước và model adapters

API hiện có `/api/v1/tickets/{ticketId}/ai/suggestions` dùng pipeline mới. Mặc định chạy **mock**, chưa gọi provider thật trong các lượt kiểm thử này.

```text
Quyền ticket + PUBLIC context → input gate
  → model 1, tối đa một lần → schema/provenance/kiểu dữ kiện/số và đơn vị
  → hỏi thêm hoặc truy xuất KB PUBLIC/PUBLISHED/current
  → model 2, tối đa một lần → kiểm tra quote và nguồn riêng từng ý
  → backend dựng nguyên section chính sách → agent duyệt
```

## Hợp đồng API

- POST bắt buộc JWT của AGENT/SUPERVISOR và header `Idempotency-Key` do client tạo trước khi gửi: 1–100 ký tự chữ/số/`.`/`_`/`:`/`-`.
- Giữ nguyên key và đầu vào khi retry. Cùng key + actor nhưng khác ticket/query/topK/ngữ cảnh PUBLIC trả 409. Key đang chạy hoặc gián đoạn trả 409; hệ thống không gọi lại model cho key đó.
- Request giữ nguyên `{ "customQuery": "...", "topK": 10 }`; bỏ topK thì mặc định 10. customQuery tối đa 2.000 ký tự như API cũ; query ghép từ lịch sử tối đa 8.000, tổng sources tối đa 16.000 ký tự trước khi gọi model.
- `SUGGESTED`: bản tham khảo chính sách, có citations. `CLARIFICATION_REQUIRED`: câu hỏi làm rõ do server dựng, không có citation giả. `FALLBACK_TO_AGENT`: thiếu nguồn hoặc gate từ chối. `FAILED`: lỗi vận hành/provider.
- Bản v1 không cho model viết kết luận cá nhân như “đơn của bạn miễn phí”. Model 2 chọn đoạn trích; server trả nguyên section, giữ bảng/chú thích/điều kiện, bỏ URL do model tạo. Cần agent kiểm tra mức liên quan và quyết định nghiệp vụ trước khi gửi. Không có auto-send hoặc tool thao tác đơn hàng.
- Citation trả exact quote snapshot ở `excerpt` và `intentIds` cho biết ý nào dùng nguồn đó. Nếu bất kỳ đoạn context đã gửi model 2 bị đổi/thu hồi/đổi scope, replay không trả draft cũ (409), lịch sử ẩn answer/citations và báo fallback, feedback không được chấp nhận.

Giới hạn một lần tính theo application HTTP attempt cho từng stage/key. Claim MySQL được commit trước provider call, sống qua rollback/crash. Provider timeout/429/JSON lỗi không được tự sửa, retry hay đổi provider. Key mới là lượt yêu cầu mới; hệ thống không thể xác định hai key khác nhau có phải cùng một lần bấm của khách không.

## Chọn provider/model độc lập

Giữ `AI_PROVIDER=mock` để các tính năng AI khác không vô tình gọi mạng. Override riêng từng stage:

```dotenv
AI_PROVIDER=mock
AI_RAG_PREPROCESS_PROVIDER=gemini
AI_RAG_PREPROCESS_MODEL=gemini-2.5-flash
AI_RAG_ANSWER_PROVIDER=openai-compatible
AI_RAG_ANSWER_MODEL=<ten-model-text-JSON-ban-dang-su-dung>
GEMINI_API_KEY=<secret-trong-moi-truong>
AI_OPENAI_API_KEY=<secret-trong-moi-truong>
AI_OPENAI_BASE_URL=https://api.openai.com/v1
AI_OPENAI_TOKEN_LIMIT_FIELD=max_completion_tokens
```

Không đặt các giá trị placeholder trên thành secret thật trong file được commit. Để chạy hoàn toàn offline, để cả hai provider là `mock` hoặc không khai báo override. Mock là bộ test trích nguồn và nhận diện vài từ khóa; không đại diện chất lượng chuẩn hóa/trả lời của model thật.

Adapter OpenAI-compatible hỗ trợ `/chat/completions`, JSON object và text response; không cam kết mọi model tương thích. Server local có thể cần `AI_OPENAI_TOKEN_LIMIT_FIELD=max_tokens`. Chỉ cho phép bỏ key khi đặt `AI_OPENAI_ALLOW_NO_KEY=true` và endpoint là loopback; endpoint remote cần HTTPS và key. Model không hỗ trợ contract thì FAILED, không thử lại API khác.

`StructuredModelAdapter` là SPI chung; Gemini/OpenAI-compatible chỉ chuyển request/response của provider. Muốn thêm provider, thêm bean có provider ID duy nhất và test giao thức. Pipeline và guardrail không phụ thuộc provider cụ thể.

## Guardrail và dữ liệu

- G1 kiểm tra mọi truy vấn phụ, fact ID/intent ID, provenance offsets UTF-16 và kiểu field; số có trong query phải được factRefs dẫn nguồn với đúng đơn vị. Thiếu dữ kiện không được tự điền. Điểm 0.8 là tín hiệu phụ, không thay thế các kiểm tra này.
- KB CellphoneS được binding bằng article/content/section hash trong `src/main/resources/rag/cellphones-policy-v1.json`, không đọc đáp án evaluation. Khi nguồn đổi hash, rule bị vô hiệu và chuyển agent đến khi review lại.
- Retrieval gộp câu gốc và tối đa ba ý, có chuẩn hóa dấu cho keyword, RRF và tie-break ổn định; giữ các section bắt buộc cho từng intent. Không phải vector search/semantic reranker. Context tối đa 10 đoạn và 24.000 ký tự; đây là giới hạn ký tự, không phải tokenizer của provider.
- Chỉ nhận text model 2 bằng quote; backend dựng section từ source để tránh đảo điều kiện, đơn vị hoặc chọn nhầm dòng bảng thành kết luận cho khách. Exact quote không chứng minh nguồn phù hợp với câu hỏi; agent vẫn cần duyệt. Các phép lọc injection giảm rủi ro, không bảo đảm phát hiện mọi biến thể.
- Toàn bộ context được kiểm tra lại sau model 2 và tại lúc lưu, dùng khóa đọc chỉ trên các chunk liên quan và query timeout 3 giây. Cache/history dùng fingerprint cả context. Snapshot quote/intent được lưu trong `ai_citation`.
- `rag_request.stage_metadata` lưu provider/model/latency/token usage khi có, plan đã kiểm tra và source hashes/IDs. Không log raw prompt/key. Query quá dài chỉ lưu hash và số ký tự để tránh tràn cột TEXT.
- Mỗi HTTP response giới hạn 128 KiB, request 256 KiB, JSON text 32.000 ký tự; không theo redirect. Model 1 tối đa 5 giây, model 2 12 giây trong deadline 25 giây của pipeline. Circuit mở 30 giây sau ba lỗi transport liên tiếp theo endpoint. DB/network khác vẫn có thể làm thời gian HTTP tổng dài hơn; đây không phải SLA cứng toàn hệ thống.

## Triển khai và kiểm thử

Flyway thêm V3–V5; không sửa V1/V2. Cần khởi động lại backend để nạp code và migration. OpenAPI và TypeScript types đã cập nhật; FE hiện chưa có màn hình làm việc của agent để trình bày pipeline này.

```powershell
cd backend
.\mvnw.cmd -B -ntp test
```

Integration test dùng MySQL Testcontainers riêng. Các test HTTP adapter chỉ gọi server giả lập loopback; không gửi KB hoặc hội thoại ra provider thật. Chưa chạy lại bộ 50 câu bằng model thật, chưa có điểm chất lượng câu trả lời hoặc benchmark chi phí/độ trễ thực tế. Top-5/top-10 cũ là artifact lịch sử của thuật toán trước thay đổi này.
