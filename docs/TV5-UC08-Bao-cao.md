# TV5 — Báo cáo ticket, SLA và CSAT (UC08)

**Đề tài:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Người thực hiện:** TV5 — bổ sung họ tên và MSSV khi nộp.  
**Phiên bản:** 2.0 · Cập nhật 21/10/2026  
**Phạm vi:** Phân tích, thiết kế và đặc tả giao diện UC08; liên kết với bộ tám Use Case của nhóm.

## 1. Căn cứ, phạm vi và sản phẩm

Căn cứ nghiệp vụ là [Chủ đề 07](../Topics-TK%20và%20XD%20phần%20mềm-20261.md) và [kế hoạch nhóm](../Ke-hoach-tuan-1-Use-Case-Chu-de-07.md). Cách tổ chức phân tích tham khảo bài 02 V3 (Phân tích Use Case AIMS) và bài 03 — Interface Design có trên máy làm việc; các bài mẫu cá nhân này không phân phối trong repository Helpdesk. Chưa tìm thấy bài 01 trong workspace nên không khẳng định hồ sơ đã khớp toàn bộ rubric của bài đó.

Hồ sơ gồm sơ đồ tổng thể, sơ đồ Use Case chi tiết UC08, đặc tả luồng, công thức KPI, lớp phân tích, trình tự, giao tiếp, hoạt động, chuyển màn hình, giao diện module và sáu ảnh giao diện. Mỗi sơ đồ có nguồn PlantUML, ảnh PNG để chèn báo cáo và SVG để phóng lớn/in.

Tuần 1 dùng phần Actor, Use Case, phân rã và đặc tả tại mục 2–5. Các mục mô hình lớp, tương tác và giao diện là phần phát triển tiếp theo yêu cầu làm tương tự bài 02–03. Tài liệu tổng thể tám UC chỉ làm bối cảnh; TV5 phụ trách chi tiết UC08.

UC08 chỉ đọc và tổng hợp dữ liệu để Supervisor/Admin đánh giá số lượng ticket, thời gian phản hồi, tuân thủ SLA và CSAT. Tạo ticket, sửa SLA, phát cảnh báo, thu thập rating và xử lý ticket thuộc các UC khác. Không thêm chức năng xuất Excel/PDF hoặc xếp hạng nhân viên vào phạm vi này.

## 2. Actor và Use Case tổng thể

| Actor | Vai trò đối với UC08 |
|---|---|
| Supervisor | Actor chính: mở báo cáo, đổi kỳ, xem thống kê và mở ticket liên quan |
| Admin | Actor chính thay thế: sử dụng cùng mục tiêu báo cáo với quyền toàn hệ thống demo |
| Agent/Khách hàng | Không được xem báo cáo quản trị trong phạm vi đã chọn |
| Hệ thống kênh ngoài | Tham gia tiếp nhận ở UC01–02; không tham gia UC08 |

AI/RAG và bộ lập lịch SLA là xử lý nội bộ. UC08 dùng các mốc SLA và cảnh báo đã có, không gọi AI hoặc bộ lập lịch khi người dùng mở báo cáo.

![Hình 1 — Use Case tổng thể tám UC](TV5-UC08-Bao-cao/use-case-tong-the.png)

Nguồn: [PlantUML](TV5-UC08-Bao-cao/use-case-tong-the.puml) · [SVG](TV5-UC08-Bao-cao/use-case-tong-the.svg).

## 3. Phân rã và Use Case chi tiết UC08

![Hình 2 — Use Case chi tiết UC08](TV5-UC08-Bao-cao/uc08-use-case-chi-tiet.png)

Nguồn: [PlantUML](TV5-UC08-Bao-cao/uc08-use-case-chi-tiet.puml) · [SVG](TV5-UC08-Bao-cao/uc08-use-case-chi-tiet.svg).

| Mã chi tiết | Mục tiêu | Quan hệ với UC08 |
|---|---|---|
| UC08.1 | Xem số lượng ticket, đang mở và phân bố trạng thái/danh mục | include: mỗi lần tải báo cáo đều trình bày khối ticket |
| UC08.2 | Xem thời gian phản hồi/giải quyết, tỷ lệ tuân thủ và tình hình SLA | include: khối SLA luôn có, cả khi chưa có mẫu |
| UC08.3 | Xem điểm CSAT và số lượt đánh giá | include: khối CSAT luôn có, cả khi chưa có rating |
| UC08.4 | Mở ticket từ một dòng cảnh báo | extend: sau khi có kết quả và người dùng chọn một ticket |

Các mã UC08.x là phân rã của UC08, không thay đổi danh mục tám UC cấp cao. Mũi tên include đi từ UC08 tới hành vi được bao gồm; extend đi từ UC08.4 tới UC08. Đăng nhập/đủ quyền là tiền điều kiện. Mở chi tiết dùng lại UC03 và phải kiểm tra quyền trên ticket.

Sơ đồ phân rã chức năng toàn hệ thống dưới đây là hình tham khảo bổ sung, dùng để đối chiếu trách nhiệm với các thành viên. Nó không thay thế sơ đồ UML chi tiết ở Hình 2.

![Hình 3 — Phân rã chức năng tám UC](TV5-UC08-Bao-cao/phan-ra-use-case.png)

Nguồn: [PlantUML](TV5-UC08-Bao-cao/phan-ra-use-case.puml) · [SVG](TV5-UC08-Bao-cao/phan-ra-use-case.svg).

## 4. Đặc tả UC08

| Thuộc tính | Nội dung |
|---|---|
| Mã / tên | UC08 — Xem báo cáo ticket, SLA và CSAT |
| Actor chính | Supervisor hoặc Admin |
| Mục tiêu | Đọc được số liệu hỗ trợ theo kỳ và hiểu được phạm vi/mẫu tính |
| Tiền điều kiện | Actor đã xác thực và được cấp quyền xem báo cáo |
| Kích hoạt | Mở Tổng quan, đổi kỳ hoặc chọn Tải lại |
| Đầu vào | days: số nguyên 1–365; mặc định 30; giao diện cung cấp 7/30/90 ngày |
| Đầu ra | Tổng ticket, đang mở, quá hạn; phân bố trạng thái/danh mục; thời gian trung bình; SLA compliance; CSAT/số rating; tối đa 30 cảnh báo gần nhất |
| Hậu điều kiện thành công | Các khối báo cáo được hiển thị cho cùng yêu cầu; dữ liệu nghiệp vụ không thay đổi |
| Bảo đảm khi lỗi | Không lộ số liệu cho người thiếu quyền; không báo cáo kết quả một phần như đầy đủ |

### 4.1. Luồng chính

