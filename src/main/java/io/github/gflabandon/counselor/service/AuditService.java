package io.github.gflabandon.counselor.service;

import java.util.List;
import io.github.gflabandon.counselor.entity.AuditEvent;
import io.github.gflabandon.counselor.mapper.AuditMapper;
import io.github.gflabandon.counselor.web.AuditQuery;
import io.github.gflabandon.counselor.web.PageResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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

    public record SearchResult(PageResult<AuditEvent> page, String timeZone) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchResult search(AuditQuery query) {
        var filter = query.filter();
        long total = mapper.count(filter);
        int pages = (int) Math.min(Integer.MAX_VALUE, Math.max(1, (total + query.getSize() - 1) / query.getSize()));
        int page = Math.max(1, Math.min(query.getPage(), pages));
        var result = new PageResult<>(mapper.findPage(filter, query.getSize(), (long) (page - 1) * query.getSize()),
                total, page, query.getSize(), pages);
        return new SearchResult(result, mapper.timeZone());
    }
}
