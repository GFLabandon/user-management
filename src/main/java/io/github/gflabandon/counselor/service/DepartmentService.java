package io.github.gflabandon.counselor.service;

import java.util.List;
import io.github.gflabandon.counselor.entity.Department;
import io.github.gflabandon.counselor.mapper.DepartmentMapper;
import io.github.gflabandon.counselor.web.DepartmentForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Service
@Validated
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentMapper mapper;
    public DepartmentService(DepartmentMapper mapper) { this.mapper = mapper; }
    public List<Department> all() { return mapper.findAll(); }
    public Department get(int id) {
        Department department = mapper.findById(id);
        if (department == null) throw new BusinessException("院系不存在。");
        return department;
    }

    @Transactional
    public int save(Integer id, @Valid DepartmentForm form) {
        if (id != null) get(id);
        Department department = new Department();
        department.setName(form.getName().trim());
        department.setActive(form.getActive());
        department.setVersion(form.getVersion());
        if (id == null) mapper.insert(department);
        else {
            department.setId(id);
            if (mapper.update(department) != 1) throw new EditConflictException();
        }
        return department.getId();
    }

    @Transactional
    public void delete(int id, int version) {
        Department department = mapper.lockById(id);
        if (department == null) throw new BusinessException("院系不存在。");
        if (department.getVersion() != version) throw new EditConflictException();
        if (mapper.references(id) != 0) throw new BusinessException("该院系已被档案或旧资料引用，请使用停用功能。");
        if (mapper.delete(id, version) != 1) throw new EditConflictException();
    }
}
