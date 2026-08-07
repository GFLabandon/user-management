package com.example.usermanagement.mapper;

import java.util.List;

import com.example.usermanagement.entity.Department;
import com.example.usermanagement.entity.Role;
import com.example.usermanagement.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Many;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("SELECT id, username, note, dept_id, photo_path FROM users WHERE id = #{id}")
    @Results(id = "userResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "username", column = "username"),
            @Result(property = "note", column = "note"),
            @Result(property = "deptId", column = "dept_id"),
            @Result(property = "photoPath", column = "photo_path"),
            @Result(property = "department", column = "dept_id",
                    one = @One(select = "findDepartmentById")),
            @Result(property = "roles", column = "id",
                    many = @Many(select = "findRolesByUserId"))
    })
    User findUserById(int id);

    @Select("SELECT id, username, note, dept_id, photo_path FROM users ORDER BY id")
    @ResultMap("userResultMap")
    List<User> findAllUsers();

    @Select("""
            SELECT id, username, note, dept_id, photo_path
            FROM users
            WHERE LOWER(username) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
               OR LOWER(note) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            ORDER BY id
            """)
    @ResultMap("userResultMap")
    List<User> searchUsers(String keyword);

    @Insert("""
            INSERT INTO users (username, note, dept_id, photo_path)
            VALUES (#{username}, #{note}, #{deptId}, #{photoPath})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertUser(User user);

    @Update("""
            UPDATE users
            SET username = #{username}, note = #{note}, dept_id = #{deptId}, photo_path = #{photoPath}
            WHERE id = #{id}
            """)
    void updateUser(User user);

    @Delete("DELETE FROM users WHERE id = #{id}")
    void deleteUser(int id);

    @Select("SELECT id, name FROM departments WHERE id = #{id}")
    Department findDepartmentById(int id);

    @Select("SELECT id, name FROM departments ORDER BY name")
    List<Department> findAllDepartments();

    @Select("SELECT id, name FROM roles ORDER BY name")
    List<Role> findAllRoles();

    @Select("""
            SELECT r.id, r.name
            FROM roles r
            INNER JOIN user_roles ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.name
            """)
    List<Role> findRolesByUserId(int userId);

    @Delete("DELETE FROM user_roles WHERE user_id = #{userId}")
    void deleteUserRoles(int userId);

    @Insert("INSERT INTO user_roles (user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") int userId, @Param("roleId") int roleId);
}
