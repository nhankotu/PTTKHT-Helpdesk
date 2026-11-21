package com.helpdesk.modules.sla.service;

/**
 * Port tạo thông báo cho module routing (assignment) và sla (warning/breach).
 */
public interface NotificationPort {

    void notifyAssignment(Long recipientId, Long ticketId, Long assignmentHistoryId);

    void notifySlaEvent(Long recipientId, Long ticketId, Long slaEventId);
}
