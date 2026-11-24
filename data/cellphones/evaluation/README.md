# Bộ 50 câu kiểm thử RAG CellphoneS — v1.0

Đã tạo 50 câu tiếng Việt từ **bản KB đã duyệt**, cố định bằng hash. Đã chạy đánh giá truy xuất offline bằng các hàm Java thực tế; **chưa đánh giá câu trả lời LLM**. Không dùng tài liệu Internet mới hơn để chấm bộ này nếu chưa cập nhật nguồn và nhãn có chủ đích.

## Các file

- `test_cases.jsonl`: 50 mẫu, một JSON mỗi dòng, dùng cho chương trình đánh giá.
- `test_cases.md`: bản dễ đọc để kiểm tra câu hỏi, đáp án và dẫn chứng.
- `source_lock.json`: phiên bản dữ liệu, SHA-256 HTML nguồn và content KB được phép dùng.
- `validation_report.json`: kết quả kiểm tra cấu trúc/dẫn chứng, không phải điểm chất lượng RAG.
- `retrieval-baseline.json`: ứng viên, điểm, guard và chấm bằng chứng của cả 50 câu từ lượt chạy JVM.
- `retrieval-summary.json`: chỉ số tổng hợp và theo nhóm.
- `retrieval-baseline.md`: báo cáo dễ đọc, giới hạn phương pháp và các câu còn thiếu nguồn.

## Kết quả retrieval baseline

Top-5, ngưỡng production 0.15, 18 chunk: **35/40 câu có nguồn tìm đủ bằng chứng**, 36/40 tìm được ít nhất một đoạn đúng, 43/48 nhóm bằng chứng được tìm thấy. Ba trong bốn câu không dấu thiếu nguồn. Với 10 câu fallback, một câu bị guard chặn; chín câu vẫn có ứng viên — đây là rủi ro tầng truy xuất, chưa phải kết quả đánh giá câu trả lời AI.

Đây là phép đo offline qua hàm production; repository cấp corpus cố định, không gọi SQL/API/Gemini. Chưa kiểm tra toàn luồng ticket, quyền truy cập hoặc query ghép lịch sử. Có 33 câu hòa điểm tại biên top-5; production chưa quy định ORDER BY. Báo cáo ghi cả lượt đảo thứ tự và không coi một thứ tự kiểm thử là hành vi database live được bảo đảm.

Để chạy lại từ repo root:

```powershell
cd backend
.\mvnw.cmd -B -ntp '-Dtest=RetrievalEvaluationMetricsTest,CellphonesRetrievalBaselineTest,AiHelpdeskFacadeRagInvariantsTest' '-Dhelpdesk.retrieval.writeReport=true' test
cd ..
.\.venv\Scripts\python.exe -X utf8 scripts/report_cellphones_retrieval.py
.\.venv\Scripts\python.exe -m unittest discover -s scripts -p 'test_*.py'
```

Chỉ sinh báo cáo tổng hợp sau khi Maven báo BUILD SUCCESS. JVM chỉ ghi artifact khi có cờ `helpdesk.retrieval.writeReport=true`; chạy test thông thường không ghi đè báo cáo. Các lệnh đánh giá không gọi model và không ghi KB/database. Giữ lại bản baseline trước khi thay thuật toán nếu cần so sánh nhiều phiên bản.

## Thử nghiệm top-10 (2026-11-24)

Đã đổi mặc định `RagSuggestionRequest.topK` khi bỏ trống từ 5 thành 10; client vẫn được chọn 1–10. Ngưỡng 0.15, tokenizer và cách tính điểm không đổi. Baseline top-5 được giữ nguyên để so sánh lịch sử; hash code trong baseline thuộc phiên bản trước thay đổi mặc định.

| Chỉ số truy xuất offline | Top-5 | Top-10 |
|---|---:|---:|
| Đủ mọi nhóm bằng chứng | 35/40 (87.5%) | 36/40 (90%) |
| Recall nhóm bằng chứng | 43/48 (89.58%) | 44/48 (91.67%) |
| Ít nhất một nguồn đúng | 36/40 | 36/40 |
| Câu fallback vẫn nhận ứng viên | 9/10 | 9/10 |

