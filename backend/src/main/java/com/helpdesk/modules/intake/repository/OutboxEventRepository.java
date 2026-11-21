package com.helpdesk.modules.intake.repository;

import com.helpdesk.modules.intake.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Đọc batch sự kiện PENDING theo chuẩn MySQL 8.0 FOR UPDATE SKIP LOCKED
     * để nhiều Worker chạy song song không xử lý trùng.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE publish_status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);
}
