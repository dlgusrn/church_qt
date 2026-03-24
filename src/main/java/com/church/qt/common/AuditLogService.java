package com.church.qt.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public void record(Long actorTeacherId, String actionType, String detail) {
        try {
            namedParameterJdbcTemplate.update(
                    """
                    insert into audit_logs(actor_teacher_id, action_type, detail, created_at)
                    values (:actorTeacherId, :actionType, :detail, CURRENT_TIMESTAMP)
                    """,
                    new MapSqlParameterSource()
                            .addValue("actorTeacherId", actorTeacherId)
                            .addValue("actionType", actionType)
                            .addValue("detail", detail)
            );
        } catch (RuntimeException e) {
            log.warn("Failed to write audit log. actorTeacherId={}, actionType={}, detail={}", actorTeacherId, actionType, detail, e);
        }
    }
}
