package com.helpdesk.modules.sla.repository;

import com.helpdesk.modules.sla.entity.SlaPolicyTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlaPolicyTargetRepository extends JpaRepository<SlaPolicyTarget, Long> {

    List<SlaPolicyTarget> findByPolicyId(Long policyId);
}