Câu `CPS-QA-030` có đủ ba nhóm nguồn ở top-10, gồm đoạn phí đổi trả hạng 6. Các câu 021, 022, 028, 032 vẫn thiếu nguồn. Đây là phép thử trên cùng tập 50 câu đã dùng phân tích lỗi, chưa phải kết quả tổng quát hóa trên tập độc lập.

Artifact mới: `retrieval-top10.json`, `retrieval-top10-summary.json`, `retrieval-top10.md`. **Chưa triển khai model chuẩn hóa, tách truy vấn, reranker hoặc hook đánh giá đủ bằng chứng.** Generator mock `GroundedRagGenerator` vẫn chỉ dùng ba đoạn đầu; mức cải thiện này chỉ thuộc tầng truy xuất, chưa chứng minh câu trả lời thực tế đã đủ ý. Tập ứng viên lớn hơn cũng có thể tăng nội dung không liên quan và chi phí khi dùng LLM.

Chạy lại top-10 từ repo root:

```powershell
cd backend
.\mvnw.cmd -B -ntp '-Dhelpdesk.retrieval.topK=10' '-Dhelpdesk.retrieval.reportName=retrieval-top10' '-Dhelpdesk.retrieval.writeReport=true' test
cd ..
.\.venv\Scripts\python.exe -X utf8 scripts/report_cellphones_retrieval.py --report-name retrieval-top10
.\.venv\Scripts\python.exe -m unittest discover -s scripts -p 'test_*.py'
```

## Phân bổ câu hỏi

| ID | Số câu | Nhãn | Mục đích |
|---|---:|---|---|
| CPS-QA-001–030 | 30 | ANSWER | Trả lời bằng chính sách đã có; gồm bảng, ngưỡng tiền, điều kiện, câu không dấu/sai chính tả/diễn đạt lại và phối hợp nhiều nguồn |
| CPS-QA-031–040 | 10 | ASK_CLARIFICATION | Có chủ đề phù hợp nhưng thiếu dữ kiện để kết luận cho khách |
| CPS-QA-041–050 | 10 | FALLBACK | Mục hoàn tiền tạm loại, dữ liệu đơn/giá trực tiếp, tài liệu chưa thu thập, nền tảng khác hoặc yêu cầu vượt quyền |

Các câu có dẫn chứng bao phủ **18/18 chunk đã nhập** từ `CPS-DOI-TRA` và `CPS-GIAO-HANG`. Đây là tập thử khởi đầu nhỏ, không đại diện đầy đủ mọi tình huống CSKH. Câu hỏi/đáp án được biên soạn cho nghiên cứu từ nguồn đã duyệt, không phải hội thoại khách hàng thật.

## Ý nghĩa trường dữ liệu

| Trường | Ý nghĩa |
|---|---|
| `id`, `question` | Mã ổn định và câu hỏi gửi cho hệ thống |
| `expected_action` | Hành vi kỳ vọng, không phải enum API backend |
| `reference_answer` | Câu trả lời tham chiếu; chấp nhận diễn đạt khác nếu giữ đúng ý và điều kiện |
| `required_facts` | Các điểm bắt buộc trong câu trả lời; rubric về nghĩa, không so khớp chuỗi máy móc |
| `must_not_claim` | Điều không được bịa/khẳng định; chỉ cần vi phạm một điểm là lỗi nội dung cần xem xét |
| `clarification_fields` | Các thông tin cần hỏi thêm cho câu thiếu dữ kiện |
| `category`, `difficulty`, `surface_form` | Nhóm nội dung, độ khó và kiểu diễn đạt do người biên soạn gán |
| `family_id` | Nhóm câu cùng ý/nghiệp vụ; dùng để tránh tách các câu gần nhau sang train và test sau này |
| `reason_code` | Lý do có thể trả lời hoặc phải fallback |
| `evidence` | Nguồn, raw/content hash, section, chunk index, chunk hash và các trích đoạn chính xác |
| `required_evidence_groups` | Danh sách nhóm bằng chứng cần có: AND giữa các nhóm, OR giữa các chunk key trong một nhóm |