1. Actor mở UI-R01 — Tổng quan báo cáo.
2. Giao diện chọn 30 ngày mặc định; nếu đã có bộ lọc trong phiên thì giữ kỳ đã chọn.
3. Giao diện hiển thị trạng thái đang tải và gửi yêu cầu với days.
4. Hệ thống kiểm tra đăng nhập, quyền Supervisor/Admin và days thuộc 1–365.
5. Hệ thống xác định mốc chốt asOf và đầu kỳ from = asOf − days × 24 giờ.
6. Hệ thống lấy ticket tạo từ from, các mốc SLA và rating gắn với những ticket đó.
7. Hệ thống lấy tối đa 30 cảnh báo phát sinh trong kỳ, sắp xếp mới nhất trước.
8. Hệ thống tổng hợp ba khối UC08.1–UC08.3 theo công thức ở mục 5.
9. Giao diện hiển thị các khối, kỳ áp dụng, đơn vị và trạng thái thiếu mẫu ở từng chỉ số.
10. Actor có thể chuyển sang UI-R02 — Chi tiết SLA mà giữ cùng kỳ; nếu chọn một cảnh báo, UC08.4 kiểm tra quyền và mở chi tiết ticket.

### 4.2. Luồng thay thế / ngoại lệ

| Mã | Tại bước | Điều kiện | Xử lý và hậu điều kiện |
|---|---|---|---|
| A1 | 2, 10 | Đổi 7/30/90 ngày | Tải lại cùng tab; chỉ áp dụng kết quả của kỳ chọn mới nhất |
| A2 | 8–9 | Cohort không có ticket | Tổng/đang mở bằng 0, thời gian/SLA/CSAT chưa tính được hiện “—”; vẫn xử lý cảnh báo độc lập |
| A3 | 8–9 | Có ticket nhưng chưa có phản hồi, giải quyết hoặc rating | Chỉ các chỉ số thiếu mẫu hiện “—”; giữ chỉ số khác |
| A4 | 8–9 | Không có mốc SLA đủ điều kiện | SLA = null, hiện “—”; không báo 0% |
| A5 | 10 | Cảnh báo thuộc ticket tạo trước đầu kỳ | Vẫn hiện vì lọc theo ngày cảnh báo; không cộng ticket đó vào tổng cohort |
| E1 | 4 | Chưa đăng nhập/hết phiên | 401; chuyển đăng nhập; không hiển thị số liệu |
| E2 | 4 | Sai vai trò | 403; hiện từ chối; có đường về trang được phép |
| E3 | 4 | days rỗng ở input tùy chỉnh, không nguyên, <1 hoặc >365 | Chỉ rõ lỗi đầu vào; không thực hiện truy vấn báo cáo |
| E4 | 6–8 | Lỗi đọc dữ liệu hoặc mất kết nối | Hiện thông báo lỗi; Thử lại giữ days/tab; không hiển thị kết quả chưa đầy đủ |
| E5 | 10 | Ticket không còn tồn tại/không đủ quyền | Không mở nội dung; giữ báo cáo và thông báo không truy cập được |

### 4.3. Đặc tả bốn UC con

| UC | Kích hoạt / điều kiện | Luồng thành công | Ngoại lệ / kết quả |
|---|---|---|---|
| UC08.1 | UC08 đã xác thực kỳ và quyền | 1. Lấy cohort. 2. Đếm tổng/đang mở. 3. Nhóm theo trạng thái/danh mục. 4. Trả số lượng | Không có ticket: tổng 0; danh mục chưa xác định hiển thị riêng, không làm mất ticket |
| UC08.2 | Cùng cohort UC08.1 và mốc chốt | 1. Lấy SLA từng ticket. 2. Tính trung bình. 3. Xét các mốc đến hạn/hoàn tất. 4. Tính tuân thủ, đang quá hạn. 5. Lấy cảnh báo theo kỳ phát sinh | Mẫu số 0: null. Mốc chưa đến hạn/chưa hoàn tất: loại khỏi mẫu số. Cảnh báo lịch sử không đồng nhất với số ticket đang quá hạn |
| UC08.3 | Cùng cohort UC08.1 | 1. Lấy rating hợp lệ của cohort. 2. Đếm lượt. 3. Tính trung bình điểm 1–5. 4. Trả điểm và số lượt | Không có rating: null và 0 lượt; không giả thành điểm 0 |
| UC08.4 | Người dùng chọn một ticket từ dòng cảnh báo sau khi đã có báo cáo | 1. Nhận ticketId từ dòng cảnh báo. 2. Kiểm tra lại quyền. 3. Mở màn chi tiết UC03. 4. Quay lại giữ kỳ/tab | 403/404: báo không truy cập được, không hiện dữ liệu ticket; bộ lọc vẫn giữ |

## 5. Quy tắc KPI, dữ liệu và ví dụ đối chiếu

### 5.1. Phạm vi thời gian

KPI ticket/SLA/CSAT dùng cohort ticket theo **ngày tạo ticket**, không lọc rating theo ngày gửi đánh giá. Ngày chốt và múi giờ hiển thị cần ghi rõ. Prototype hiện dùng cửa sổ ngày lùi từ thời điểm gọi API; quy tắc demo là 24/7, WAITING_CUSTOMER vẫn tính SLA, mở lại dùng hạn ban đầu.

Bảng cảnh báo dùng **ngày phát sinh cảnh báo**, độc lập với cohort trên và giới hạn 30 bản ghi mới nhất. Do đó có thể có tổng ticket bằng 0 nhưng vẫn có cảnh báo trong kỳ từ ticket cũ. Không dùng số dòng cảnh báo làm số ticket đang quá hạn.

### 5.2. Công thức

| Chỉ số | Công thức / đơn vị | Thiếu mẫu |
|---|---|---|
| Tổng ticket | Số ticket trong cohort | 0 |
| Đang mở | Trạng thái khác RESOLVED/CLOSED | 0 |
| Phản hồi đầu tiên TB | Tổng(first_response_at − created_at) / số ticket có first_response_at; API trả giây | null → “—” |
| Giải quyết TB | Tổng(resolved_at − created_at) / số ticket có resolved_at | null → “—” |
| SLA compliance | 100 × số mốc đúng hạn / số mốc đủ điều kiện; mỗi ticket tối đa hai mốc phản hồi/giải quyết | Mẫu số 0 → null |
| CSAT | Tổng điểm / số rating hợp lệ; thang 1–5, làm tròn một chữ số thập phân khi hiển thị | null và 0 lượt |
| Đang quá hạn | Ticket chưa có actual và đã vượt due của ít nhất một mốc; mỗi ticket chỉ đếm một lần | 0 |

Mốc SLA đủ điều kiện khi đã có actual hoặc due ≤ asOf. Đúng hạn khi actual tồn tại và actual ≤ due. Tin PENDING/FAILED/CANCELLED, ghi chú nội bộ và thư xác nhận tiếp nhận không tính là phản hồi hữu ích đã gửi. Tự động FAQ được tính sau gửi thành công theo quy tắc của nhóm. Ticket phản hồi trễ nhưng đã phản hồi vẫn có mốc SLA vi phạm, dù không còn “đang quá hạn phản hồi”.

CSAT ở đây là **điểm trung bình**, không phải phần trăm khách hài lòng. Nếu nhóm đổi sang tỷ lệ 4–5 sao thì cần đổi nhãn, công thức và đặc tả cùng lúc.

### 5.3. Dữ liệu minh họa có thể tính lại

Bộ [sample-report.json](TV5-UC08-Bao-cao/sample-report.json) chứa sáu ticket tổng hợp với các thời điểm đầy đủ. Mốc chốt: **21/10/2026 12:00 UTC+7**; kỳ 7 ngày. Mọi ticket trong ví dụ dùng SLA phản hồi 4 giờ/giải quyết 24 giờ.

