package io.github.gflabandon.counselor.controller;

import io.github.gflabandon.counselor.entity.Department;
import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.DepartmentForm;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/departments")
public class DepartmentController {
    private final DepartmentService service;
    private final AuditService audit;
    public DepartmentController(DepartmentService service, AuditService audit) { this.service = service; this.audit = audit; }
    @InitBinder("departmentForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("name", "active", "version");
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }
    @GetMapping
    public String list(Model model) { model.addAttribute("departments", service.all()); return "departments/list"; }
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("departmentForm", new DepartmentForm()); return "departments/form";
    }
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable int id, Model model) {
        Department department = service.get(id);
        DepartmentForm form = new DepartmentForm();
        form.setName(department.getName()); form.setActive(department.getActive()); form.setVersion(department.getVersion());
        model.addAttribute("departmentForm", form); model.addAttribute("recordId", id);
        return "departments/form";
    }
    @PostMapping
    public String create(@Valid @ModelAttribute DepartmentForm departmentForm, BindingResult errors,
                         Model model, RedirectAttributes redirect, HttpServletResponse response) {
        return save(null, departmentForm, errors, model, redirect, response);
    }
    @PostMapping("/{id}")
    public String update(@PathVariable int id, @RequestParam int version, @Valid @ModelAttribute DepartmentForm departmentForm, BindingResult errors,
                         Model model, RedirectAttributes redirect, HttpServletResponse response) {
        return save(id, departmentForm, errors, model, redirect, response);
    }
    private String save(Integer id, DepartmentForm form, BindingResult errors, Model model, RedirectAttributes redirect, HttpServletResponse response) {
        model.addAttribute("recordId", id);
        if (errors.hasErrors()) { response.setStatus(400); failure(id, "VALIDATION"); return "departments/form"; }
        try { service.save(id, form); }
        catch (BusinessException | DataIntegrityViolationException exception) {
            if (exception instanceof RecordNotFoundException missing) throw missing;
            response.setStatus(exception instanceof EditConflictException || exception instanceof DataIntegrityViolationException ? 409 : 400);
            model.addAttribute("conflict", exception instanceof EditConflictException);
            failure(id, "WRITE_FAILED");
            model.addAttribute("error", exception instanceof DataIntegrityViolationException ? "院系名称已存在。" : exception.getMessage());
            return "departments/form";
        }
        redirect.addFlashAttribute("success", "院系已保存。"); return "redirect:/departments";
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable int id, @RequestParam int version, RedirectAttributes redirect) {
        try { service.delete(id, version); redirect.addFlashAttribute("success", "院系已删除。"); }
        catch (BusinessException | DataIntegrityViolationException exception) {
            failure(id, "WRITE_FAILED");
            if (exception instanceof DataIntegrityViolationException) throw new BusinessException("院系已被引用，请使用停用功能。");
            throw (BusinessException) exception;
        }
        return "redirect:/departments";
    }
    private void failure(Integer id, String reason) {
        audit.event(AuditService.actor(), "DEPARTMENT_WRITE", "DEPARTMENT", id, "FAILURE", reason);
    }

}
