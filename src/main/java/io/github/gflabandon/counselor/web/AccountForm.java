package io.github.gflabandon.counselor.web;

import jakarta.validation.constraints.*;
import io.github.gflabandon.counselor.entity.AccountRole;
public class AccountForm {
    @NotBlank(message = "请填写登录名")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{2,49}", message = "登录名须为 3–50 位字母、数字、点、下划线或短横线")
    private String username;
    @Size(max = 64, message = "密码不能超过 64 个字符")
    private String password = "";
    @NotNull(message = "请选择权限角色")
    private AccountRole role = AccountRole.VIEWER;
    private boolean enabled = true;
    @Positive(message = "请选择有效档案")
    private Integer counselorId;
    @NotNull(message = "缺少版本，请重新打开页面")
    @Min(value = 0, message = "版本无效")
    private Integer version = 0;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public AccountRole getRole() { return role; }
    public void setRole(AccountRole role) { this.role = role; }

    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Integer getCounselorId() { return counselorId; }
    public void setCounselorId(Integer counselorId) { this.counselorId = counselorId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

}
