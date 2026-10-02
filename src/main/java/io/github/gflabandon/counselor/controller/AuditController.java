package io.github.gflabandon.counselor.controller;
import io.github.gflabandon.counselor.service.AuditService;
import io.github.gflabandon.counselor.service.BusinessException;
import io.github.gflabandon.counselor.web.AuditQuery;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
@Controller
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }
    @InitBinder("auditQuery")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("actor", "targetType", "targetId", "outcome", "startDate", "endDate", "page", "size");
    }

    @GetMapping("/audit")
    public String list(@Valid @ModelAttribute AuditQuery query, BindingResult errors,
                       Model model, HttpServletResponse response) {
        if (!errors.hasErrors()) {
            try {
                var search = audit.search(query);
                model.addAttribute("result", search.page());
                model.addAttribute("timeZone", search.timeZone());
                if (query.getPage() != search.page().page()) model.addAttribute("pageNotice", "页码超出范围，已显示最后一页。");
            } catch (BusinessException invalid) {
                errors.reject("auditQuery.invalid", invalid.getMessage());
            }
        }
        if (errors.hasErrors()) response.setStatus(400);
        return "audit/list";
    }
}
