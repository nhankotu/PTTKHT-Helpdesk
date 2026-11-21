package com.helpdesk.modules.routing.repository;

import com.helpdesk.modules.routing.entity.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {

    Optional<RoutingRule> findByCategoryIdAndActiveTrue(Long categoryId);

    boolean existsByCategoryIdAndActiveTrueAndIdNot(Long categoryId, Long id);

    List<RoutingRule> findAllByOrderByIdAsc();
}
