# CellphoneS — bộ tài liệu KB ban đầu

Nguồn duy nhất: website CellphoneS. Thu thập hai trang công khai đã được chọn; đây là bản chụp nghiên cứu cho Helpdesk local, không phải dữ liệu đơn hàng trực tiếp hay tuyên bố đại diện CellphoneS.

## Nội dung đã lấy

| Mã bài | Nguồn | Bảng | Chunk đã nhập |
|---|---|---:|---:|
| CPS-DOI-TRA | https://cellphones.com.vn/huong-dan-huy-giao-dich-doi-tra | 1 | 7 |
| CPS-GIAO-HANG | https://cellphones.com.vn/chinh-sach-giao-hang | 2 | 11 |

Đã tạo **2 bài KB, 2 phiên bản PUBLISHED, 18 chunk hoạt động** trong database local. Chạy import lần hai giữ nguyên article/version ID, không tạo bản trùng. Kết quả của máy hiện tại nằm trong `local-import.json`.

Phạm vi hiện có: điều kiện/phương thức hủy giao dịch, bảng đổi trả và điều kiện gửi trả, tiếp nhận khiếu nại, cách thanh toán, phí vận chuyển, điều kiện/thời gian giao nhận, tình trạng bao bì, đổi mới hàng mua online và các trường hợp cần thanh toán trước. Chưa thu thập tài liệu bảo hành chi tiết theo hãng; bài hiện tại chỉ dẫn liên kết đến trang bảo hành.

## Cấu trúc

```text
sources.json             # URL, timestamp UTC, hash, selector, bảng, ảnh, phạm vi loại trừ
review.json              # Biên bản kiểm tra; khóa hash toàn payload và bản gốc
local-import.json        # Article/version ID của backend local hiện tại
raw/<document-id>/       # HTML nguyên gốc và hai ảnh thanh toán minh họa
kb/<document-id>.md      # Toàn bộ phần chính sách đã làm sạch, kể cả mục tạm loại
kb/articles.jsonl        # Hai payload API đã chuẩn bị, chỉ chứa phần cho phép nhập RAG
evaluation/README.md     # 50 câu kiểm thử và kết quả retrieval baseline, không nhập vào KB
```

`raw/` giữ nguyên HTML để đối chiếu, có thể chứa script, giao diện và bình luận công khai của website. Không dùng raw HTML trực tiếp làm KB. Markdown sạch không chứa menu, footer hoặc bình luận khách hàng. Không coi bình luận/hội thoại riêng của khách là chính sách.

## Các quyết định khi làm sạch

- Mở rộng `rowspan`/`colspan` và ghép đầy đủ các cấp tiêu đề bảng. Một bảng nguồn không có header được đặt tên kỹ thuật `Cột 1`, `Cột 2`, không tự diễn giải ý nghĩa.
- Giữ điều kiện, ngoại lệ, chú thích `(*)`, `(**)`, `(***)` và liên kết gốc. Chuẩn hóa khoảng trắng, không sửa các lỗi diễn đạt sẵn có hoặc tự bổ sung chính sách.
- Markdown là bản tham chiếu đầy đủ. Payload nhập KB gom **theo mục**, giữ bảng và chú thích trong cùng chunk; mỗi mục kèm đường dẫn tiêu đề và URL nguồn. Điều này phù hợp bộ chia đoạn theo dòng trống của backend hiện tại.
- Hai ảnh nguồn đã được xem thủ công: minh họa chọn phương thức thanh toán và trang thanh toán VietQR. Ảnh được lưu đối chiếu, không OCR/đưa QR, số tài khoản, mã đơn/giao dịch mẫu hay ưu đãi trong ảnh vào KB.
- `allowAutoReply=false`; chỉ dùng gợi ý có nguồn cho nhân viên kiểm tra.

## Hai mục tạm chưa đưa vào RAG

1. Trang đổi trả: **2.4. Điều kiện hủy giao dịch** — nội dung phần này nói về hoàn tiền và có các mốc khác nhau theo đoạn/phương thức.
2. Trang giao hàng: **4. Thông tin về huỷ đơn hàng và thời gian hoàn tiền** — cần đối chiếu với trang đổi trả trước khi khẳng định một thời hạn cụ thể.

