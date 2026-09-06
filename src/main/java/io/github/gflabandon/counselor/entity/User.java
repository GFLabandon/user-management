package io.github.gflabandon.counselor.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class User {
    private int id;

    @NotBlank(message = "请填写姓名")
    @Size(min = 2, max = 50, message = "姓名长度须为 2 至 50 个字符")
    private String username;

    @NotBlank(message = "请填写备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String note;

    @NotNull(message = "请选择部门")
    private Integer deptId;

    private String photoPath;
    private Department department;
    private List<Role> roles = new ArrayList<>();
    private List<Integer> roleIds = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getDeptId() {
        return deptId;
    }

    public void setDeptId(Integer deptId) {
        this.deptId = deptId;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles == null ? new ArrayList<>() : roles;
    }

    public List<Integer> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Integer> roleIds) {
        this.roleIds = roleIds == null ? new ArrayList<>() : roleIds;
    }
}
