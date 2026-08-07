package com.example.usermanagement.service.impl;

import java.util.List;

import com.example.usermanagement.entity.Department;
import com.example.usermanagement.entity.Role;
import com.example.usermanagement.entity.User;
import com.example.usermanagement.mapper.UserMapper;
import com.example.usermanagement.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<User> findUsers(String keyword) {
        return StringUtils.hasText(keyword)
                ? userMapper.searchUsers(keyword.trim())
                : userMapper.findAllUsers();
    }

    @Override
    public User getUserById(int id) {
        User user = userMapper.findUserById(id);
        if (user != null) {
            user.setRoleIds(user.getRoles().stream().map(Role::getId).toList());
        }
        return user;
    }

    @Override
    @Transactional
    public void saveUser(User user) {
        if (user.getId() == 0) {
            userMapper.insertUser(user);
        } else {
            userMapper.updateUser(user);
            userMapper.deleteUserRoles(user.getId());
        }

        user.getRoleIds().stream()
                .distinct()
                .forEach(roleId -> userMapper.insertUserRole(user.getId(), roleId));
    }

    @Override
    @Transactional
    public void deleteUser(int id) {
        userMapper.deleteUser(id);
    }

    @Override
    public List<Department> findAllDepartments() {
        return userMapper.findAllDepartments();
    }

    @Override
    public List<Role> findAllRoles() {
        return userMapper.findAllRoles();
    }
}
