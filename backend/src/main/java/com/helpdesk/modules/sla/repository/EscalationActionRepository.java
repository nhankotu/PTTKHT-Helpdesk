package com.helpdesk.modules.sla.repository;

import com.helpdesk.modules.sla.entity.EscalationAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscalationActionRepository extends JpaRepository<EscalationAction, Long> {

    List<EscalationAction> findBySlaEventIdOrderByHandledAtAsc(Long slaEventId);

    boolean existsBySlaEventId(Long slaEventId);
}
