package io.github.gflabandon.counselor.service.impl;

import java.util.List;

import io.github.gflabandon.counselor.entity.Department;
import io.github.gflabandon.counselor.entity.Role;
import io.github.gflabandon.counselor.entity.User;
import io.github.gflabandon.counselor.mapper.UserMapper;
import io.github.gflabandon.counselor.service.UserService;
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
