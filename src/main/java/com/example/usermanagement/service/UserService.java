package com.example.usermanagement.service;

import java.util.List;

import com.example.usermanagement.entity.Department;
import com.example.usermanagement.entity.Role;
import com.example.usermanagement.entity.User;

public interface UserService {
    List<User> findUsers(String keyword);

    User getUserById(int id);

    void saveUser(User user);

    void deleteUser(int id);

    List<Department> findAllDepartments();

    List<Role> findAllRoles();
}
