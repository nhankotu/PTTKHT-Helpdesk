package com.helpdesk.modules.reporting.repository;

import com.helpdesk.modules.reporting.dto.ReportingDtos.CategoryCountDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.RecentSlaAlertDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ScoreCountDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.StatusCountDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Repository chuyên biệt cho phân hệ Báo cáo (UC08).
 * Đọc trực tiếp từ View v_ticket_sla_summary và các bảng liên quan (read-only),
 * giữ trọn ranh giới Modular Monolith mà không truy cập Repository của module khác.
 */
@Repository
public class ReportingQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportingQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SlaAggregateRow(
            Double avgFirstResponseSeconds,
            Double avgNetResolutionSeconds,
            long eligibleSlaCount,
            long metSlaCount,
            long overdueTicketCount
    ) {
    }

    public record CsatAggregateRow(
            Double averageScore,
            long submittedCount
    ) {
    }

    public List<StatusCountDto> countTicketsByStatus(Instant from, Instant asOf, List<Long> teamIds) {
        String sql = """
                SELECT v.status, COUNT(*) AS cnt
                FROM v_ticket_sla_summary v
                WHERE v.created_at >= :fromTs AND v.created_at <= :asOfTs
                """ + teamFilterClause("v", teamIds) + """
                GROUP BY v.status
                """;
        return jdbc.query(sql, buildParams(from, asOf, teamIds),
                (rs, rowNum) -> new StatusCountDto(rs.getString("status"), rs.getLong("cnt")));
    }

    public List<CategoryCountDto> countTicketsByCategory(Instant from, Instant asOf, List<Long> teamIds) {
        String sql = """
                SELECT c.id AS category_id, c.code AS category_code, c.name AS category_name, COUNT(*) AS cnt
                FROM v_ticket_sla_summary v
                JOIN category c ON c.id = v.category_id
                WHERE v.created_at >= :fromTs AND v.created_at <= :asOfTs
                """ + teamFilterClause("v", teamIds) + """
                GROUP BY c.id, c.code, c.name
                ORDER BY cnt DESC, c.name ASC
                """;
        return jdbc.query(sql, buildParams(from, asOf, teamIds),
                (rs, rowNum) -> new CategoryCountDto(
                        rs.getLong("category_id"),
                        rs.getString("category_code"),
                        rs.getString("category_name"),
                        rs.getLong("cnt")
                ));
    }

    public SlaAggregateRow aggregateSlaMetrics(Instant from, Instant asOf, List<Long> teamIds) {
        String sql = """
                SELECT
                    AVG(CASE WHEN v.first_response_at IS NOT NULL
                             THEN GREATEST(0, TIMESTAMPDIFF(SECOND, v.created_at, v.first_response_at))
                             ELSE NULL END) AS avg_fr_sec,
                    AVG(CASE WHEN v.resolved_at IS NOT NULL
                             THEN GREATEST(0, TIMESTAMPDIFF(SECOND, v.created_at, v.resolved_at) - v.resolution_paused_seconds)
                             ELSE NULL END) AS avg_res_sec,
                    COALESCE(SUM(CASE WHEN v.first_response_status IN ('MET', 'BREACHED') THEN 1 ELSE 0 END), 0)
                      + COALESCE(SUM(CASE WHEN v.resolution_status IN ('MET', 'BREACHED') THEN 1 ELSE 0 END), 0) AS eligible_cnt,
                    COALESCE(SUM(CASE WHEN v.first_response_status = 'MET' THEN 1 ELSE 0 END), 0)
                      + COALESCE(SUM(CASE WHEN v.resolution_status = 'MET' THEN 1 ELSE 0 END), 0) AS met_cnt,
                    COALESCE(SUM(CASE WHEN v.status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')
                                       AND (v.first_response_status = 'BREACHED' OR v.resolution_status = 'BREACHED')
                                      THEN 1 ELSE 0 END), 0) AS overdue_cnt
                FROM v_ticket_sla_summary v
                WHERE v.created_at >= :fromTs AND v.created_at <= :asOfTs
                """ + teamFilterClause("v", teamIds);

        return jdbc.queryForObject(sql, buildParams(from, asOf, teamIds), (rs, rowNum) -> {
            Object frObj = rs.getObject("avg_fr_sec");
            Object resObj = rs.getObject("avg_res_sec");
            return new SlaAggregateRow(
                    frObj != null ? ((Number) frObj).doubleValue() : null,
                    resObj != null ? ((Number) resObj).doubleValue() : null,
                    rs.getLong("eligible_cnt"),
                    rs.getLong("met_cnt"),
                    rs.getLong("overdue_cnt")
            );
        });
    }

    public List<RecentSlaAlertDto> findRecentSlaAlerts(Instant from, Instant asOf, List<Long> teamIds, int limit) {
        String sql = """
                SELECT
                    se.id AS sla_event_id,
                    t.id AS ticket_id,
                    t.ticket_code,
                    t.subject,
                    t.priority,
                    ts.metric,
                    se.event_type,
                    ts.deadline_at,
                    se.created_at,
                    EXISTS (SELECT 1 FROM escalation_action ea WHERE ea.sla_event_id = se.id) AS handled
                FROM sla_event se
                JOIN ticket_sla ts ON ts.id = se.ticket_sla_id
                JOIN ticket t ON t.id = ts.ticket_id
                WHERE se.created_at >= :fromTs AND se.created_at <= :asOfTs
                """ + teamFilterClause("t", teamIds) + """
                ORDER BY se.created_at DESC
                LIMIT :limitRows
                """;
        MapSqlParameterSource params = buildParams(from, asOf, teamIds)
                .addValue("limitRows", limit);
        return jdbc.query(sql, params, (rs, rowNum) -> new RecentSlaAlertDto(
                rs.getLong("sla_event_id"),
                rs.getLong("ticket_id"),
                rs.getString("ticket_code"),
                rs.getString("subject"),
                rs.getString("priority"),
                rs.getString("metric"),
                rs.getString("event_type"),
                toInstant(rs.getTimestamp("deadline_at")),
                toInstant(rs.getTimestamp("created_at")),
                rs.getBoolean("handled")
        ));
    }

    public CsatAggregateRow aggregateCsatMetrics(Instant from, Instant asOf, List<Long> teamIds) {
        String sql = """
                SELECT
                    AVG(sr.score) AS avg_score,
                    COUNT(sr.score) AS submitted_cnt
                FROM satisfaction_rating sr
                JOIN ticket t ON t.id = sr.ticket_id
                WHERE sr.submitted_at IS NOT NULL
                  AND sr.submitted_at >= :fromTs AND sr.submitted_at <= :asOfTs
                """ + teamFilterClause("t", teamIds);
        return jdbc.queryForObject(sql, buildParams(from, asOf, teamIds), (rs, rowNum) -> {
            Object avgObj = rs.getObject("avg_score");
            return new CsatAggregateRow(
                    avgObj != null ? ((Number) avgObj).doubleValue() : null,
                    rs.getLong("submitted_cnt")
            );
        });
    }

    public List<ScoreCountDto> countCsatDistribution(Instant from, Instant asOf, List<Long> teamIds) {
        String sql = """
                SELECT sr.score, COUNT(*) AS cnt
                FROM satisfaction_rating sr
                JOIN ticket t ON t.id = sr.ticket_id
                WHERE sr.submitted_at IS NOT NULL
                  AND sr.submitted_at >= :fromTs AND sr.submitted_at <= :asOfTs
                """ + teamFilterClause("t", teamIds) + """
                GROUP BY sr.score
                """;
        return jdbc.query(sql, buildParams(from, asOf, teamIds),
                (rs, rowNum) -> new ScoreCountDto(rs.getInt("score"), rs.getLong("cnt")));
    }

    private String teamFilterClause(String alias, List<Long> teamIds) {
        if (teamIds == null) {
            return "";
        }
        return " AND " + alias + ".team_id IN (:teamIds) ";
    }

    private MapSqlParameterSource buildParams(Instant from, Instant asOf, List<Long> teamIds) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTs", Timestamp.from(from))
                .addValue("asOfTs", Timestamp.from(asOf));
        if (teamIds != null) {
            params.addValue("teamIds", teamIds);
        }
        return params;
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