| Ticket | Trạng thái | Phản hồi sau | Giải quyết sau | Kết quả mốc phản hồi / giải quyết | Rating |
|---|---|---:|---:|---|---:|
| #101 | CLOSED | 1 giờ | 8 giờ | Đúng / đúng | 5 |
| #102 | RESOLVED | 5 giờ | 20 giờ | Trễ / đúng | 4 |
| #103 | IN_PROGRESS | 2 giờ | Chưa | Đúng / đã quá hạn | — |
| #104 | WAITING_CUSTOMER | 6 giờ | Chưa | Trễ / chưa đến hạn | — |
| #105 | NEW | Chưa | Chưa | Chưa đến hạn / chưa đến hạn | — |
| #106 | IN_PROGRESS | 1 giờ | Chưa | Đúng / chưa đến hạn | — |

Kết quả tính lại: tổng **6**, đang mở **4**, đang quá hạn **1**; phản hồi TB (1+5+2+6+1)/5 = **3 giờ**; giải quyết TB (8+20)/2 = **14 giờ**; tuân thủ **5/8 = 62,5%**; CSAT **(5+4)/2 = 4,5/5, hai lượt**. Các danh mục có 2 ticket mỗi nhóm. Trạng thái 1 mới + 2 đang xử lý + 1 chờ khách + 1 giải quyết + 1 đóng = 6.

Các hình GUI tại mục 8 được sinh từ chính dữ liệu này. [sample-metrics.json](TV5-UC08-Bao-cao/sample-metrics.json) lưu kết quả tính; đây là minh họa kiểm tra công thức, không phải số liệu vận hành.

## 6. Phân tích lớp và giao diện module — theo bài 02/03

### 6.1. Trách nhiệm và mô hình lớp

| Thành phần | Loại | Trách nhiệm |
|---|---|---|
| ReportDashboard | Boundary | Nhận kỳ, hiển thị đang tải, báo cáo/lỗi; điều hướng mở ticket |
| ReportController | Control | Kiểm tra quyền/tham số, phối hợp lấy dữ liệu, tổng hợp KPI |
| ReportRepository | Repository đề xuất | Đọc dữ liệu ticket/SLA/rating/cảnh báo; trả ReportData |
| Ticket, TicketSla | Entity | Trạng thái, thời điểm tạo, actual/due của phản hồi và giải quyết |
| Rating, SlaAlert | Entity | Điểm đánh giá, lịch sử cảnh báo |
| ReportData, ReportSummary | DTO | Dữ liệu đọc và kết quả tổng hợp; không phải bảng nghiệp vụ mới |

![Hình 4 — Lớp phân tích và hợp đồng thiết kế UC08](TV5-UC08-Bao-cao/uc08-analysis-class.png)

Nguồn: [PlantUML](TV5-UC08-Bao-cao/uc08-analysis-class.puml) · [SVG](TV5-UC08-Bao-cao/uc08-analysis-class.svg).

Ticket liên kết một TicketSla, tối đa một Rating và nhiều SlaAlert. DTO là nhóm dữ liệu tạm cho lần đọc. ReportRepository/ReportController là **lớp đề xuất**, chưa phải lớp đang tồn tại trong code: prototype hiện thực truy vấn và tính toán trực tiếp trong hàm reports() tại [main.py](../helpdesk/app/main.py).

#### 6.1.1. Class Diagram miền nghiệp vụ UC08

Sơ đồ dưới đây chỉ mô tả thực thể nghiệp vụ và quan hệ dữ liệu mà báo cáo cần đọc; không đưa `ReportDashboard`, `ReportController`, `ReportRepository` hoặc DTO vào mô hình miền. Đây là bản phân tích logic, còn tên/cột cụ thể cần đối chiếu với schema triển khai ở bước thiết kế database.

Nguồn Mermaid: [uc08-class-domain.mmd](TV5-UC08-Bao-cao/uc08-class-domain.mmd).

```mermaid
classDiagram
direction LR
class Ticket {
  +int id
  +int customerId
  +Channel channel
  +TicketStatus status
  +Priority priority
  +datetime createdAt
}
class User {
  +int id
  +string name
  +UserRole role
}
class Team {
  +int id
  +string name
}
class Category {
  +string code
  +string name
}
class SlaPolicy {
  +int id
  +int version
  +Priority priority
  +int responseSeconds
  +int resolutionSeconds
}
class TicketSla {
  +int ticketId
  +int policyId
  +datetime responseDue
  +datetime resolutionDue
  +datetime firstResponseAt
  +datetime resolvedAt
}
class Rating {
  +int ticketId
  +int userId
  +int score
  +datetime createdAt
}
class SlaAlert {
  +int id
  +int ticketId
  +DeadlineType deadlineType
  +AlertLevel level
  +datetime createdAt
}
Ticket "0..*" --> "1" User : customer
Ticket "0..*" --> "0..1" User : assignee
Ticket "0..*" --> "0..1" Team : routed team
Ticket "0..*" --> "0..1" Category : category
Ticket "1" *-- "1" TicketSla : SLA snapshot
SlaPolicy "1" <-- "0..*" TicketSla : applied policy
Ticket "1" *-- "0..1" Rating : evaluation
Ticket "1" *-- "0..*" SlaAlert : alert history
```

File `.mmd` là nguồn đầy đủ, gồm cả các enum trạng thái, mức ưu tiên, vai trò và loại cảnh báo. Quan hệ một ticket–một `TicketSla` và tối đa một `Rating` phản ánh schema hiện tại. Phần UC08 dùng `Ticket.createdAt/status/category`, `TicketSla` cho KPI thời gian/SLA, `Rating.score` cho CSAT, và `SlaAlert.createdAt` cho danh sách cảnh báo. Nhãn “khách hàng” và “người được phân công” là hai vai trò liên kết tới `User`, không phải hai bảng user riêng.

#### 6.1.2. Truy vết KPI sang lớp miền

| Chỉ số UC08 | Lớp/thuộc tính nguồn | Quy tắc lấy mẫu |
|---|---|---|
| Tổng, đang mở, theo trạng thái | `Ticket.createdAt`, `Ticket.status` | Cohort theo ngày tạo; đang mở loại `RESOLVED`, `CLOSED` |
| Phân bố danh mục | `Ticket.category` → `Category.code` | Giữ nhóm chưa phân loại để tổng nhóm bằng tổng ticket |
| Thời gian phản hồi/giải quyết TB | `Ticket.createdAt`, `TicketSla.firstResponseAt`, `TicketSla.resolvedAt` | Chỉ lấy ticket có actual tương ứng; không tính tin nhắn chưa gửi thành công |
| SLA compliance / đang quá hạn | `TicketSla.responseDue`, `resolutionDue`, actual tương ứng | Mốc đủ điều kiện theo asOf; ticket quá hạn đếm tối đa một lần |
| CSAT và số lượt | `Rating.score`, quan hệ `Rating.ticketId` | Điểm 1–5; rating thuộc ticket trong cohort ngày tạo |
| Lịch sử cảnh báo | `SlaAlert.createdAt`, `deadlineType`, `level`, `ticketId` | Lọc theo ngày phát sinh, độc lập cohort ticket; tối đa 30 mới nhất |

