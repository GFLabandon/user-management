package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.gflabandon.counselor.security.DatabaseUserDetailsService;
import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorFeedbackTests {
    @TempDir static Path temporary;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.upload-dir", () -> temporary.resolve("uploads").toString()); }
    @Autowired MockMvc mvc;
    @Autowired DatabaseUserDetailsService users;
    @Autowired CounselorService counselors;
    @Autowired DepartmentService departments;
    @Autowired AccountService accounts;
    private RequestPostProcessor admin() { return user(users.loadUserByUsername("admin")); }

    @Test void invalidParametersAndMissingRecordsHaveSafePagesAndCorrectStatus() throws Exception {
        for (String path : new String[]{"/counselors/not-a-number", "/counselors?page=private-invalid-page", "/counselors?status=private-invalid-status"}) {
            String html = mvc.perform(get(path).with(admin())).andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).contains("请求内容有误", "返回档案列表", "请求编号").doesNotContain("private-invalid", "java.lang", "Exception");
        }
        for (String path : new String[]{"/counselors/999999", "/accounts/999999/edit", "/departments/999999/edit", "/css/missing.css"})
            mvc.perform(get(path).with(admin())).andExpect(status().isNotFound())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("页面或记录不存在")));
    }

    @Test void uploadValidationAndStorageFailureKeepTextAndNeverExposePaths() throws Exception {
        int department = departments.all().get(0).getId();
        var invalid = mvc.perform(multipart("/counselors").file(new MockMultipartFile("photo", "notes.txt", "text/plain", new byte[]{1}))
                .with(admin()).with(csrf()).param("employeeNo", "ERR-UPLOAD").param("name", "保留姓名")
                .param("remark", "保留备注").param("departmentId", "" + department))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(invalid.getContentAsString()).contains("仅支持 JPG/PNG", "保留姓名", "保留备注", "请重新选择图片", invalid.getHeader("X-Request-ID"));
        Path blocked = temporary.resolve("uploads");
        Files.writeString(blocked, "blocks directory creation");
        try {
            var failed = mvc.perform(multipart("/counselors").file(new MockMultipartFile("photo", "ok.png", "image/png", TestImages.png()))
                    .with(admin()).with(csrf()).param("employeeNo", "ERR-UPLOAD").param("name", "保留姓名")
                    .param("departmentId", "" + department))
                    .andExpect(status().isInternalServerError()).andReturn().getResponse();
            assertThat(failed.getContentAsString()).contains("图片保存失败", "保留姓名", failed.getHeader("X-Request-ID"))
                    .doesNotContain(temporary.toString(), "FileAlreadyExistsException");
        } finally { Files.delete(blocked); }
        assertThat(counselors.search("ERR-UPLOAD", null, null, 1, 10).total()).isZero();
    }

    @Test void accountConflictRetainsInputAndPasswordResetOrDisableCannotBypassVersion() throws Exception {
        var f = new AccountForm(); f.setUsername("error-feedback"); f.setPassword("initial-test-pass-123");
        int id = accounts.save(null, f, "admin");
        f.setPassword("new-test-password-123"); accounts.save(id, f, "admin");
        var response = mvc.perform(post("/accounts/" + id).with(admin()).with(csrf()).param("version", "0")
                .param("username", f.getUsername()).param("enabled", "false").param("password", "never-echo-private-password"))
                .andExpect(status().isConflict()).andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("重新打开最新账号", "error-feedback", response.getHeader("X-Request-ID"))
                .doesNotContain("never-echo-private-password", "{bcrypt}");
        assertThat(accounts.get(id).getEnabled()).isTrue();
        assertThat(accounts.get(id).getVersion()).isEqualTo(1);
    }

    @Test void destructiveActionFailuresKeepStatusAndNeverWrite() throws Exception {
        var c = counselors.get(1);
        mvc.perform(post("/counselors/1/deactivate").with(admin()).with(csrf()).param("version", "999"))
                .andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.containsString("资料已更新")));
        assertThat(counselors.get(1).getVersion()).isEqualTo(c.getVersion());
        mvc.perform(post("/departments/" + c.getDepartmentId() + "/delete").with(admin()).with(csrf()).param("version", "0"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("请使用停用功能")));
        assertThat(departments.get(c.getDepartmentId())).isNotNull();
    }
}
