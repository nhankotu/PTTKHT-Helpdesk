# Spec Delta

## Purpose

Tiếp nhận yêu cầu từ nhiều nguồn vào một luồng dữ liệu Helpdesk thống nhất, có thể truy vết và chống tạo bản ghi trùng khi kênh gửi lại sự kiện.

**Use Case tuần 1:** UC01 — Gửi và tiếp nhận yêu cầu hỗ trợ; UC02 — Tạo và tổng hợp ticket.

## ADDED Requirements

### Requirement: Normalize incoming channel requests
The system SHALL convert each supported channel request into a common ticket and message representation while retaining its source channel and external identifiers.

#### Scenario: Request is accepted from a supported channel
- **WHEN** a valid request arrives from a configured channel
- **THEN** the system records its channel, customer reference, subject, message content, and external event identifier in the common representation

#### Scenario: Request payload is invalid
- **WHEN** a channel request is missing required content or has an unsupported payload
- **THEN** the system rejects or quarantines the event with a recorded reason and creates no partial ticket

### Requirement: Process channel events idempotently
The system SHALL process a channel event at most once using its channel and external event identifier.

#### Scenario: Channel retries a previously processed event
- **WHEN** the system receives an event with the same channel and external event identifier again
- **THEN** it returns the existing processing result and creates no duplicate ticket or message

### Requirement: Persist requests before AI processing
The system SHALL persist an accepted ticket and its initial message before starting AI classification or response generation.

#### Scenario: AI service is unavailable after intake
- **WHEN** the ticket is saved and the AI service is unavailable
- **THEN** the ticket remains available in a human-processing queue with an AI failure status recorded

#### Scenario: Persistence fails
- **WHEN** the system cannot persist the ticket or initial message
- **THEN** it does not report successful intake and makes the failure visible to the channel adapter for retry or review

### Requirement: Track outbound delivery before counting first response
The system SHALL track queued, delivered, failed, and cancelled outbound messages and SHALL count the first response only after the channel adapter confirms delivery.

#### Scenario: Simulated channel delivery fails
- **WHEN** the configured local email adapter fails to deliver a response
- **THEN** the message remains available to authorized staff as failed, is not shown to the customer, and does not stop the first-response SLA clock

#### Scenario: Staff retries a failed response
- **WHEN** authorized staff retries a failed message and the adapter succeeds
- **THEN** the system delivers the same message without duplication and records its successful delivery time for SLA

#### Scenario: User tries to resolve while a reply is unsent
- **WHEN** a user attempts to resolve or close a ticket with a pending or failed response
- **THEN** the system rejects the transition until the response is successfully delivered
