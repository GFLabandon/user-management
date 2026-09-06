package io.github.gflabandon.counselor.web;

import jakarta.validation.constraints.*;
import io.github.gflabandon.counselor.entity.EmploymentStatus;
public class CounselorForm {
    @NotBlank(message = "请填写工号")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,31}", message = "工号须为 2 至 32 位字母、数字、下划线或短横线，以字母或数字开头")
    private String employeeNo;

    @NotBlank(message = "请填写姓名")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String name;

    @NotNull(message = "请选择院系")
    @Positive(message = "请选择有效院系")
    private Integer departmentId;

    @NotNull(message = "请选择任职状态")
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    @NotNull(message = "缺少版本，请重新打开页面")
    @Min(value = 0, message = "版本无效")
    private Integer version = 0;

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(EmploymentStatus employmentStatus) { this.employmentStatus = employmentStatus; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

}
