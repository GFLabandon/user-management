package io.github.gflabandon.counselor.mapper;

import java.util.List;
import io.github.gflabandon.counselor.entity.AuditEvent;
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
}