`chunk_index` tính từ **0** trong payload của phiên bản đã khóa, không phải `kb_chunk.id` của một database cụ thể. Khóa như `CPS-GIAO-HANG#5` là bài `CPS-GIAO-HANG`, chunk index 5. Khi chạy thật cần ánh xạ citation backend về article/version/chunk index và kiểm tra hash trước khi chấm.

Các câu `ANSWER`/`ASK_CLARIFICATION` có trích đoạn từ `../kb/articles.jsonl`. Các câu fallback không gắn dẫn chứng giả: thiếu kiến thức không thể được chứng minh bằng cách trích một mục đã bị loại. `reason_code` cho biết loại giới hạn. Hai mục hoàn tiền bị loại và văn bản trong ảnh QR không được dùng làm đáp án.

## Cách dùng ở bước đánh giá tiếp theo

Chỉ gửi **`question`** (và thông tin đầu vào thật sự được phép nếu có) cho hệ thống. Không truyền reference answer, evidence hay các tiêu chí chấm vào prompt của hệ thống được đánh giá. Không nhập các file evaluation vào Knowledge Base. Script importer hiện chỉ đọc `../kb/articles.jsonl`, không quét thư mục evaluation.

Đánh giá tách biệt:

1. **Truy xuất:** với câu có evidence, kiểm tra top-K có ít nhất một chunk trong **mỗi** nhóm yêu cầu không; có thể tính recall theo nhóm và tỷ lệ đủ nhóm. Không tính câu fallback không có evidence như một lần retrieval miss tự động.
2. **Quyết định:** hành vi trả lời/hỏi thêm/fallback có phù hợp nhãn không. Không suy nhãn chỉ từ số đoạn đã truy xuất.
3. **Nội dung:** đúng các `required_facts`, không vi phạm `must_not_claim`, giữ điều kiện và ngoại lệ; nhân viên có thể duyệt câu trả lời AI.
4. **Dẫn nguồn:** citation tồn tại, thuộc đúng phiên bản được khóa và hỗ trợ thực sự cho phát biểu; citation ID hợp lệ chưa đủ chứng minh nội dung đúng.

Backend hiện trả `SUGGESTED`/`FALLBACK_TO_AGENT`/`FAILED`, chưa có enum `ASK_CLARIFICATION`. Nhãn hỏi thêm ở đây mô tả **mục tiêu sản phẩm**: cần đọc nội dung gợi ý để xác định hệ thống có hỏi đúng dữ kiện hay không. Lỗi mạng/LLM là lỗi vận hành riêng, không tự tính là một quyết định fallback đúng nghiệp vụ.

Không dùng Exact Match toàn câu làm thước đo duy nhất. Hai câu cùng `family_id` có thể thay đổi thành viên/giá trị/ngày nên không được sao chép máy móc đáp án. Không tự cho model tự chấm đạt rồi coi đó là kiểm duyệt độc lập.

## Tái tạo và kiểm tra

Chạy từ thư mục repo:

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/build_cellphones_evaluation.py
.\.venv\Scripts\python.exe -X utf8 scripts/validate_cellphones_evaluation.py
.\.venv\Scripts\python.exe -m unittest discover -s scripts -p 'test_cellphones*.py'
```

Builder chứa 50 mẫu được biên soạn có chủ đích; script chỉ thêm provenance và xuất file, không gọi LLM sinh lại, không truy cập mạng và không ghi database. Nó từ chối nguồn khác hash đã khóa để tránh âm thầm giữ đáp án cũ cho chính sách mới.

Validator kiểm tra số lượng/ID/câu hỏi trùng, tỷ lệ 30/10/10, nguồn đã duyệt, hash snapshot/content/chunk, trích đoạn nằm chính xác trong phần được phép dùng, bằng chứng cho từng nhóm, thông tin thiếu cần hỏi và 18/18 chunk có câu tham chiếu. Các kiểm tra này **không tự chứng minh đáp án đúng về nghĩa hoặc mô hình đã trả lời đúng**; phần nội dung được rà soát riêng.
