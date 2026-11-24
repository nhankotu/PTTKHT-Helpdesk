package com.helpdesk.modules.ticket.entity;

public enum TicketStatus {
    NEW,
    ASSIGNED,
    IN_PROGRESS,
    WAITING_CUSTOMER,
    RESOLVED,
    CLOSED,
    CANCELLED;

    public boolean isOpen() {
        return this != RESOLVED && this != CLOSED && this != CANCELLED;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
