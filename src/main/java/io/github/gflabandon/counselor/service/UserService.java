package io.github.gflabandon.counselor.service;

import java.util.List;

import io.github.gflabandon.counselor.entity.Department;
import io.github.gflabandon.counselor.entity.Role;
import io.github.gflabandon.counselor.entity.User;

public interface UserService {
    List<User> findUsers(String keyword);

    User getUserById(int id);

    void saveUser(User user);

    void deleteUser(int id);

    List<Department> findAllDepartments();

    List<Role> findAllRoles();
}
