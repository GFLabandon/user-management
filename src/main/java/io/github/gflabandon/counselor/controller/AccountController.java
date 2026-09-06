package io.github.gflabandon.counselor.controller;

import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.AccountForm;
import jakarta.validation.Valid;
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
    @GetMapping("/new") public String createForm(Model model) {
        model.addAttribute("accountForm", new AccountForm()); return "accounts/form";
    }
    @GetMapping("/{id}/edit") public String editForm(@PathVariable int id, Model model) {
        var account = accounts.get(id); var form = new AccountForm();
        form.setUsername(account.getUsername()); form.setRole(account.getRole()); form.setEnabled(account.getEnabled());
        form.setCounselorId(account.getCounselorId()); form.setVersion(account.getVersion());
        model.addAttribute("accountForm", form); model.addAttribute("recordId", id); return "accounts/form";
    }
    @PostMapping public String create(@Valid @ModelAttribute AccountForm accountForm, BindingResult errors,
            Model model, RedirectAttributes redirect) { return save(null, accountForm, errors, model, redirect); }
    @PostMapping("/{id}") public String update(@PathVariable int id, @RequestParam int version,
            @Valid @ModelAttribute AccountForm accountForm, BindingResult errors, Model model, RedirectAttributes redirect) {
        return save(id, accountForm, errors, model, redirect);
    }
    private String save(Integer id, AccountForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        model.addAttribute("recordId", id);
        try {
            if (errors.hasErrors()) {
                audit.event(AuditService.actor(), "ACCOUNT_WRITE", "ACCOUNT", id, "FAILURE", "VALIDATION");
                return "accounts/form";
            }
            accounts.save(id, form, AuditService.actor());
            redirect.addFlashAttribute("success", "账号已保存；该账号的旧会话将在下次请求时失效。");
            return "redirect:/accounts";
        } catch (BusinessException | DataIntegrityViolationException exception) {
            audit.event(AuditService.actor(), "ACCOUNT_WRITE", "ACCOUNT", id, "FAILURE", "WRITE_FAILED");
            model.addAttribute("error", exception instanceof DataIntegrityViolationException ? "登录名或档案关联已被使用。" : exception.getMessage());
            return "accounts/form";
        } finally { form.setPassword(""); }
    }
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public String invalid(BusinessException exception, Model model) {
        model.addAttribute("error", exception.getMessage()); return "error/business";
    }
}
