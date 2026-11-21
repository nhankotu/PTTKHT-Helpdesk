# THIẾT KẾ USE CASE — THÀNH VIÊN 4 (UC06 & UC07)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phân hệ:** Quản lý Knowledge Base & AI RAG gợi ý câu trả lời (KB & AI RAG)  
**Phụ trách:** Thành viên 4 (TV4)  
**Trạng thái:** Đã chuẩn hóa theo bản hợp nhất toàn hệ thống (MySQL 8.0+, 7 trạng thái Ticket)

---

## 1. Phạm vi, Actor và ranh giới hệ thống

- **UC06 — Quản lý bài viết Knowledge Base:** Cho phép Agent soạn thảo/cập nhật phiên bản nháp và gửi duyệt; Supervisor phê duyệt, xuất bản (kèm phân đoạn & lập chỉ mục tìm kiếm), từ chối hoặc thu hồi bài viết hết hiệu lực.
- **UC07 — AI RAG gợi ý câu trả lời có dẫn nguồn:** Truy xuất các đoạn bài viết KB còn hiệu lực (`PUBLISHED`, `is_active = TRUE`) theo đúng quyền truy cập, sinh câu trả lời nháp có dẫn nguồn cụ thể cho Agent xem/sửa/gửi, hoặc chuyển sang Agent xử lý thủ công (**Fallback**) khi thiếu nguồn, nguồn mâu thuẫn, khách yêu cầu người hay AI gặp lỗi.

### Actor & Ranh giới
- **Agent:** Soạn/sửa/gửi duyệt/tìm kiếm KB (UC06); yêu cầu gợi ý AI RAG, kiểm tra trích dẫn, chỉnh sửa áp dụng sang UC03 và đánh giá chất lượng gợi ý (UC07).
- **Supervisor:** Duyệt/xuất bản, từ chối và thu hồi bài viết KB (UC06); xem phản hồi chất lượng AI (UC07).
- **Thành phần nội bộ (KHÔNG phải Actor):** `KB Indexer`, `KnowledgeRetriever`, `RAG Engine / LLM Service`, `Citation Validator`.
- **Biểu đồ tham chiếu:** `../Bieu_do/UseCase/UC06_UseCase.png`, `../Bieu_do/UseCase/UC07_UseCase.png`, `../Bieu_do/SSD/UC06_SSD.png`, `../Bieu_do/SSD/UC07_SSD.png`, `../Bieu_do/Tong_the/ERD_Tong_The.png`.

---