Cả hai mục vẫn có trong raw HTML và Markdown. `sources.json` liệt kê chính xác `excluded_sections`. Không tạo đáp án khẳng định về thời hạn hoàn tiền từ hai mục này cho tới khi có xác nhận/phiên bản chính sách thống nhất. Chưa có cơ chế backend tự phát hiện mọi xung đột nội dung; các loại trừ ở đây là quyết định duyệt dữ liệu.

Nguồn giao hàng có một ghi chú hiệu lực **12/12/2022** trong phần hoàn tiền. Metadata ghi nó trong `section_effective_dates`, **không áp ngày này cho toàn trang**. `effective_date` toàn trang để null; ngày thu thập không phải ngày chính sách bắt đầu có hiệu lực. Thời điểm thu thập và SHA-256 nằm trong `sources.json`.

## Thu thập và tái tạo

Chạy từ thư mục repo, dùng môi trường Python riêng:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts/requirements-cellphones.txt
.\.venv\Scripts\python.exe -X utf8 scripts/cellphones.py collect
```

Lệnh chỉ tải hai URL trong `SOURCES` và các ảnh thuộc phần chính sách, không quét toàn website. Selector đổi hoặc nội dung quá ngắn sẽ báo lỗi; không tự chuyển sang lấy toàn trang.

Để tái tạo từ bản HTML đã lưu, không truy cập Internet:

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/cellphones.py collect --from-snapshots
```

Sau mỗi thay đổi nội dung nguồn/bộ trích xuất, phải xem lại Markdown, bảng/ảnh và các mục loại trừ. `review.json` khóa hash **toàn bộ payload** (bao gồm title, summary, accessScope và allowAutoReply), không chỉ nội dung. Không tự động đánh dấu nguồn mới là đã duyệt. Nếu payload thay đổi, import bị chặn cho tới khi kiểm tra và cập nhật biên bản duyệt.

## Nhập KB local

Backend và MySQL cần đang chạy. Lệnh sẽ hỏi email/mật khẩu (mật khẩu không hiển thị), hoặc đọc `HELPDESK_EMAIL`/`HELPDESK_PASSWORD` từ môi trường. Không ghi credential vào file.

```powershell
.\.venv\Scripts\python.exe -X utf8 scripts/cellphones.py import-local
```

Chỉ cho phép backend loopback; mặc định `http://localhost:8080/api/v1`. Request API không theo redirect để tránh chuyển credential sang nơi khác. Lệnh dùng API hiện có để tạo DRAFT → submit → publish. Tài khoản cần quyền duyệt KB; đã dùng tài khoản Admin dev local. Không ghi SQL trực tiếp và không thay đổi schema/backend.

Nếu nội dung không đổi, lệnh tái sử dụng phiên bản hiện có. Nếu nội dung đã được duyệt thay đổi, tạo phiên bản mới theo quy trình backend; bản PUBLISHED trước sẽ được archive bởi backend.

## Kiểm tra

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s scripts -p test_cellphones.py
```

10 test bao phủ bảng gộp ô, chú thích, loại bỏ phần ngoài chính sách, ảnh, thiếu selector, ngày hiệu lực theo mục, phạm vi loại trừ, khóa payload/raw hash và chặn redirect API. Kiểm tra đối chiếu bản nguồn hiện tại: **246 mảnh văn bản**, không thiếu mảnh nào trong Markdown sạch. Kiểm tra MySQL thực tế: 7 + 11 chunk, đúng hai phiên bản đã publish; import lặp không tạo thêm bản ghi.

Nguồn công khai không đồng nghĩa dataset được cấp phép phân phối lại. Giữ thông tin xuất xứ và rà soát điều kiện sử dụng nếu đưa bản chụp ra ngoài phạm vi nghiên cứu/local. Khi chạy thật cần cơ chế cập nhật và duyệt lại chính sách.
