package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.mapper.*;
import io.github.gflabandon.counselor.security.*;
import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountSecurityTests {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.upload-dir", () -> uploads.toString()); }
    @Autowired MockMvc mvc;
    @Autowired AccountMapper mapper;
    @Autowired AccountService accounts;
    @Autowired CounselorService counselors;
    @Autowired DepartmentService departments;
    @Autowired AuditService audit;
    @Autowired PasswordEncoder encoder;
    @Autowired DatabaseUserDetailsService users;
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String name) { return user(users.loadUserByUsername(name)); }
    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(redirectedUrl("/counselors")).andReturn().getRequest().getSession(false);
    }
    private AccountForm form(String name) {
        var f = new AccountForm(); f.setUsername(name); f.setPassword("temporary-pass-123"); return f;
    }
    @Test void hashedPasswordsAndGenericLoginFailures() throws Exception {
        var admin = mapper.findByUsername("admin");
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(encoder.matches("demo-admin-pass", admin.getPasswordHash())).isTrue();
        mvc.perform(post("/login").with(csrf()).param("username", "nonexistent").param("password", "a-secret-password"))
                .andExpect(redirectedUrl("/login?error"));
        assertThat(audit.recent()).anySatisfy(e -> { assertThat(e.getAction()).isEqualTo("LOGIN"); assertThat(e.getOutcome()).isEqualTo("FAILURE"); });
        assertThat(audit.recent()).noneSatisfy(e -> assertThat(e.getReason()).contains("a-secret-password"));
    }
    @Test void csrfRequiredForLoginWritesAndLogout() throws Exception {
        mvc.perform(post("/login").param("username", "admin").param("password", "demo-admin-pass")).andExpect(status().isForbidden());
        var session = login("admin", "demo-admin-pass");
        mvc.perform(post("/departments").session(session).param("name", "伪造院系")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/accounts").session(session)).andExpect(status().isOk());
        assertThat(departments.all()).noneMatch(d -> d.getName().equals("伪造院系"));
        assertThat(audit.recent()).anyMatch(e -> "CSRF".equals(e.getReason()));
    }
    @Test void viewerCanReadButCannotReachManagementOrForgeWrites() throws Exception {
        for (String path : new String[]{"/counselors", "/counselors/1", "/departments"})
            mvc.perform(get(path).with(as("viewer"))).andExpect(status().isOk());
        for (String path : new String[]{"/accounts", "/audit", "/counselors/new", "/counselors/1/edit", "/departments/new"})
            mvc.perform(get(path).with(as("viewer"))).andExpect(status().isForbidden());
        int version = counselors.get(1).getVersion();
        mvc.perform(post("/counselors/1/deactivate").with(as("viewer")).with(csrf()).param("version", String.valueOf(version)))
                .andExpect(status().isForbidden());
        assertThat(counselors.get(1).getVersion()).isEqualTo(version);
        var html = mvc.perform(get("/counselors").with(as("viewer"))).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("href=\"/counselors/new\"", "href=\"/accounts\"");
    }
    @Test void disabledAccountLosesExistingSessionAndCannotLogin() throws Exception {
        var f = form("disabled-test"); int id = accounts.save(null, f, "admin");
        var session = login(f.getUsername(), f.getPassword());
        f.setPassword(""); f.setEnabled(false); accounts.save(id, f, "admin");
        mvc.perform(get("/counselors").session(session)).andExpect(redirectedUrl("/login?expired"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(post("/login").with(csrf()).param("username", f.getUsername()).param("password", "temporary-pass-123"))
                .andExpect(redirectedUrl("/login?error"));
    }
    @Test void roleAndPasswordChangesRevokeSessionsAndOldPassword() throws Exception {
        var f = form("revoked-test"); f.setRole(AccountRole.ADMIN); int id = accounts.save(null, f, "admin");
        var session = login(f.getUsername(), f.getPassword());
        f.setRole(AccountRole.VIEWER); f.setPassword("replacement-pass-123"); accounts.save(id, f, "admin");
        mvc.perform(post("/departments").session(session).with(csrf()).param("name", "stale-role"))
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(post("/login").with(csrf()).param("username", f.getUsername()).param("password", "temporary-pass-123"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/accounts").session(login(f.getUsername(), f.getPassword()))).andExpect(status().isForbidden());
    }
    @Test void accountValidationUniquenessConflictAndAdminProtection() {
        var admin = mapper.findByUsername("admin"); var f = form("admin"); f.setVersion(admin.getVersion()); f.setPassword(""); f.setEnabled(false);
        assertThatThrownBy(() -> accounts.save(admin.getId(), f, "admin")).hasMessageContaining("当前登录");
        assertThatThrownBy(() -> accounts.save(admin.getId(), f, "system")).hasMessageContaining("至少保留");
        assertThatThrownBy(() -> AccountService.validatePassword("short")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AccountService.validatePassword("密".repeat(30))).isInstanceOf(BusinessException.class);
        var newForm = form("unique-test"); int id = accounts.save(null, newForm, "admin");
        assertThatThrownBy(() -> accounts.save(null, newForm, "admin")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        newForm.setPassword(""); accounts.save(id, newForm, "admin");
        assertThatThrownBy(() -> accounts.save(id, newForm, "admin")).isInstanceOf(EditConflictException.class);
    }
    @Test void accountFormsNeverEchoSubmittedPasswordOrHashes() throws Exception {
        for (String path : new String[]{"/accounts", "/accounts/new", "/accounts/1/edit", "/audit"})
            mvc.perform(get(path).with(as("admin"))).andExpect(status().isOk());
        var response = mvc.perform(post("/accounts").with(as("admin")).with(csrf()).param("username", "!").param("password", "secret-to-never-echo"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-to-never-echo", "{bcrypt}");
    }
    @Test void photosRequireLoginAndExistingReferenceAndRejectSymlinks() throws Exception {
        Files.write(uploads.resolve("linked.png"), TestImages.png());
        Files.write(uploads.resolve("orphan.png"), TestImages.png());
        Files.createSymbolicLink(uploads.resolve("symlink.png"), uploads.resolve("linked.png"));
        var f = new CounselorForm(); f.setEmployeeNo("PHOTO-SEC"); f.setName("图片测试"); f.setDepartmentId(departments.all().get(0).getId());
        counselors.create(f, "/uploads/linked.png", "admin");
        mvc.perform(get("/uploads/linked.png")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/uploads/linked.png").with(as("viewer"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        for (String name : new String[]{"orphan.png", "symlink.png", "missing.png"})
            mvc.perform(get("/uploads/" + name).with(as("viewer"))).andExpect(status().isNotFound());
    }
}
