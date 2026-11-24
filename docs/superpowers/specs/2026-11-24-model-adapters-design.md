# Adapter cho model trong pipeline RAG

Đã triển khai v1 và kiểm thử giao thức bằng HTTP server loopback; chưa gọi provider thật.

Theo yêu cầu bổ sung của người dùng, hai vai trò trong RAG được chọn provider/model độc lập. Adapter không có quyền thay guardrail, truy xuất thêm tài liệu hay gọi lại provider khác khi lỗi.

```text
QueryPreprocessor ─┐
                  ├→ ModelAdapterRegistry → StructuredModelAdapter
GroundedAnswerer ──┘                         ├→ GeminiStructuredAdapter
                                           └→ OpenAiCompatibleAdapter
             Mock xử lý tại role adapter, không có HTTP
```

`StructuredModelAdapter.Request` gồm model, system instruction, input JSON, timeout và giới hạn output. `Reply` gồm JSON và số token input/output nếu provider có trả; thiếu usage ghi null. Provider-specific envelope được kiểm tra trước khi JSON đi vào guardrail chung.

Adapter Gemini giữ `system_instruction` riêng và đọc generateContent. Adapter OpenAI-compatible dùng `/chat/completions`, system/user messages riêng, `response_format=json_object`, `stream=false`, một lựa chọn, từ chối tool call/refusal/output chưa hoàn tất. Schema nghiệp vụ vẫn do Java kiểm tra, không coi JSON mode là schema validation. Không gửi tools hoặc quyền thao tác.

Transport dùng HTTPS hoặc HTTP loopback đã cấu hình, không nhận endpoint từ khách, không theo redirect. Giới hạn body response 128 KiB ngay trong subscriber, request 256 KiB, JSON text 32.000 ký tự. Deadline bao gồm thời gian đọc body sau header; timeout hủy future. Không retry hoặc tự đổi model. Sau ba lỗi transport liên tiếp, circuit mở 30 giây theo endpoint. Dấu stage trong MySQL ngăn replay/crash gọi lại cùng logical request.

Model phải hỗ trợ text + JSON object và system instruction. Không cam kết mọi model trên thị trường tương thích. Với model/API không hỗ trợ các tham số này, trả FAILED và agent xử lý; không gọi thử lại với tham số khác. Cấu hình `max_completion_tokens` cho OpenAI; endpoint local tương thích có thể cần `max_tokens`. Chỉ cho phép hai tên tham số này.

## Cấu hình

Mặc định `AI_PROVIDER=mock`, cả hai bước chạy mock. Cấu hình stage để override provider chung, giữ phân loại ticket cũ độc lập:

```dotenv
AI_PROVIDER=mock
AI_RAG_PREPROCESS_PROVIDER=gemini
AI_RAG_PREPROCESS_MODEL=gemini-2.5-flash
AI_RAG_ANSWER_PROVIDER=openai-compatible
AI_RAG_ANSWER_MODEL=<model-ho-tro-JSON-cua-ban>
GEMINI_API_KEY=<cau-hinh-trong-moi-truong>
AI_OPENAI_API_KEY=<cau-hinh-trong-moi-truong>
AI_OPENAI_BASE_URL=https://api.openai.com/v1
AI_OPENAI_TOKEN_LIMIT_FIELD=max_completion_tokens
```

Không bật cấu hình này trong test thông thường. Để dùng endpoint local, ví dụ URL loopback `/v1`, đặt model đúng tên server cung cấp. Chỉ khi server local không yêu cầu key mới đặt `AI_OPENAI_ALLOW_NO_KEY=true`; remote vẫn bắt buộc key và HTTPS. Việc tương thích phải được kiểm thử với endpoint/model cụ thể trước khi dùng thật.

Thêm provider mới: implement `StructuredModelAdapter`, đăng ký bean với provider ID duy nhất, map Request sang API và envelope sang Reply, bổ sung test giao thức cho JSON/timeout/body limit/refusal. Không sửa pipeline hoặc các gate để bỏ qua lỗi provider mới.

Tham khảo hợp đồng Chat Completions: [OpenAI API reference](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create). Đây là tham khảo giao thức; tên model được người vận hành cấu hình, không hard-code theo danh sách model hiện hành.
