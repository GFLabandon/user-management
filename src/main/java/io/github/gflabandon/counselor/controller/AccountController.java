package io.github.gflabandon.counselor.controller;

import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.AccountForm;
import io.github.gflabandon.counselor.web.CounselorOption;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/accounts")
public class AccountController {
    private final AccountService accounts;
    private final AuditService audit;
    public AccountController(AccountService accounts, AuditService audit) { this.accounts = accounts; this.audit = audit; }
    @InitBinder("accountForm") void bind(WebDataBinder binder) {
        binder.setAllowedFields("username", "password", "role", "enabled", "counselorId", "version");
    }
    @GetMapping public String list(Model model) { model.addAttribute("accounts", accounts.all()); return "accounts/list"; }
    @GetMapping("/counselor-options") @ResponseBody
    public List<CounselorOption> counselorOptions(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Integer accountId) {
        return accounts.counselorOptions(keyword, accountId);
    }
    @GetMapping("/new") public String createForm(Model model) {
        model.addAttribute("accountForm", new AccountForm()); return "accounts/form";
    }
    @GetMapping("/{id}/edit") public String editForm(@PathVariable int id, Model model) {
        var account = accounts.get(id); var form = new AccountForm();
        form.setUsername(account.getUsername()); form.setRole(account.getRole()); form.setEnabled(account.getEnabled());
        form.setCounselorId(account.getCounselorId()); form.setVersion(account.getVersion());
        model.addAttribute("accountForm", form); model.addAttribute("recordId", id);
        model.addAttribute("selectedCounselor", accounts.counselorOption(form.getCounselorId()));
        return "accounts/form";
    }
    @PostMapping public String create(@Valid @ModelAttribute AccountForm accountForm, BindingResult errors,
            Model model, RedirectAttributes redirect, HttpServletResponse response) { return save(null, accountForm, errors, model, redirect, response); }
    @PostMapping("/{id}") public String update(@PathVariable int id, @RequestParam int version,
            @Valid @ModelAttribute AccountForm accountForm, BindingResult errors, Model model, RedirectAttributes redirect, HttpServletResponse response) {
        return save(id, accountForm, errors, model, redirect, response);
    }
    private String save(Integer id, AccountForm form, BindingResult errors, Model model, RedirectAttributes redirect, HttpServletResponse response) {
        model.addAttribute("recordId", id);
        model.addAttribute("selectedCounselor", accounts.counselorOption(form.getCounselorId()));
        try {
            if (errors.hasErrors()) {
                response.setStatus(400);
                audit.event(AuditService.actor(), "ACCOUNT_WRITE", "ACCOUNT", id, "FAILURE", "VALIDATION");
                return "accounts/form";
            }
            accounts.save(id, form, AuditService.actor());
            redirect.addFlashAttribute("success", "账号已保存；该账号的旧会话将在下次请求时失效。");
            return "redirect:/accounts";
        } catch (BusinessException | DataIntegrityViolationException exception) {
            if (exception instanceof RecordNotFoundException missing) throw missing;
            response.setStatus(exception instanceof EditConflictException || exception instanceof DataIntegrityViolationException ? 409 : 400);
            model.addAttribute("conflict", exception instanceof EditConflictException);
            audit.event(AuditService.actor(), "ACCOUNT_WRITE", "ACCOUNT", id, "FAILURE", "WRITE_FAILED");
            model.addAttribute("error", exception instanceof DataIntegrityViolationException ? "登录名或档案关联已被使用。" : exception.getMessage());
            return "accounts/form";
        } finally { form.setPassword(""); }
    }
}