Mô hình lớp miền UC08 làm căn cứ cho ERD/lược đồ bảng; giữ nguyên các quan hệ, bội số và quy tắc KPI đã truy vết.

### 6.2. ERD và lược đồ dữ liệu phục vụ UC08

ERD dưới đây ánh xạ Class Diagram sang các bảng prototype hiện có. Mermaid là nguồn sơ đồ để xem/chỉnh sửa khi không xuất được PNG; sơ đồ chỉ gồm các bảng cần thiết cho báo cáo và các bảng tham chiếu danh mục/người dùng.

Nguồn Mermaid: [uc08-erd.mmd](TV5-UC08-Bao-cao/uc08-erd.mmd).

```mermaid
erDiagram
  USERS {
    INTEGER id PK
    TEXT name
    TEXT role
  }
  TEAMS {
    INTEGER id PK
    TEXT name UK
  }
  CATEGORIES {
    TEXT code PK
    TEXT name
    INTEGER team_id FK
  }
  SLA_POLICIES {
    INTEGER id PK
    INTEGER version
    TEXT priority
    INTEGER response_seconds
    INTEGER resolution_seconds
  }
  TICKETS {
    INTEGER id PK
    INTEGER customer_id FK
    TEXT category FK
    INTEGER team_id FK
    INTEGER assignee_id FK
    TEXT status
    TEXT priority
    REAL created_at
    REAL updated_at
  }
  TICKET_SLA {
    INTEGER ticket_id PK, FK
    INTEGER policy_id FK
    REAL response_due
    REAL resolution_due
    REAL first_response_at
    REAL resolved_at
  }
  RATINGS {
    INTEGER ticket_id PK, FK
    INTEGER customer_id FK
    INTEGER score
    TEXT comment
    REAL created_at
  }
  SLA_ALERTS {
    INTEGER id PK
    INTEGER ticket_id FK
    TEXT deadline_type
    TEXT level
    REAL created_at
  }
  USERS ||--o{ TICKETS : customer
  USERS o|--o{ TICKETS : assignee
  TEAMS o|--o{ USERS : membership
  TEAMS o|--o{ TICKETS : assigned_team
  TEAMS ||--o{ CATEGORIES : routes
  CATEGORIES o|--o{ TICKETS : classifies
  TICKETS ||--|| TICKET_SLA : has_sla
  SLA_POLICIES ||--o{ TICKET_SLA : applied_to
  TICKETS ||--o| RATINGS : receives
  USERS ||--o{ RATINGS : submits
  TICKETS ||--o{ SLA_ALERTS : produces
```

#### Từ điển dữ liệu UC08

Các bảng dưới đây phản ánh schema hiện tại trong [db.py](../helpdesk/app/db.py); thời gian lưu dạng Unix epoch giây (`REAL`). `NOT NULL`/`CHECK` được ghi theo định nghĩa đang triển khai. Tên cột `created_at` trong `ratings` là thời điểm đánh giá, còn cohort CSAT vẫn xác định qua `tickets.created_at`.

| Bảng | Cột liên quan | Khóa/ràng buộc hiện có | Vai trò với UC08 |
|---|---|---|---|
| `tickets` | `id`, `customer_id`, `category`, `team_id`, `assignee_id`, `status`, `priority`, `created_at`, `updated_at` | PK `id`; FK đến `users`, `categories`, `teams`; `category`, `team_id`, `assignee_id` nullable; `status` và `priority` có CHECK | Cohort ticket; đếm tổng/đang mở; nhóm trạng thái/danh mục; liên kết người/nhóm khi hiển thị cảnh báo |
| `ticket_sla` | `ticket_id`, `policy_id`, `response_due`, `resolution_due`, `first_response_at`, `resolved_at` | PK/FK `ticket_id` → `tickets`; FK `policy_id`; due bắt buộc, actual nullable | Mẫu số, tuân thủ SLA, đang quá hạn, thời gian phản hồi/giải quyết |
| `sla_policies` | `id`, `version`, `priority`, `response_seconds`, `resolution_seconds` | PK `id`; UNIQUE(`version`,`priority`) | Giải nghĩa chính sách đã áp dụng; thời hạn cụ thể của ticket vẫn lấy từ snapshot due ở `ticket_sla` |
| `ratings` | `ticket_id`, `customer_id`, `score`, `comment`, `created_at` | PK/FK `ticket_id`; FK `customer_id`; CHECK score 1–5 | Điểm CSAT và số lượt; tối đa một đánh giá/ticket trong schema hiện tại |
| `sla_alerts` | `id`, `ticket_id`, `deadline_type`, `level`, `created_at` | PK `id`; FK `ticket_id`; UNIQUE(`ticket_id`,`deadline_type`,`level`) | Lịch sử cảnh báo, lọc theo ngày phát sinh và giới hạn 30 dòng gần nhất |
| `categories` | `code`, `name`, `team_id` | PK `code`; FK `team_id` bắt buộc | Nhãn danh mục và ánh xạ nhóm; ticket chưa phân loại có `tickets.category = NULL` |
| `teams` | `id`, `name` | PK `id`; UNIQUE `name` | Tên nhóm của ticket trong bảng cảnh báo |
| `users` | `id`, `name`, `role`, `team_id` | PK `id`; role CHECK; `team_id` nullable | Danh tính customer/assignee và ranh giới quyền; báo cáo chỉ dành Supervisor/Admin |

#### Chỉ mục và nhất quán truy vấn

Schema hiện có chỉ mục `ticket_queue(team_id,status,updated_at)` và `ticket_customer(customer_id,updated_at)`; `ticket_sla.ticket_id` và `ratings.ticket_id` có chỉ mục do là khóa chính. Các truy vấn UC08 lọc ticket theo `created_at` và cảnh báo theo `created_at`, nên đề xuất cân nhắc hai chỉ mục sau khi đo trên dữ liệu đại diện:

```sql
CREATE INDEX idx_tickets_created_at_id ON tickets(created_at, id);
CREATE INDEX idx_sla_alerts_created_at_id ON sla_alerts(created_at DESC, id DESC);
```

Thứ tự cảnh báo nên xác định tường minh `created_at DESC, id DESC` để ổn định khi nhiều dòng cùng thời điểm. Nếu triển khai các chỉ mục này, cần thêm migration có phiên bản; chưa thay đổi schema ứng dụng trong bước thiết kế này.

Mỗi lần tạo báo cáo nên xác định một `asOf` duy nhất, rồi đọc Ticket/SLA, Rating và Alert trong cùng read transaction/snapshot. `asOf` và kết quả tổng hợp không cần bảng lưu riêng cho báo cáo tức thời; các KPI được tính từ dữ liệu nghiệp vụ. Snapshot nhất quán là yêu cầu xử lý truy vấn, không phải bảo đảm mà schema hiện tại tự cung cấp.

#### Rà soát khả năng tính KPI

