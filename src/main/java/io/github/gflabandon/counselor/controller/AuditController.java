package io.github.gflabandon.counselor.controller;
import io.github.gflabandon.counselor.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }
    @GetMapping("/audit") public String list(Model model) { model.addAttribute("events", audit.recent()); return "audit/list"; }
}
