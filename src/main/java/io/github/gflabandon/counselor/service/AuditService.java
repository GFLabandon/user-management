package io.github.gflabandon.counselor.service;

import java.util.List;
import io.github.gflabandon.counselor.entity.AuditEvent;
import io.github.gflabandon.counselor.mapper.AuditMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditMapper mapper;
    public AuditService(AuditMapper mapper) { this.mapper = mapper; }

    public static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void success(String actor, String action, String targetType, Integer id) {
        mapper.insert(actor, action, targetType, id == null ? null : id.toString(), "SUCCESS", "OK");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void event(String actor, String action, String targetType, Integer id, String outcome, String reason) {
        mapper.insert(actor, action, targetType, id == null ? null : id.toString(), outcome, reason);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recent() { return mapper.recent(); }
}
