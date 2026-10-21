# Spec Delta

## Purpose

Quản lý vòng đời yêu cầu hỗ trợ thành ticket thống nhất, cung cấp lịch sử hội thoại và thay đổi để khách hàng cùng nhân viên theo dõi việc xử lý.

**Use Case tuần 1:** UC03 — Xử lý và cập nhật trạng thái ticket. Quyền truy cập là quy tắc xuyên suốt các Use Case có dữ liệu cần bảo vệ.

## ADDED Requirements

### Requirement: Customer can access only their own tickets
The system SHALL authorize customer ticket reads and updates against the authenticated customer's identity.

#### Scenario: Customer reads their ticket
- **WHEN** an authenticated customer requests a ticket they own
- **THEN** the system returns the ticket and customer-visible messages

#### Scenario: Customer requests another customer's ticket
- **WHEN** an authenticated customer requests a ticket they do not own
- **THEN** the system denies access without disclosing the ticket contents

### Requirement: Keep internal notes private
The system SHALL distinguish internal notes from customer-visible messages and SHALL prevent customers from reading internal notes.

#### Scenario: Agent adds an internal note
- **WHEN** an authorized Agent records an internal note on a ticket
- **THEN** the note is visible to authorized staff and excluded from customer-facing responses and views

### Requirement: Enforce ticket lifecycle transitions
The system SHALL allow only valid transitions among `NEW`, `IN_PROGRESS`, `WAITING_CUSTOMER`, `RESOLVED`, and `CLOSED`.

#### Scenario: Agent starts a new ticket
- **WHEN** an Agent accepts a ticket in `NEW`
- **THEN** the ticket transitions to `IN_PROGRESS` and the change is recorded in its history

#### Scenario: Customer replies to a waiting ticket
- **WHEN** a customer sends a new message on a ticket in `WAITING_CUSTOMER`
- **THEN** the ticket returns to `IN_PROGRESS` and records the event

#### Scenario: Customer reopens a resolved ticket
- **WHEN** a customer reports that a `RESOLVED` issue remains unresolved
- **THEN** the ticket returns to `IN_PROGRESS` and retains its prior history

#### Scenario: User requests an invalid transition
- **WHEN** a user attempts a transition not allowed by the lifecycle
- **THEN** the system rejects the update and leaves the ticket state unchanged

### Requirement: Record ticket assignment and lifecycle history
The system SHALL record status, assignee, team, and priority changes with the actor and time of change.

#### Scenario: Supervisor reassigns a ticket
- **WHEN** an authorized Supervisor changes the team or assignee
- **THEN** the system updates the current assignment and appends an auditable history event

### Requirement: Detect concurrent ticket updates
The system SHALL detect when a ticket was changed after a client read the version it is updating.

#### Scenario: Two staff members update the same ticket
- **WHEN** a stale ticket version is submitted for update
- **THEN** the system rejects it as a conflict and returns the current ticket version