| KPI | Điều kiện truy vấn | Lưu ý thiết kế |
|---|---|---|
| Tổng/đang mở/trạng thái | `tickets.created_at` trong kỳ; nhóm theo `status` | Cohort lấy theo thời điểm tạo ticket |
| Danh mục | Cùng cohort; nhóm theo `category` | Cộng thêm nhóm “Chưa phân loại” cho `NULL` để tổng phân bố khớp tổng ticket |
| Phản hồi/giải quyết trung bình | JOIN `ticket_sla`; lấy actual khác NULL trừ `tickets.created_at` | `first_response_at` chỉ được ghi khi phản hồi gửi thành công; đơn vị nội bộ là giây |
| Tuân thủ SLA | Mỗi mốc phản hồi/giải quyết đủ điều kiện so actual với due | Due/actual ở `ticket_sla`; chính sách được giữ qua `policy_id` |
| Ticket đang quá hạn | Actual còn NULL và due < `asOf` ở ít nhất một mốc | Đếm ticket một lần dù cả hai mốc cùng quá hạn |
| CSAT | JOIN `ratings` theo ticket trong cohort; `AVG(score)`, `COUNT(*)` | Không lọc cohort theo `ratings.created_at`; không có mẫu trả null và 0 lượt |
| Cảnh báo | `sla_alerts.created_at` trong kỳ, sắp xếp mới nhất | Lọc độc lập cohort ticket, tối đa 30; không dùng số alert làm số ticket quá hạn |

**Chênh lệch cần xử lý khi hiện thực/ nghiệm thu:** code báo cáo hiện không cộng bucket `category = NULL` vào map danh mục; truy vấn cảnh báo sắp theo `id DESC`; và các truy vấn riêng chưa được bọc trong một snapshot đọc thống nhất. Đây là điểm cần đưa vào backlog nếu nhóm muốn UI đáp ứng đầy đủ đặc tả và tính nhất quán đã chốt, không phải thay đổi đã triển khai.

### 6.3. Giao diện module nội bộ

![Hình 5 — Giao diện module báo cáo](TV5-UC08-Bao-cao/uc08-report-components.png)

Nguồn: [PlantUML](TV5-UC08-Bao-cao/uc08-report-components.puml) · [SVG](TV5-UC08-Bao-cao/uc08-report-components.svg).

| Hợp đồng đề xuất | Điều kiện vào | Kết quả / lỗi |
|---|---|---|
| IReporting.getReport(requester, days) | Requester xác thực, được cấp quyền; days nguyên 1–365 | ReportSummary; lỗi xác thực/quyền/tham số hoặc REPORT_UNAVAILABLE |
| IReportData.loadReportData(from, asOf) | Đã kiểm tra quyền ở tầng ứng dụng; from ≤ asOf | ReportData nhất quán trong lần đọc; lỗi DataAccessError |

Thiết kế đề xuất đọc trên một snapshot dữ liệu để tránh các khối lệch nhau do ticket đổi giữa các truy vấn. Prototype hiện có nhiều truy vấn trực tiếp, chưa được xem là đã hiện thực yêu cầu snapshot này. Tên lỗi REPORT_UNAVAILABLE/DataAccessError là hợp đồng thiết kế, không phải mã API đã triển khai.

UC08 không có subsystem bên ngoài tương tự Interbank của AIMS. Phần áp dụng bài 03 là xác định interface và phụ thuộc module báo cáo nội bộ; không tạo dịch vụ ngoài khi nghiệp vụ không cần.

## 7. Biểu đồ tương tác và hoạt động

### 7.1. Biểu đồ trình tự

```mermaid
sequenceDiagram
  autonumber
  actor User as Supervisor/Admin
  participant UI as ReportDashboard
  participant RC as ReportController
  participant Repo as ReportRepository
  participant Ticket as TicketService UC03
  User->>UI: openReport(days), mặc định 30
  UI->>UI: showLoading()
  UI->>RC: getReport(requester, days)
  activate RC
  RC->>RC: authorizeAndValidate(requester, days)
  alt Chưa đăng nhập, sai quyền hoặc days không hợp lệ
    RC-->>UI: 401 / 403 / 422
    UI-->>User: Đăng nhập, từ chối truy cập hoặc sửa kỳ
  else Yêu cầu hợp lệ
    Note over RC,Repo: from = asOf - days × 24 giờ; ticket/SLA/rating theo tickets.created_at; alert theo sla_alerts.created_at
    RC->>Repo: loadReportData(from, asOf)
    activate Repo
    alt Lỗi đọc dữ liệu
      Repo-->>RC: DataAccessError
      RC-->>UI: REPORT_UNAVAILABLE
      UI-->>User: Báo lỗi và cho thử lại cùng kỳ
    else Đọc thành công
      Repo-->>RC: ReportData
      deactivate Repo
      RC->>RC: aggregateMetrics(data, asOf)
      RC-->>UI: ReportSummary
      UI->>UI: showSummary(report)
      UI-->>User: Hiện ticket, SLA, CSAT; KPI null hiện “—”
      opt Người dùng chọn ticket từ cảnh báo (UC08.4)
        User->>UI: select(ticketId)
        UI->>Ticket: openTicket(requester, ticketId)
        activate Ticket
        Ticket->>Ticket: authorizeTicketAccess(requester, ticketId)
        alt Được phép
          Ticket-->>UI: TicketDetail
          UI-->>User: Mở chi tiết ticket (UC03)
        else Ticket không tồn tại hoặc không đủ quyền
          Ticket-->>UI: 403 / 404
          UI-->>User: Giữ báo cáo/kỳ và báo không truy cập được
        end
        deactivate Ticket
      end
    end
  end
  deactivate RC
```

Nguồn Mermaid: [uc08-sequence.mmd](TV5-UC08-Bao-cao/uc08-sequence.mmd). Nguồn PlantUML tương đương: [uc08-sequence.puml](TV5-UC08-Bao-cao/uc08-sequence.puml). PNG cũ không được dùng làm hình chính vì chưa có nhánh UC08.4 mới.

Biểu đồ bao gồm luồng thành công, nhánh sai quyền/tham số, lỗi đọc dữ liệu và UC08.4 tùy chọn. UC08.4 gọi lại dịch vụ ticket thuộc UC03 để kiểm tra quyền tại thời điểm mở; không suy ra quyền từ việc ticket xuất hiện trong báo cáo. Thiếu mẫu là trạng thái từng khối, không phải điều kiện xóa toàn bộ báo cáo.

#### Hợp đồng tương tác của biểu đồ trình tự

| Bước | Người gửi → người nhận | Thông điệp / dữ liệu | Kết quả hoặc nhánh |
|---|---|---|---|
| 1 | Supervisor/Admin → ReportDashboard | `openReport(days)`; mặc định `days=30` | Hiện trạng thái đang tải |
| 1.1 | ReportDashboard → ReportController | `getReport(requester, days)` | Kiểm tra phiên, vai trò và miền 1–365 |
| 1.1.1 | ReportController → ReportController | `authorizeAndValidate(requester, days)` | 401/403/422 thì trả lỗi, không truy vấn số liệu |
| 1.1.2 | ReportController → ReportRepository | `loadReportData(from, asOf)` | Ticket/SLA/rating theo cohort ngày tạo; cảnh báo theo ngày phát sinh, tối đa 30 |
| 1.1.3 | ReportController → ReportController | `aggregateMetrics(data, asOf)` | Tạo ba khối UC08.1–UC08.3; giá trị thiếu mẫu là null |
| 1.2 | ReportController → ReportDashboard → Actor | `ReportSummary` / `showSummary(report)` | Hiện số 0 cho bộ đếm rỗng, “—” cho KPI không có mẫu |
| 2 | Actor → ReportDashboard → TicketService (UC03) | `openTicket(requester, ticketId)` | Chỉ chạy khi chọn ticket (điểm mở rộng UC08.4); kiểm tra quyền lại |
| 2.1 | TicketService → ReportDashboard → Actor | `TicketDetail` hoặc 403/404 | Được phép thì mở chi tiết; nếu lỗi giữ báo cáo và kỳ đã chọn |

