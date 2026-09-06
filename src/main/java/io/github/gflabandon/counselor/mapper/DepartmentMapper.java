package io.github.gflabandon.counselor.mapper;

import java.util.List;
import io.github.gflabandon.counselor.entity.Department;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DepartmentMapper {
    @Select("SELECT * FROM departments ORDER BY id")
    List<Department> findAll();

    @Select("SELECT * FROM departments WHERE id = #{id}")
    Department findById(int id);

    @Select("SELECT * FROM departments WHERE id = #{id} FOR UPDATE")
    Department lockById(int id);

    @Insert("INSERT INTO departments (name, active) VALUES (#{name}, #{active})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Department department);

    @Update("UPDATE departments SET name = #{name}, active = #{active}, version = version + 1 WHERE id = #{id} AND version = #{version}")
    int update(Department department);

    @Select("SELECT (SELECT COUNT(*) FROM counselors WHERE department_id = #{id}) + (SELECT COUNT(*) FROM users WHERE dept_id = #{id})")
    long references(int id);

    @Delete("DELETE FROM departments WHERE id = #{id} AND version = #{version}")
    int delete(@Param("id") int id, @Param("version") int version);
}
