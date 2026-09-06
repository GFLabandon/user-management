package io.github.gflabandon.counselor.web;

import jakarta.validation.constraints.*;
public class DepartmentForm {
    @NotBlank(message = "请填写院系名称")
    @Size(max = 100, message = "院系名称不能超过 100 个字符")
    private String name;


    private boolean active = true;

    @NotNull(message = "缺少版本，请重新打开页面")
    @Min(value = 0, message = "版本无效")
    private Integer version = 0;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean getActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

}