Các lời gọi `TicketService` là tương tác với chức năng xem chi tiết ticket của UC03, không phải một phần truy vấn KPI. Tên lớp dịch vụ mô tả điểm tích hợp mức thiết kế; cần ánh xạ sang controller/route thực tế của nhóm khi chốt kiến trúc.

### 7.2. Biểu đồ giao tiếp

![Hình 7 — Giao tiếp UC08, luồng thành công](TV5-UC08-Bao-cao/uc08-communication.png)

Nguồn: [PlantUML](TV5-UC08-Bao-cao/uc08-communication.puml) · [SVG](TV5-UC08-Bao-cao/uc08-communication.svg).

| Số | Thông điệp | Lớp nhận khai báo thao tác |
|---|---|---|
| 1 | openReport(days) | ReportDashboard |
| 1.1 | getReport(requester, days) | ReportController |
| 1.1.1 | authorizeAndValidate(requester, days) | ReportController, tự gọi |
| 1.1.2 | loadReportData(from, asOf) | ReportRepository |
| 1.1.3 | aggregateMetrics(data, asOf) | ReportController, tự gọi |
| 1.2 | showSummary(report) | ReportDashboard, tự gọi |

Luồng lỗi dùng showError(error); showLoading() thể hiện phản hồi lúc chờ. Các tự gọi được ghi bằng note ở sơ đồ giao tiếp để giữ chữ dễ đọc; không phải lời gọi tới một đối tượng khác.

### 7.3. Biểu đồ hoạt động

```mermaid
flowchart TD
  A([Bắt đầu]) --> B[Mở báo cáo hoặc đổi kỳ]
  B --> C{Đã đăng nhập?}
  C -- Không --> C1[Chuyển tới đăng nhập] --> Z([Kết thúc])
  C -- Có --> D{Có quyền Supervisor/Admin?}
  D -- Không --> D1[Hiện từ chối truy cập] --> Z
  D -- Có --> E{days là số nguyên 1..365?}
  E -- Không --> E1[Báo kỳ không hợp lệ, giữ bộ lọc] --> Z
  E -- Có --> F[Hiện trạng thái đang tải]
  F --> G[Lấy ticket, SLA, rating theo ngày tạo ticket]
  G --> H[Lấy tối đa 30 cảnh báo theo ngày phát sinh]
  H --> I{Đọc dữ liệu thành công?}
  I -- Không --> I1[Báo lỗi, không hiện kết quả một phần]
  I1 --> I2[Cho thử lại cùng kỳ] --> Z
  I -- Có --> J[Tổng hợp ticket, thời gian, SLA và CSAT]
  J --> K[Hiện từng khối; bộ đếm rỗng là 0, KPI thiếu mẫu là —]
  K --> L{Chọn ticket từ dòng cảnh báo?}
  L -- Không --> Z
  L -- Có --> M[Kiểm tra lại quyền trên ticket]
  M --> N{Được phép?}
  N -- Có --> O[Mở chi tiết ticket theo UC03] --> Z
  N -- Không --> P[Báo không truy cập được; giữ báo cáo và kỳ] --> Z
```

Nguồn Mermaid: [uc08-activity.mmd](TV5-UC08-Bao-cao/uc08-activity.mmd) · nguồn PlantUML: [uc08-activity.puml](TV5-UC08-Bao-cao/uc08-activity.puml). PNG hiện có chưa được dựng lại sau khi làm rõ UC08.4 chỉ mở ticket từ dòng cảnh báo.

Nguồn: [PlantUML](TV5-UC08-Bao-cao/uc08-activity.puml) · [SVG](TV5-UC08-Bao-cao/uc08-activity.svg).

## 8. Thiết kế giao diện chi tiết — theo bài 03

### 8.1. Quy chuẩn GUI

| Mã | Quy chuẩn |
|---|---|
| G01 | Mockup desktop 1366 × 768; bố cục dùng lề 40 px, khoảng cách 16–18 px; màn hẹp xếp KPI thành cột, bảng có vùng cuộn ngang riêng |
| G02 | Segoe UI; tiêu đề 32 px, chữ nội dung 22–24 px, chú thích 18 px; màu chính xanh #2463D3, chữ #172B44, nền #F3F6FB |
| G03 | Số liệu luôn có nhãn và đơn vị; màu trạng thái có chữ đi kèm; null hiển thị “—”, tổng thực bằng 0 hiển thị 0 |
| G04 | Kỳ mặc định 30 ngày; các mockup dùng kỳ 7 ngày để đối chiếu bộ dữ liệu minh họa; đổi tab giữ kỳ |
| G05 | Đổi kỳ/Tải lại hiển thị đang tải; bỏ kết quả đến muộn của kỳ cũ; tránh trộn các khối của hai yêu cầu |
| G06 | Số tỷ lệ và điểm làm tròn một chữ số thập phân; thời gian API là giây, GUI đổi sang phút/giờ phù hợp; không làm tròn trước khi tổng hợp |
| G07 | Ngày giờ hiển thị dd/MM/yyyy HH:mm, UTC+7; không dùng màu làm dấu hiệu duy nhất |
| G08 | Tab/Shift+Tab đi tới chọn kỳ → tab báo cáo → tải lại/thử lại → dòng ticket; Enter kích hoạt; thông báo lỗi có nội dung rõ và hỗ trợ screen reader |
| G09 | Sai quyền không hiện KPI; hết phiên về đăng nhập; lỗi mạng giữ kỳ/tab và nút Thử lại |
| G10 | Đây là thiết kế giao diện đề xuất. Việc một chi tiết có trong mockup không khẳng định prototype đã hiện thực chi tiết đó |

### 8.2. Danh mục và luồng màn hình

| Mã | Màn hình | Điều kiện mở / lối ra |
|---|---|---|
| UI-R01 | Tổng quan ticket, SLA, CSAT | Sau kiểm tra quyền; đổi kỳ, chuyển UI-R02 |
| UI-R02 | Chi tiết SLA và cảnh báo | Từ UI-R01, giữ kỳ; chọn ticket sang màn UC03 |
| UI-TICKET | Chi tiết ticket (tham chiếu UC03) | Kiểm tra quyền theo ticket; quay lại UI-R02 giữ kỳ và vị trí cuộn |

![Hình 9 — Luồng chuyển màn hình](TV5-UC08-Bao-cao/uc08-screen-flow.png)

Nguồn: [PlantUML](TV5-UC08-Bao-cao/uc08-screen-flow.puml) · [SVG](TV5-UC08-Bao-cao/uc08-screen-flow.svg).

### 8.3. Mockup UI-R01 — Tổng quan

![Hình 10 — Tổng quan có dữ liệu, 1366 × 768](TV5-UC08-Bao-cao/ui-r01-tong-quan.png)