## 2. Đặc tả chi tiết UC06 — Quản lý bài viết Knowledge Base

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC06 — Quản lý bài viết Knowledge Base |
| **Actor chính / phụ** | Chính: Agent (soạn/sửa/gửi duyệt), Supervisor (duyệt/xuất bản/từ chối/thu hồi). Phụ: Admin (cấu hình danh mục). |
| **Mục tiêu** | Kiểm soát vòng đời phiên bản bài viết KB, bảo đảm chỉ nội dung đã được Supervisor duyệt (`PUBLISHED`) và còn hiệu lực mới được lập chỉ mục cho tìm kiếm và AI RAG. |
| **Tiền điều kiện** | Người dùng đã đăng nhập với vai trò `AGENT` hoặc `SUPERVISOR`; danh mục KB (`category`) tồn tại. |
| **Sự kiện kích hoạt** | Agent tạo bài viết mới / phiên bản mới, hoặc Supervisor mở hàng đợi bài chờ duyệt / chọn thu hồi bài đang xuất bản. |
| **Luồng chính** | 1. Agent tạo bài `kb_article` (hoặc tạo `version_number` mới cho bài hiện có), nhập tiêu đề, danh mục, `access_scope` (`PUBLIC` / `INTERNAL_AGENT`), cờ `allow_auto_reply` và nội dung → hệ thống kiểm tra dữ liệu bắt buộc và lưu `kb_version(status = 'DRAFT')`.<br>2. Agent bấm **Gửi duyệt** → chuyển `IN_REVIEW` và thông báo Supervisor.<br>3. Supervisor mở bài `IN_REVIEW`, kiểm tra nội dung và chọn **Phê duyệt & Xuất bản**.<br>4. Trong transaction MySQL: chuyển phiên bản mới sang `PUBLISHED` (`reviewed_by`, `published_at`), cập nhật `kb_article.current_published_version_id`; nếu bài đã có bản `PUBLISHED` cũ thì chuyển bản cũ sang `ARCHIVED` (`archived_at`) và đặt `kb_chunk.is_active = FALSE` cho bản cũ.<br>5. Bộ lập chỉ mục nội bộ chia đoạn (`kb_chunk`), tạo chỉ mục tìm kiếm/vector (`embedding_ref`) và đánh dấu `is_active = TRUE`. |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Từ chối duyệt:** Supervisor chọn **Từ chối**, bắt buộc nhập `review_note` → chuyển `REJECTED`, thông báo Agent chỉnh sửa lại.<br>- **A2 — Thu hồi bài viết:** Supervisor chọn **Thu hồi** trên bài đang `PUBLISHED` → chuyển `ARCHIVED`, đặt `kb_article.current_published_version_id = NULL`, đặt `kb_chunk.is_active = FALSE` để RAG ngừng dùng ngay.<br>- **E1 — Thiếu tiêu đề/nội dung:** Trả `422`, giữ form.<br>- **E2 — Agent gọi API duyệt/xuất bản/thu hồi:** Từ chối `403 Forbidden`.<br>- **E3 — Lỗi lập chỉ mục:** Ghi nhận lỗi/rollback hoặc đánh dấu hàng đợi đồng bộ lại để tránh lệch giữa trạng thái `PUBLISHED` và chỉ mục tìm kiếm. |
| **Hậu điều kiện** | Mỗi bài viết chỉ có tối đa 1 phiên bản `PUBLISHED` (bảo đảm bởi `published_article_key` + `UNIQUE` trong MySQL 8.0); bài thu hồi bị loại khỏi chỉ mục RAG ngay lập tức. |
| **Quy tắc nghiệp vụ** | - Không ghi đè trực tiếp phiên bản đã `PUBLISHED`; mọi cập nhật phải tạo `version_number` mới.<br>- Bài `INTERNAL_AGENT` không bao giờ được dùng cho chế độ tự động trả lời khách. |

---

