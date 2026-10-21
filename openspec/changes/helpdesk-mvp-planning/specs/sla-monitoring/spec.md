# Spec Delta

## Purpose

Theo dõi các cam kết thời gian phản hồi và giải quyết ticket, cảnh báo nhóm hỗ trợ khi gần hoặc đã quá hạn và cung cấp số liệu vận hành có thể đối chiếu.

**Use Case tuần 1:** UC05 — Theo dõi SLA, cảnh báo và chuyển cấp; UC08 — Xem báo cáo ticket, SLA và CSAT.

## ADDED Requirements

### Requirement: Apply a versioned SLA policy to each ticket
The system SHALL assign an SLA policy version and calculate first-response and resolution deadlines from the ticket priority when the ticket is created.

#### Scenario: Ticket is created with a supported priority
- **WHEN** a new ticket is persisted with a supported priority
- **THEN** the system records the applicable policy version and both deadlines on the ticket's SLA record

#### Scenario: SLA policy changes later
- **WHEN** an administrator publishes a new SLA policy version
- **THEN** existing tickets retain the policy version already recorded for them

### Requirement: Measure first response and resolution
The system SHALL record when the first qualifying response is successfully delivered and when a ticket is resolved.

#### Scenario: First qualifying response is delivered
- **WHEN** a customer-visible support response is successfully sent
- **THEN** the system records the response time and whether it was sent by an Agent or an authorized automated workflow

#### Scenario: Outbound response fails
- **WHEN** a support response fails to reach the customer channel
- **THEN** the system does not count it as a successful first response

### Requirement: Alert staff when SLA approaches or expires
The system SHALL create deduplicated alerts when a tracked SLA threshold is reached and make overdue tickets visible to the responsible Supervisor.

#### Scenario: Ticket reaches a warning threshold
- **WHEN** a ticket reaches the configured warning threshold before a deadline
- **THEN** the system creates one warning alert for that ticket and deadline type

#### Scenario: Ticket passes its deadline
- **WHEN** a tracked first-response or resolution deadline passes without completion
- **THEN** the system marks the ticket overdue and makes it visible for Supervisor escalation

#### Scenario: Scheduler retries an alert check
- **WHEN** the SLA scheduler processes the same threshold more than once
- **THEN** it does not create duplicate alerts for the same ticket and deadline type

### Requirement: Report support and SLA outcomes
The system SHALL provide authorized Supervisors with ticket volume, first-response time, resolution time, SLA compliance, and customer satisfaction summaries over a selected period.

#### Scenario: Supervisor views a report
- **WHEN** an authorized Supervisor requests a report for a period
- **THEN** the system returns metrics derived from tickets visible to that Supervisor with the selected period applied

#### Scenario: User without reporting permission requests a report
- **WHEN** a user without reporting permission requests supervisor metrics
- **THEN** the system denies the request without disclosing the metrics
