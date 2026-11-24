package com.helpdesk.modules.identity.repository;

import com.helpdesk.modules.identity.entity.SupportTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SupportTeamRepository extends JpaRepository<SupportTeam, Long> {

    @EntityGraph(attributePaths = {"supervisor", "members"})
    List<SupportTeam> findAllByOrderByNameAsc();

    Optional<SupportTeam> findByCode(String code);

    @Query("""
            SELECT DISTINCT t.id FROM SupportTeam t
            LEFT JOIN t.members m
            WHERE t.active = true AND (m.id = :userId OR t.supervisor.id = :userId)
            """)
    Set<Long> findActiveTeamIdsForUser(@Param("userId") Long userId);

    @Query("""
            SELECT m.id FROM SupportTeam t
            JOIN t.members m
            WHERE t.id = :teamId AND t.active = true AND m.active = true AND m.available = true
            ORDER BY m.id ASC
            """)
    List<Long> findAvailableActiveMemberIds(@Param("teamId") Long teamId);

    @Query("""
            SELECT t.id FROM SupportTeam t
            WHERE t.active = true AND t.supervisor.id = :supervisorId
            ORDER BY t.id ASC
            """)
    List<Long> findSupervisedTeamIds(@Param("supervisorId") Long supervisorId);
}
