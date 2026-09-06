package io.github.gflabandon.counselor.mapper;

import java.util.List;
import io.github.gflabandon.counselor.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CounselorMapper {
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM counselors WHERE photo_path = #{path}")
    int photoReferences(String path);

    String FILTER = """
            <where>
              <if test="keyword != null and keyword != ''">
                (LOCATE(LOWER(#{keyword}), LOWER(c.employee_no)) &gt; 0
                 OR LOCATE(LOWER(#{keyword}), LOWER(c.name)) &gt; 0)
              </if>
              <if test="departmentId != null">AND c.department_id = #{departmentId}</if>
              <if test="status != null">AND c.employment_status = #{status}</if>
            </where>
            """;

    @Select("<script>SELECT COUNT(*) FROM counselors c " + FILTER + "</script>")
    long count(@Param("keyword") String keyword, @Param("departmentId") Integer departmentId,
               @Param("status") EmploymentStatus status);

    @Select("<script>SELECT c.*, d.name AS department_name FROM counselors c "
            + "JOIN departments d ON d.id = c.department_id " + FILTER
            + " ORDER BY c.id LIMIT #{size} OFFSET #{offset}</script>")
    List<Counselor> findPage(@Param("keyword") String keyword, @Param("departmentId") Integer departmentId,
                             @Param("status") EmploymentStatus status, @Param("size") int size,
                             @Param("offset") long offset);

    @Select("SELECT c.*, d.name AS department_name FROM counselors c JOIN departments d ON d.id = c.department_id WHERE c.id = #{id}")
    Counselor findById(int id);

    @Insert("""
            INSERT INTO counselors (employee_no, name, department_id, employment_status, remark, photo_path)
            VALUES (#{employeeNo}, #{name}, #{departmentId}, #{employmentStatus}, #{remark}, #{photoPath})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Counselor counselor);

    @Update("""
            UPDATE counselors SET employee_no = #{employeeNo}, name = #{name}, department_id = #{departmentId},
                employment_status = #{employmentStatus}, remark = #{remark}, photo_path = #{photoPath},
                updated_at = CURRENT_TIMESTAMP, version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int update(Counselor counselor);

    @Update("""
            UPDATE counselors SET employment_status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP, version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int deactivate(@Param("id") int id, @Param("version") int version);

    @Insert("""
            INSERT INTO counselor_status_history (counselor_id, from_status, to_status, actor)
            VALUES (#{counselorId}, #{fromStatus}, #{toStatus}, #{actor})
            """)
    void insertHistory(@Param("counselorId") int counselorId, @Param("fromStatus") EmploymentStatus fromStatus,
                       @Param("toStatus") EmploymentStatus toStatus, @Param("actor") String actor);

    @Select("SELECT * FROM counselor_status_history WHERE counselor_id = #{id} ORDER BY id DESC")
    List<StatusHistory> history(int id);
}