### 8.4. Mockup UI-R02 — Chi tiết SLA

![Hình 11 — Lịch sử cảnh báo SLA](TV5-UC08-Bao-cao/ui-r02-sla.png)

“Đang quá hạn: 1” là số ticket chưa hoàn tất mốc đã vượt hạn. Ba dòng bên dưới là lịch sử cảnh báo của hai ticket, gồm cả cảnh báo đã được xử lý; hai đại lượng không cần bằng nhau.

### 8.5. Đặc tả control và thao tác

| Mã | Control | Sự kiện | Xử lý / kết quả |
|---|---|---|---|
| SEL-R01 | Chọn kỳ 7/30/90 ngày | Change | Cập nhật days, tải lại tab hiện tại; chỉ áp dụng kết quả mới nhất |
| TAB-R01 | Tổng quan | Click/Enter | Mở UI-R01 với days hiện có |
| TAB-R02 | Chi tiết SLA | Click/Enter | Mở UI-R02 với days hiện có |
| ACT-R01 | Tải lại / mở lại trang | Refresh | Gọi lại báo cáo cùng kỳ, cập nhật thời điểm lấy số liệu sau thành công |
| BTN-R01 | Thử lại | Click/Enter sau lỗi | Giữ kỳ/tab; hiển thị đang tải, không gửi thay đổi nghiệp vụ |
| LINK-R01 | Mã ticket ở bảng cảnh báo | Click/Enter | UC08.4: kiểm tra quyền, mở chi tiết; lỗi giữ màn báo cáo |
| BTN-R02 | Về trang được phép | Click/Enter sau 403 | Quay về màn phù hợp vai trò, không hiển thị báo cáo |

### 8.6. Đặc tả trường dữ liệu

| Mã | Trường | Kiểu / miền | Thuộc tính | Định dạng / quy tắc |
|---|---|---|---|---|
| F01 | days | Integer 1–365 | Chọn; mặc định 30 | GUI chuẩn có 7/30/90; input tùy chỉnh nếu bổ sung phải kiểm tra miền trước gửi |
| F02 | total / open / overdue | Integer ≥0 | Chỉ đọc | Không có ticket là 0; open không gồm RESOLVED/CLOSED |
| F03 | avg_response_seconds | Decimal ≥0 hoặc null | Chỉ đọc | Phút/giờ, có nhãn phản hồi; null → “—” |
| F04 | avg_resolution_seconds | Decimal ≥0 hoặc null | Chỉ đọc | Phút/giờ; null → “—” |
| F05 | sla_compliance | Decimal 0–100 hoặc null | Chỉ đọc | Một chữ số thập phân + %; nhãn định nghĩa mẫu số |
| F06 | csat / rating_count | Decimal 1–5 hoặc null / Integer ≥0 | Chỉ đọc | x/5 và số lượt; chưa có mẫu là “—”/0 |
| F07 | statuses | Map trạng thái → Integer ≥0 | Chỉ đọc | Đủ năm trạng thái, giá trị vắng coi 0; tổng bằng total |
| F08 | categories | Map danh mục → Integer ≥0 | Chỉ đọc | Kèm Chưa phân loại nếu cần để tổng bằng total |
| F09 | alerts | List, tối đa 30 | Chỉ đọc | ticket_id, deadline_type, level, created_at; không hiển thị nội dung hội thoại |
| F10 | ticket_id | Integer >0 | Liên kết | Kiểm tra quyền khi mở; không coi có ID trong báo cáo là quyền đọc mọi ticket |
| F11 | created_at của cảnh báo | DateTime | Chỉ đọc | dd/MM/yyyy HH:mm, UTC+7; sắp xếp giảm dần |
| F12 | asOf / kỳ hiển thị | DateTime / số ngày | Chỉ đọc, thiết kế đề xuất | Phân biệt thời điểm đọc báo cáo với ngày phát sinh từng cảnh báo |

asOf, số mẫu thời gian và số mốc đúng hạn trong mockup là thông tin thiết kế bổ sung. API hiện tại trả phần trăm/trung bình nhưng chưa trả riêng tất cả các mẫu số này; cần bổ sung hợp đồng khi hiện thực thiết kế GUI.

### 8.7. Các trạng thái giao diện

![Hình 12 — Trạng thái rỗng](TV5-UC08-Bao-cao/ui-r01-rong.png)

![Hình 13 — Lỗi tải và thử lại](TV5-UC08-Bao-cao/ui-r01-loi.png)

![Hình 14 — Đang tải](TV5-UC08-Bao-cao/ui-r01-dang-tai.png)

![Hình 15 — Từ chối truy cập](TV5-UC08-Bao-cao/ui-r01-tu-choi.png)

Trạng thái chưa có rating hoặc mốc SLA áp dụng độc lập theo từng thẻ. Tải rỗng thành công không dùng thông báo lỗi. Khi đang tải hoặc lỗi, không hiển thị số liệu cũ dưới nhãn kỳ mới.

## 9. Đối chiếu với prototype và hợp đồng API

API hiện có: GET /api/reports?days=30. Quyền Supervisor/Admin; days 1–365. Dữ liệu trả về gồm total, open, overdue, avg_response_seconds, avg_resolution_seconds, sla_compliance, csat, rating_count, statuses, categories, alerts.

| Thành phần | Hiện trạng đọc từ code | Thiết kế hồ sơ |
|---|---|---|
| Tính KPI/cohort và giới hạn 30 cảnh báo | Có trong hàm reports() | Giữ công thức/phạm vi, giải thích rõ hai loại cửa sổ thời gian |
| Controller/Repository riêng | Chưa có lớp riêng | Đề xuất tách trách nhiệm như Hình 4–7 |
| Snapshot nhất quán các truy vấn | Chưa được bảo đảm bởi thiết kế truy vấn hiện tại | Đề xuất đọc cùng snapshot và cùng asOf |
| asOf, số mẫu/mẫu số trên GUI | Chưa có đủ trong response | Thêm metadata khi thực hiện thiết kế GUI |
| Danh mục chưa phân loại | API đếm các danh mục có cấu hình, có thể bỏ ticket null | Thiết kế bổ sung nhóm Chưa phân loại để tổng số khớp |
| Lỗi, request đến muộn và trạng thái GUI chi tiết | Không coi là đã nghiệm thu trong đợt tài liệu này | Mô tả đầy đủ trạng thái và yêu cầu chấp nhận |

Các lỗi 401/403/422 gắn với xác thực/quyền/validation của prototype. Mã REPORT_UNAVAILABLE và DataAccessError trong sơ đồ là tên lỗi ở mức thiết kế. Tên thuộc tính camelCase trên mô hình được ánh xạ sang snake_case khi trao đổi JSON.

## 10. Kịch bản nghiệm thu đề xuất

