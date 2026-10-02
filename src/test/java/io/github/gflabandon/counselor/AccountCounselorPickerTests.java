package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.gflabandon.counselor.security.DatabaseUserDetailsService;
import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountCounselorPickerTests {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.upload-dir", () -> uploads.toString()); }
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired CounselorService counselors;
    @Autowired DepartmentService departments;
    @Autowired DatabaseUserDetailsService users;
    private RequestPostProcessor admin() { return user(users.loadUserByUsername("admin")); }
    private int counselor(String employee, String name) {
        var f = new CounselorForm(); f.setEmployeeNo(employee); f.setName(name);
        f.setDepartmentId(departments.all().get(0).getId()); f.setRemark("private-picker-remark");
        return counselors.create(f, null, "admin");
    }
    private AccountForm account(String username, Integer counselor) {
        var f = new AccountForm(); f.setUsername(username); f.setPassword("picker-test-pass-123");
        f.setCounselorId(counselor); return f;
    }
    @Test void searchRequiresAdministratorAndNeverWrites() throws Exception {
        mvc.perform(get("/accounts/counselor-options").param("keyword", "测试")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/accounts/counselor-options").with(user(users.loadUserByUsername("viewer"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/accounts/counselor-options").with(admin())).andExpect(content().json("[]"));
        mvc.perform(get("/accounts/counselor-options").with(admin()).param("keyword", "x".repeat(51)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/accounts/counselor-options").with(admin()).param("accountId", "999999"))
                .andExpect(status().isNotFound());
    }
    @Test void boundedSearchDistinguishesSameNamesAndReturnsOnlyPickerFields() throws Exception {
        for (int i = 0; i < 21; i++) counselor("PICK-" + String.format("%02d", i), "同名测试");
        mvc.perform(get("/accounts/counselor-options").with(admin()).param("keyword", "同名测试"))
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].length()").value(4))
                .andExpect(jsonPath("$[0].employeeNo").value("PICK-00"))
                .andExpect(jsonPath("$[1].employeeNo").value("PICK-01"))
                .andExpect(jsonPath("$[0].departmentName").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-picker-remark"))));
        mvc.perform(get("/accounts/counselor-options").with(admin()).param("keyword", "  pick-20  "))
                .andExpect(jsonPath("$.length()").value(1));
        for (String term : new String[]{"missing-name", "%", "_", "' OR 1=1 --"})
            mvc.perform(get("/accounts/counselor-options").with(admin()).param("keyword", term)).andExpect(content().json("[]"));
    }
    @Test void searchExcludesOccupiedRecordsExceptCurrentAccountAndEditRendersSelection() throws Exception {
        int c = counselor("PICK-LINK", "关联测试");
        int a = accounts.save(null, account("picker-linked", c), "admin");
        mvc.perform(get("/accounts/counselor-options").with(admin()).param("keyword", "PICK-LINK"))
                .andExpect(content().json("[]"));
        mvc.perform(get("/accounts/counselor-options").with(admin()).param("keyword", "PICK-LINK").param("accountId", "" + a))
                .andExpect(jsonPath("$[0].id").value(c));
        String edit = mvc.perform(get("/accounts/" + a + "/edit").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(edit).contains("已选择：关联测试 · PICK-LINK", "account-counselor-picker.js").doesNotContain("{bcrypt}");
        String list = mvc.perform(get("/accounts").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("关联测试 · PICK-LINK", "/counselors/" + c).doesNotContain("{bcrypt}");
    }
    @Test void validationFailurePreservesSelectionAndNonSensitiveFieldsButNeverPassword() throws Exception {
        int c = counselor("PICK-FAIL", "失败保留");
        String html = mvc.perform(post("/accounts").with(admin()).with(csrf()).param("username", "!")
                .param("password", "never-echo-this-password").param("counselorId", "" + c))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("已选择：失败保留 · PICK-FAIL", "value=\"!\"").doesNotContain("never-echo-this-password");
    }
    @Test void forgedDuplicateAndStaleSelectionsDoNotOverwriteAssociation() throws Exception {
        int c = counselor("PICK-CONFLICT", "冲突测试");
        int a = accounts.save(null, account("picker-owner", c), "admin");
        for (String id : new String[]{"999999", "" + c}) {
            String html = mvc.perform(post("/accounts").with(admin()).with(csrf()).param("username", "picker-forged")
                    .param("password", "never-echo-this-password").param("counselorId", id))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains(id.equals("999999") ? "档案不存在" : "已关联其他账号").doesNotContain("never-echo-this-password");
        }
        assertThat(accounts.all()).noneMatch(row -> row.username().equals("picker-forged"));
        mvc.perform(post("/accounts/" + a).with(admin()).with(csrf()).param("username", "picker-owner")
                .param("version", "0").param("counselorId", "")).andExpect(status().is3xxRedirection());
        String html = mvc.perform(post("/accounts/" + a).with(admin()).with(csrf()).param("username", "picker-owner")
                .param("version", "0").param("counselorId", "" + c))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("资料已被其他操作更新");
        assertThat(accounts.get(a).getCounselorId()).isNull();
        assertThat(accounts.get(a).getVersion()).isEqualTo(1);
    }
    @Test void viewerAndMissingCsrfCannotModifyAssociations() throws Exception {
        int c = counselor("PICK-DENY", "拒绝修改");
        int a = accounts.save(null, account("picker-denied", null), "admin");
        mvc.perform(post("/accounts/" + a).with(user(users.loadUserByUsername("viewer"))).with(csrf())
                .param("username", "picker-denied").param("version", "0").param("counselorId", "" + c))
                .andExpect(status().isForbidden());
        mvc.perform(post("/accounts/" + a).with(admin()).param("username", "picker-denied")
                .param("version", "0").param("counselorId", "" + c)).andExpect(status().isForbidden());
        assertThat(accounts.get(a).getCounselorId()).isNull();
    }
}
