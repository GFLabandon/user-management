package io.github.gflabandon.counselor.controller;

import java.io.IOException;
import java.util.List;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.CounselorForm;
import io.github.gflabandon.counselor.web.CounselorListContext;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/counselors")
public class CounselorController {
    private final CounselorService service;
    private final AuditService audit;
    private final DepartmentService departments;
    private final FileStorageService storage;
    private final ImageLifecycle images;
    public CounselorController(CounselorService service, DepartmentService departments, FileStorageService storage, AuditService audit, ImageLifecycle images) {
        this.service = service; this.departments = departments; this.storage = storage; this.audit = audit; this.images = images;
    }

    @InitBinder("counselorForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("employeeNo", "name", "departmentId", "employmentStatus", "remark", "version");
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }
    @ModelAttribute("departments")
    List<Department> departments() { return departments.all(); }
    @ModelAttribute("statuses")
    EmploymentStatus[] statuses() { return EmploymentStatus.values(); }

    @ModelAttribute("listContext")
    CounselorListContext listContext(@RequestParam(defaultValue = "") String listKeyword,
                                     @RequestParam(required = false) Integer listDepartmentId,
                                     @RequestParam(required = false) EmploymentStatus listStatus,
                                     @RequestParam(required = false) Integer listPage,
                                     @RequestParam(defaultValue = "10") int listSize) {
        return new CounselorListContext(listKeyword, listDepartmentId, listStatus, listPage, listSize);
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String keyword,
                       @RequestParam(required = false) Integer departmentId,
                       @RequestParam(required = false) EmploymentStatus status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size, Model model) {
        var result = service.search(keyword, departmentId, status, page, size);
        model.addAttribute("result", result);
        model.addAttribute("listContext", new CounselorListContext(keyword, departmentId, status, result.page(), result.size()));
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("status", status);
        return "counselors/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("counselorForm", new CounselorForm());
        return form(model, null);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable int id, Model model) {
        Counselor counselor = service.get(id);
        CounselorForm form = new CounselorForm();
        form.setEmployeeNo(counselor.getEmployeeNo()); form.setName(counselor.getName());
        form.setDepartmentId(counselor.getDepartmentId()); form.setEmploymentStatus(counselor.getEmploymentStatus());
        form.setRemark(counselor.getRemark()); form.setVersion(counselor.getVersion());
        model.addAttribute("counselorForm", form);
        return form(model, id);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable int id, Model model) {
        model.addAttribute("counselor", service.get(id));
        model.addAttribute("history", service.history(id));
        return "counselors/detail";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute CounselorForm counselorForm, BindingResult errors,
                         @RequestParam(required = false) MultipartFile photo, Principal principal,
                         Model model, RedirectAttributes redirect, HttpServletResponse response) {
        return save(null, counselorForm, errors, photo, principal, model, redirect, response);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable int id, @RequestParam int version, @Valid @ModelAttribute CounselorForm counselorForm, BindingResult errors,
                         @RequestParam(required = false) MultipartFile photo, Principal principal,
                         Model model, RedirectAttributes redirect, HttpServletResponse response) {
        return save(id, counselorForm, errors, photo, principal, model, redirect, response);
    }

    private String save(Integer id, CounselorForm input, BindingResult errors, MultipartFile photo,
                        Principal principal, Model model, RedirectAttributes redirect, HttpServletResponse response) {
        if (id != null) service.get(id);
        if (errors.hasErrors()) {
            response.setStatus(400);
            model.addAttribute("uploadRetry", photo != null && !photo.isEmpty());
            failure(id, "VALIDATION"); return form(model, id);
        }
        String newImage = null;
        int savedId;
        try {
            if (photo != null && !photo.isEmpty()) newImage = storage.storeImage(photo);
            String actor = principal.getName();
            if (id == null) savedId = service.create(input, newImage, actor);
            else { service.update(id, input, newImage, actor); savedId = id; }
        } catch (IOException | BusinessException | DataIntegrityViolationException exception) {
            images.retainForReview(newImage);
            if (exception instanceof RecordNotFoundException missing) throw missing;
            response.setStatus(exception instanceof EditConflictException || exception instanceof DataIntegrityViolationException ? 409
                    : exception instanceof IOException && !(exception instanceof UploadValidationException) ? 500 : 400);
            failure(id, "SAVE_FAILED");
            model.addAttribute("error", exception instanceof DataIntegrityViolationException
                    ? "工号已存在或关联数据无效，请检查后重试。"
                    : exception instanceof IOException && !(exception instanceof UploadValidationException)
                    ? "图片保存失败，请稍后重试或联系管理员，并提供请求编号。" : exception.getMessage());
            if (exception instanceof EditConflictException) model.addAttribute("conflict", true);
            model.addAttribute("uploadRetry", photo != null && !photo.isEmpty());
            return form(model, id);
        } catch (RuntimeException exception) {
            images.retainForReview(newImage);
            failure(id, "SAVE_FAILED");
            throw exception;
        }
        redirect.addFlashAttribute("success", id == null ? "辅导员档案已建立。" : "辅导员档案已更新。");
        return "redirect:" + ((CounselorListContext) model.getAttribute("listContext")).detailUrl(savedId);
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable int id, @RequestParam int version, Principal principal,
                             RedirectAttributes redirect, Model model) {
        try {
            service.deactivate(id, version, principal.getName());
            redirect.addFlashAttribute("success", "档案已停用，原有资料和状态记录保留。");
        } catch (BusinessException exception) { failure(id, "DEACTIVATE_FAILED"); throw exception; }
        return "redirect:" + ((CounselorListContext) model.getAttribute("listContext")).detailUrl(id);
    }

    private String form(Model model, Integer id) {
        model.addAttribute("recordId", id);
        if (id != null) model.addAttribute("existing", service.get(id));
        return "counselors/form";
    }

    private void failure(Integer id, String reason) {
        audit.event(AuditService.actor(), "COUNSELOR_WRITE", "COUNSELOR", id, "FAILURE", reason);
    }

}