| Mã | Tình huống | Kết quả mong đợi |
|---|---|---|
| AC01 | Supervisor/Admin mở báo cáo | Đọc được ba khối; kỳ mặc định 30, API cho 1–365 |
| AC02 | Agent/Customer/chưa đăng nhập gọi báo cáo | Từ chối theo quyền/phiên; không trả số liệu |
| AC03 | Chọn kỳ khác khi yêu cầu trước chưa xong | Hiển thị đúng kỳ mới nhất, không trộn dữ liệu |
| AC04 | Không có ticket hoặc rating | 0 cho đếm; null/“—” cho KPI thiếu mẫu; số rating chính xác |
| AC05 | Dùng dữ liệu sáu ticket tại mục 5.3 | 6 tổng, 4 mở, 1 quá hạn, 3h/14h, 62,5%, 4,5/5 với 2 rating |
| AC06 | Ticket cũ có cảnh báo mới trong kỳ | Có dòng cảnh báo nhưng không cộng vào tổng cohort |
| AC07 | Tin nhắn gửi lỗi / ghi chú nội bộ | Không được tính là phản hồi đầu tiên đã gửi |
| AC08 | Lỗi đọc/mất kết nối | Giữ kỳ/tab, thông báo lỗi và Thử lại; không báo thành công một phần |
| AC09 | Mở ticket bị thu hồi quyền hoặc đã xóa | Không hiện nội dung; có thông báo; báo cáo vẫn giữ bộ lọc |

Đây là kịch bản nghiệm thu để nhóm áp dụng cho phần mềm sau khi hiện thực thiết kế. Đợt hoàn thiện hồ sơ này kiểm tra sơ đồ, hình ảnh và số liệu minh họa; không tuyên bố đã chạy các kịch bản phần mềm mới trên prototype.

## 11. Kết quả review và việc nhóm cần xác nhận

| Vấn đề review trước | Kết quả hoàn thiện |
|---|---|
| Thiếu Use Case chi tiết UC08 | Đã có Hình 2 với Actor, ranh giới, include/extend và đặc tả UC08.x |
| Thông điệp không khớp thao tác lớp | Đã thống nhất openReport, getReport, authorizeAndValidate, loadReportData, aggregateMetrics, showSummary |
| Gọi Repository là lớp đã có trong code | Đã ghi rõ là thiết kế đề xuất, đối chiếu prototype tại mục 9 |
| GUI chỉ là wireframe | Đã có quy chuẩn, chuyển màn hình, control, trường dữ liệu và sáu mockup PNG |
| Chưa xuất ảnh/kiểm tra render | Các sơ đồ PlantUML ban đầu có PNG/SVG; Sequence và Activity đã được cập nhật bằng nguồn Mermaid nhúng vì môi trường hiện tại không có PlantUML để dựng ảnh mới |
| Số wireframe không kiểm tra được | Đã dùng một bộ sáu ticket nhất quán, có dữ liệu gốc và script tính/sinh hình |

Các thông tin còn cần nhóm cung cấp/xác nhận: họ tên/MSSV TV5; người review/duyệt thật; công thức cohort, CSAT trung bình và quyền Admin; mẫu bài 01 khi có; định dạng nộp cuối cùng và yêu cầu dùng Astah nếu giảng viên quy định. Không tự ghi tên reviewer hoặc đánh dấu nhóm đã nghiệm thu.

### 11.1. Rà soát truy vết và nhất quán cuối vòng

| Quyết định nghiệp vụ | Use Case / đặc tả | Activity / Sequence | Class / ERD | Kết quả rà soát |
|---|---|---|---|---|
| Actor chỉ Supervisor/Admin; phải đăng nhập và có quyền | Mục 2, 4; E1–E2 | Kiểm tra trước truy vấn | `users.role`; không lưu quyền báo cáo riêng | Nhất quán; quyền trên ticket được kiểm tra lại riêng |
| Kỳ mặc định 30 ngày, miền API 1–365 | Mục 4; A1/E3 | Validate `days` trước khi đọc dữ liệu | Không cần bảng báo cáo hay bảng kỳ | Nhất quán |
| Ticket/SLA/CSAT theo cohort ngày tạo | Mục 5.1–5.2 | Lấy dữ liệu theo `tickets.created_at` | `tickets`, `ticket_sla`, `ratings` | Nhất quán; rating.created_at không xác định cohort |
| Cảnh báo lọc theo ngày phát sinh, độc lập cohort, tối đa 30 | Mục 4.2 A5, 5.1 | Đọc `sla_alerts` riêng | `sla_alerts.created_at` và quan hệ ticket | Nhất quán; không đồng nghĩa số ticket đang quá hạn |
| UC08.4 tùy chọn từ một dòng cảnh báo | Mục 3, 4.3 | Chỉ nhánh chọn ticket từ cảnh báo | Mở ticket theo UC03; kiểm tra quyền | Đã đồng bộ Activity và Sequence Mermaid |
| Không có mẫu thì KPI null/“—”; bộ đếm là 0 | Mục 4.2–5.2 | Hiện từng khối, không xóa cả báo cáo | Không cần lưu giá trị tổng hợp | Nhất quán |
| Không thay đổi dữ liệu nghiệp vụ khi xem báo cáo | Mục 4 | Repository chỉ đọc; UC08.4 chuyển sang luồng UC03 | Tận dụng bảng nghiệp vụ hiện có | Nhất quán |

Điểm cần nhóm chốt trước khi nộp: cách tính kỳ theo 24 giờ hay ngày lịch/múi giờ; quyền Admin toàn hệ thống; cách hiện bucket chưa phân loại; và yêu cầu snapshot nhất quán khi đọc nhiều bảng. Các quyết định này cần cập nhật đồng thời đặc tả, biểu đồ và truy vấn nếu thay đổi.

## 12. Danh mục nguồn và cách tái tạo hình

Chín file .puml nằm trong thư mục sơ đồ. Các nguồn Mermaid dùng cho mô hình lớp miền, ERD và phiên bản Activity/Sequence mới nhất là [uc08-class-domain.mmd](TV5-UC08-Bao-cao/uc08-class-domain.mmd), [uc08-erd.mmd](TV5-UC08-Bao-cao/uc08-erd.mmd), [uc08-activity.mmd](TV5-UC08-Bao-cao/uc08-activity.mmd) và [uc08-sequence.mmd](TV5-UC08-Bao-cao/uc08-sequence.mmd). Mermaid của Activity/Sequence được nhúng trong tài liệu để thay thế ảnh PNG/SVG cũ chưa phản ánh các lần chỉnh sửa gần nhất.

Sáu mockup giao diện sinh bởi [render_mockups.py](TV5-UC08-Bao-cao/render_mockups.py); dữ liệu từ sample-report.json. Script chỉ đọc dữ liệu minh họa và ghi ảnh/kết quả vào thư mục này, không kết nối DB ứng dụng.

Tái tạo toàn bộ (cần Java, PlantUML 1.2024.8 và Python có Pillow):

~~~powershell
.\docs\TV5-UC08-Bao-cao\render_all.ps1 -PlantUmlJar 'C:\tools\plantuml-1.2024.8.jar' -JavaPath 'C:\Program Files\Java\jdk-23\bin\java.exe'
~~~

Nếu chỉ cần tái tạo mockup: chạy python docs/TV5-UC08-Bao-cao/render_mockups.py. Nguồn PlantUML giữ lại để chỉnh sửa; PNG dùng chèn Word, SVG dùng phóng to. Đây là mô hình UML và mockup thiết kế; chuyển sang dự án Astah cần dựng/import bằng công cụ nhóm sử dụng.
