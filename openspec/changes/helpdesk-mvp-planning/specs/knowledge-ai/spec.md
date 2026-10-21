# Spec Delta

## Purpose

Hỗ trợ nhân viên phân loại yêu cầu và trả lời câu hỏi nghiệp vụ dựa trên bài viết tri thức đã được xuất bản, có thể truy nguồn và chuyển cho người khi thiếu căn cứ.

**Use Case tuần 1:** UC04 — AI phân loại và định tuyến ticket; UC06 — Quản lý bài viết Knowledge Base; UC07 — AI RAG gợi ý câu trả lời có dẫn nguồn.

## ADDED Requirements

### Requirement: Classify incoming tickets
The system SHALL produce a supported ticket category for an accepted ticket or mark the classification for human review.

#### Scenario: Classification maps to a supported category
- **WHEN** the classifier returns a valid supported category
- **THEN** the system records the category and makes it available to the routing rules

#### Scenario: Classification fails or is unusable
- **WHEN** the classifier is unavailable, returns malformed output, or cannot determine a supported category
- **THEN** the system marks the ticket for human classification and preserves the ticket for staff processing

### Requirement: Route tickets using explicit rules
The system SHALL use configured category-to-team rules to select a team and SHALL allow authorized staff to review and change the assignment.

#### Scenario: Category has an active routing rule
- **WHEN** a ticket has a category with an active routing rule
- **THEN** the system places it in the configured team's queue and records the routing result

#### Scenario: No team member is available
- **WHEN** a routing rule selects a team with no eligible available Agent
- **THEN** the ticket remains in that team's queue without losing its assignment or history

### Requirement: Retrieve only eligible knowledge articles
The system SHALL retrieve only published, active knowledge content that the requesting workflow is authorized to use.

#### Scenario: Relevant published article exists
- **WHEN** a question matches an active published article available to the workflow
- **THEN** the system may use relevant article content as answer context and retain the article reference

#### Scenario: No eligible article is found
- **WHEN** no relevant eligible article is found
- **THEN** the system does not generate an automatic customer answer and offers the ticket to an Agent

### Requirement: Ground AI answers in cited knowledge
The system SHALL provide a customer-facing AI answer only when it can associate the answer with eligible knowledge sources and SHALL include those sources for the customer or Agent to inspect.

#### Scenario: Answer is supported by knowledge sources
- **WHEN** retrieved active articles support a generated answer
- **THEN** the system presents the answer with references to the supporting articles

#### Scenario: Sources do not support a safe answer
- **WHEN** sources are conflicting, insufficient, inaccessible, or expired
- **THEN** the system withholds automatic sending and routes the ticket to an Agent with the reason recorded

### Requirement: Preserve human fallback when AI is unavailable
The system SHALL keep ticket processing available to staff when an AI operation fails or exceeds its configured time limit.

#### Scenario: AI request fails or times out
- **WHEN** an AI operation fails or exceeds its configured time limit
- **THEN** the system records the failure and keeps the ticket available in the staff queue without sending an unsupported answer

#### Scenario: Customer requests a person
- **WHEN** the customer explicitly requests a human Agent
- **THEN** the system routes the ticket to staff and stops automatic customer-facing responses for that request

### Requirement: Automatically answer explicitly approved FAQs
The system SHALL automatically send only exact normalized FAQ matches approved by a Supervisor or Admin for the currently published article version, using the approved article content and a source reference.

#### Scenario: Eligible ticket matches an approved question
- **WHEN** a new NORMAL or LOW priority ticket's initial customer message exactly matches an approved question after normalization, with no human request or staff intervention
- **THEN** the system queues at most one FAQ response for that ticket and leaves the ticket unresolved

#### Scenario: A source or approval changes before delivery
- **WHEN** the article or approval changes, a customer adds a message, or staff intervenes before a queued FAQ is delivered
- **THEN** the queued answer is cancelled without recording a first response and cannot be revived by reapproving a newer article version

#### Scenario: Generated answer is not an approved FAQ
- **WHEN** an LLM returns a draft for a question without an eligible approved exact match
- **THEN** the system requires staff review and does not automatically send the generated text
