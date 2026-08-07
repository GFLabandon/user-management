package com.example.usermanagement.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class User {
    private int id;

    @NotBlank(message = "Username is required")
    @Size(min = 2, max = 50, message = "Username must contain 2 to 50 characters")
    private String username;

    @NotBlank(message = "Note is required")
    @Size(max = 255, message = "Note must contain at most 255 characters")
    private String note;

    @NotNull(message = "Department is required")
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
