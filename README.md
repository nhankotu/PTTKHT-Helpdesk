# PTTKHT — Helpdesk đa kênh tích hợp AI

Dự án Chủ đề 07, học phần Thiết kế và Xây dựng phần mềm 20261, nhóm 5 thành viên. Repository gồm tài liệu phân tích, kế hoạch, hồ sơ thiết kế UC08 và prototype Helpdesk chạy cục bộ.

## Tài liệu chính

- [Kế hoạch tuần 1 — phân công TV1–TV5](Ke-hoach-tuan-1-Use-Case-Chu-de-07.md).
- [Phân tích và thiết kế tổng thể](Phan-tich-thiet-ke-Chu-de-07-Helpdesk.md).
- [TV5 — Báo cáo ticket, SLA và CSAT (UC08)](docs/TV5-UC08-Bao-cao.md): đặc tả, 9 sơ đồ UML/phân rã và 6 mockup giao diện; có PNG, SVG và nguồn PlantUML.
- [Đề bài học phần](<Topics-TK và XD phần mềm-20261.md>).
- [Tiến độ và giới hạn prototype](helpdesk/docs/progress.md).
- [Checklist OpenSpec](openspec/changes/helpdesk-mvp-planning/tasks.md).

## Chạy prototype

Yêu cầu Python 3.11 trở lên và uv. Tại thư mục repository:

```powershell
Set-Location helpdesk
uv sync --locked
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Mở http://127.0.0.1:8000. Tài khoản, dữ liệu demo, cấu hình và các luồng dùng thử được mô tả trong [README ứng dụng](helpdesk/README.md).

## Cấu trúc

```text
docs/TV5-UC08-Bao-cao.md     Hồ sơ cá nhân TV5 — UC08
docs/TV5-UC08-Bao-cao/      Nguồn UML, PNG/SVG, mockup và dữ liệu minh họa
helpdesk/                   Backend FastAPI, giao diện web, kiểm thử và hướng dẫn
openspec/                   Đặc tả nghiệp vụ và checklist triển khai
```

Tuần 1 tập trung Actor, tám Use Case và đặc tả. Mô hình lớp/giao diện UC08 là phần thiết kế tiếp theo; prototype có trạng thái nghiệm thu riêng. Các nguồn bài tập AIMS dùng làm mẫu cá nhân, DB cục bộ, môi trường Python và cấu hình riêng không đưa vào repository.
