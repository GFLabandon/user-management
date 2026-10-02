package io.github.gflabandon.counselor.mapper;

import java.util.List;
import io.github.gflabandon.counselor.entity.AuditEvent;
import io.github.gflabandon.counselor.web.AuditQuery;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditMapper {
    @Insert("""
            INSERT INTO audit_events (actor, action, target_type, target_id, outcome, reason)
            VALUES (#{actor}, #{action}, #{targetType}, #{targetId}, #{outcome}, #{reason})
            """)
    void insert(@Param("actor") String actor, @Param("action") String action,
                @Param("targetType") String targetType, @Param("targetId") String targetId,
                @Param("outcome") String outcome, @Param("reason") String reason);
    @Select("SELECT * FROM audit_events ORDER BY id DESC LIMIT 100")
    List<AuditEvent> recent();

    String FILTER = """
            <where>
              <if test="filter.actor != ''">AND LOWER(actor) = LOWER(#{filter.actor})</if>
              <if test="filter.targetType != ''">AND target_type = #{filter.targetType}</if>
              <if test="filter.targetId != ''">AND target_id = #{filter.targetId}</if>
              <if test="filter.outcome != ''">AND outcome = #{filter.outcome}</if>
              <if test="filter.from != null">AND occurred_at &gt;= #{filter.from}</if>
              <if test="filter.until != null">AND occurred_at &lt; #{filter.until}</if>
            </where>
            """;
    @Select("<script>SELECT COUNT(*) FROM audit_events " + FILTER + "</script>")
    long count(@Param("filter") AuditQuery.Filter filter);

    @Select("<script>SELECT * FROM audit_events " + FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<AuditEvent> findPage(@Param("filter") AuditQuery.Filter filter, @Param("size") int size, @Param("offset") long offset);

    @Select(value = "SELECT CASE WHEN @@session.time_zone = 'SYSTEM' THEN CONCAT('SYSTEM / ', @@system_time_zone) ELSE @@session.time_zone END", databaseId = "MySQL")
    @Select(value = "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'TIME ZONE'", databaseId = "H2")
    String timeZone();
}