## 3. Đặc tả chi tiết UC07 — AI RAG gợi ý câu trả lời có dẫn nguồn

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC07 — AI RAG gợi ý câu trả lời có dẫn nguồn |
| **Actor chính / phụ** | Chính: Agent. Phụ: Hệ thống tự động kích hoạt bất đồng bộ cho ticket mới (nếu bật cấu hình). |
| **Mục tiêu** | Truy xuất nguồn KB hợp lệ, sinh câu trả lời nháp có dẫn nguồn minh bạch giúp Agent xử lý nhanh; tự động fallback sang Agent khi AI không đủ căn cứ hoặc gặp sự cố. |
| **Tiền điều kiện** | Ticket tồn tại và chưa ở trạng thái `CLOSED` / `CANCELLED`; người gọi có quyền xem ticket. |
| **Sự kiện kích hoạt** | Agent bấm **Tạo gợi ý AI** trên màn hình chi tiết ticket (UC03), hoặc hệ thống tự động gọi sau UC04. |
| **Luồng chính** | 1. Hệ thống kiểm tra quyền truy cập ticket của Agent.<br>2. Xây dựng ngữ cảnh truy vấn (`query_text`) từ tiêu đề và **chỉ các tin nhắn `visibility = 'PUBLIC'`** (**tuyệt đối loại bỏ mọi ghi chú nội bộ `INTERNAL`**).<br>3. `KnowledgeRetriever` truy xuất các `kb_chunk` thỏa `is_active = TRUE`, `kb_version.status = 'PUBLISHED'` và đúng `access_scope`.<br>4. Nếu có đoạn đạt ngưỡng liên quan, `RAG Engine` sinh câu trả lời nháp kèm danh sách `kb_chunk_id` trích dẫn (kèm lớp bảo vệ chống prompt injection: nội dung khách và KB chỉ là dữ liệu tham khảo, không điều khiển quyền hay công cụ hệ thống).<br>5. `Citation Validator` xác minh mọi `kb_chunk_id` đều tồn tại, còn hiệu lực và thuộc tập đoạn đã truy xuất.<br>6. Lưu `ai_run(outcome = 'SUGGESTED')` và các bản ghi `ai_citation` (kèm snapshot `kb_version_id`, `relevance_score`).<br>7. Hiển thị câu trả lời nháp + danh sách nguồn trích dẫn cho Agent. Agent chọn **Chấp nhận**, **Chỉnh sửa và gửi** (chuyển sang UC03) hoặc **Từ chối** → lưu `ai_suggestion_feedback`. |
| **Luồng thay thế / Ngoại lệ (Fallback)** | - **A1 — Tự động trả lời (`AUTO_REPLY`):** Chỉ kích hoạt khi mọi nguồn đều thuộc bài `access_scope = 'PUBLIC'` và `allow_auto_reply = TRUE`, độ tin cậy đạt ngưỡng và yêu cầu thuộc phạm vi cho phép (VD: *đặt lại mật khẩu*). Các yêu cầu nhạy cảm (VD: *hoàn tiền*) bắt buộc fallback (`OUT_OF_AUTO_SCOPE`).<br>- **E1 — Không tìm thấy nguồn KB / Nguồn mâu thuẫn / Hết hiệu lực / Trích dẫn không hợp lệ:** Lưu `ai_run(outcome = 'FALLBACK_TO_AGENT')` kèm `fallback_reason` tương ứng; không sinh câu trả lời bịa đặt; thông báo Agent tự xử lý.<br>- **E2 — Khách yêu cầu gặp nhân viên:** Lưu `CUSTOMER_REQUESTED_HUMAN`, chuyển hàng đợi Agent.<br>- **E3 — Dịch vụ AI lỗi / Timeout:** Lưu `ai_run(outcome = 'FAILED', fallback_reason = 'AI_SERVICE_ERROR')`, hiển thị thông báo mềm, **không cản trở Agent xử lý ticket thủ công**. |
| **Hậu điều kiện** | Mọi lần chạy AI đều lưu vết trong `ai_run` và `ai_citation`; trạng thái tác vụ AI độc lập với trạng thái ticket. |
| **Quy tắc nghiệp vụ** | - Không sinh câu trả lời gửi khách khi không có trích dẫn `PUBLISHED` hợp lệ.<br>- Ghi chú `INTERNAL` tuyệt đối không được lọt vào prompt hoặc câu trả lời công khai. |

---

## 4. Hợp đồng API, Dữ liệu MySQL 8.0+ & Kiểm thử

### 4.1. Các API trọng tâm
- `POST /api/v1/kb/articles`, `POST /api/v1/kb/articles/{id}/versions`, `POST /api/v1/kb/versions/{id}/submit`
- `POST /api/v1/kb/versions/{id}/publish`, `POST /api/v1/kb/versions/{id}/reject`, `POST /api/v1/kb/articles/{id}/revoke`
- `GET /api/v1/kb/articles/search?q=&scope=`
- `POST /api/v1/tickets/{id}/ai-suggestions`, `GET /api/v1/tickets/{id}/ai-suggestions`, `POST /api/v1/ai-runs/{id}/feedback`

### 4.2. Các bảng MySQL sử dụng (trích từ `helpdesk_unified_schema_mysql.sql`)
- `kb_article`, `kb_version` (có cột sinh `published_article_key` + `UNIQUE`), `kb_chunk`
- `ai_run`, `ai_citation`, `ai_suggestion_feedback`

### 4.3. Tiêu chí kiểm thử & Đánh giá AI
1. Bài KB bị thu hồi (`ARCHIVED`) → không còn xuất hiện trong truy xuất và không được dùng để trả lời.
2. Câu hỏi ngoài phạm vi KB → trả `FALLBACK_TO_AGENT` (`NO_RELEVANT_KB`), không bịa câu trả lời.
3. Ticket có ghi chú `INTERNAL` → xác minh `ai_run.query_text` và câu trả lời không chứa nội dung nội bộ.
4. AI lỗi/timeout → lưu `FAILED`, Agent vẫn phản hồi ticket bình thường.
5. Đo lường định lượng (mục 9.3 đề tài gốc): Groundedness (nguồn hỗ trợ câu trả lời), tỷ lệ chấp nhận/sửa từ `ai_suggestion_feedback`, tỷ lệ Fallback chính xác, độ trễ `latency_ms` và tỷ lệ lỗi.
