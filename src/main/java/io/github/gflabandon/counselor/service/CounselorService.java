package io.github.gflabandon.counselor.service;

import java.util.List;
import java.util.Locale;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.mapper.CounselorMapper;
import io.github.gflabandon.counselor.mapper.DepartmentMapper;
import io.github.gflabandon.counselor.web.CounselorForm;
import io.github.gflabandon.counselor.web.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Service
@Validated
@Transactional(readOnly = true)
public class CounselorService {
    private final CounselorMapper mapper;
    private final DepartmentMapper departments;
    private final AuditService audit;
    private final ImageLifecycle images;

    public CounselorService(CounselorMapper mapper, DepartmentMapper departments, AuditService audit, ImageLifecycle images) {
        this.mapper = mapper;
        this.departments = departments; this.audit = audit; this.images = images;
    }

    public PageResult<Counselor> search(String keyword, Integer departmentId, EmploymentStatus status, int page, int size) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.length() > 100) throw new BusinessException("检索内容不能超过 100 个字符。");
        int safeSize = Math.max(1, Math.min(size, 100));
        long total = mapper.count(query, departmentId, status);
        int pages = (int) Math.max(1, (total + safeSize - 1) / safeSize);
        int safePage = Math.max(1, Math.min(page, pages));
        return new PageResult<>(mapper.findPage(query, departmentId, status, safeSize,
                (long) (safePage - 1) * safeSize), total, safePage, safeSize, pages);
    }

    public Counselor get(int id) {
        Counselor counselor = mapper.findById(id);
        if (counselor == null) throw new RecordNotFoundException("未找到该辅导员档案。");
        return counselor;
    }

    public List<StatusHistory> history(int id) { return mapper.history(id); }

    @Transactional
    public int create(@Valid CounselorForm form, String photoPath, String actor) {
        images.change(photoPath);
        requireDepartment(form.getDepartmentId(), null);
        Counselor counselor = fromForm(form);
        counselor.setPhotoPath(photoPath);
        mapper.insert(counselor);
        mapper.insertHistory(counselor.getId(), null, counselor.getEmploymentStatus(), actor);
        audit.success(actor, "COUNSELOR_CREATE", "COUNSELOR", counselor.getId());
        return counselor.getId();
    }

    /** Returns the replaced image path; ImageLifecycle handles cleanup after transaction completion. */
    @Transactional
    public String update(int id, @Valid CounselorForm form, String newPhotoPath, String actor) {
        var imageChange = images.change(newPhotoPath);
        Counselor existing = get(id);
        if (existing.getVersion() != form.getVersion()) throw new EditConflictException();
        requireDepartment(form.getDepartmentId(), existing.getDepartmentId());
        Counselor counselor = fromForm(form);
        counselor.setId(id);
        counselor.setPhotoPath(newPhotoPath == null ? existing.getPhotoPath() : newPhotoPath);
        if (mapper.update(counselor) != 1) throw new EditConflictException();
        if (existing.getEmploymentStatus() != counselor.getEmploymentStatus()) {
            mapper.insertHistory(id, existing.getEmploymentStatus(), counselor.getEmploymentStatus(), actor);
        }
        audit.success(actor, "COUNSELOR_UPDATE", "COUNSELOR", id);
        imageChange.replaced(existing.getPhotoPath());
        return newPhotoPath == null ? null : existing.getPhotoPath();
    }

    @Transactional
    public void deactivate(int id, int version, String actor) {
        Counselor existing = get(id);
        if (existing.getVersion() != version) throw new EditConflictException();
        if (existing.getEmploymentStatus() == EmploymentStatus.INACTIVE) throw new BusinessException("该档案已经停用。");
        if (mapper.deactivate(id, version) != 1) throw new EditConflictException();
        mapper.insertHistory(id, existing.getEmploymentStatus(), EmploymentStatus.INACTIVE, actor);
        audit.success(actor, "COUNSELOR_DEACTIVATE", "COUNSELOR", id);
    }

    private void requireDepartment(int id, Integer previousId) {
        // Serialize assignment with department deactivation/deletion; still allow editing an existing association.
        Department department = departments.lockById(id);
        if (department == null) throw new BusinessException("院系不存在，请重新选择。");
        if (!department.getActive() && !Integer.valueOf(id).equals(previousId)) {
            throw new BusinessException("该院系已停用，不能新增档案关联。");
        }
    }

    private Counselor fromForm(CounselorForm form) {
        Counselor counselor = new Counselor();
        counselor.setEmployeeNo(form.getEmployeeNo().trim().toUpperCase(Locale.ROOT));
        counselor.setName(form.getName().trim());
        counselor.setDepartmentId(form.getDepartmentId());
        counselor.setEmploymentStatus(form.getEmploymentStatus());
        counselor.setRemark(form.getRemark() == null ? "" : form.getRemark().trim());
        counselor.setVersion(form.getVersion());
        return counselor;
    }
}
