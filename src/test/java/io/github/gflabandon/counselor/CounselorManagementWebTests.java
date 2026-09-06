package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

import java.nio.file.Files;
import java.nio.file.Path;
import io.github.gflabandon.counselor.entity.*;
import io.github.gflabandon.counselor.service.*;
import io.github.gflabandon.counselor.web.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CounselorManagementWebTests {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploads.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired CounselorService service;
    @Autowired DepartmentService departments;

    private int departmentId() { return departments.all().get(0).getId(); }
    private CounselorForm input(String number, String name) {
        CounselorForm form = new CounselorForm(); form.setEmployeeNo(number); form.setName(name);
        form.setDepartmentId(departmentId()); return form;
    }

    @Test void allNewRoutesAndLegacyRoutesRequireLogin() throws Exception {
        for (String route : new String[]{"/counselors", "/counselors/new", "/counselors/1/edit", "/departments", "/departments/new", "/users/list"}) {
            mvc.perform(get(route)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        }
        mvc.perform(post("/counselors")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/departments/1/delete")).andExpect(redirectedUrl("/login"));
    }

    @Test void loginLogoutAndLegacyDirectoryRedirect() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").param("username", "admin").param("password", "demo-pass"))
                .andExpect(redirectedUrl("/counselors")).andReturn().getRequest().getSession(false);
        assertThat(session.getAttribute("user")).isEqualTo("admin");
        mvc.perform(get("/users/list").session(session)).andExpect(redirectedUrl("/counselors"));
        mvc.perform(post("/users/add").session(session)).andExpect(status().isNotFound());
        mvc.perform(post("/logout").session(session)).andExpect(redirectedUrl("/login"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void directoryAndFormsRenderBusinessFields() throws Exception {
        mvc.perform(get("/counselors").sessionAttr("user", "admin"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("DEMO-001")))
                .andExpect(content().string(containsString("任职状态")));
        mvc.perform(get("/counselors/new").sessionAttr("user", "admin"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("姓名允许重复")));
        mvc.perform(get("/counselors/1/edit").sessionAttr("user", "admin"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("DEMO-001")));
        mvc.perform(get("/departments").sessionAttr("user", "admin")).andExpect(status().isOk());
        mvc.perform(get("/departments/new").sessionAttr("user", "admin")).andExpect(status().isOk());
    }

    @Test void sameNamesAllowedEmployeeNumbersNormalizedAndUnique() {
        int first = service.create(input("test-001", "同名老师"), null, "admin");
        service.create(input("TEST-002", "同名老师"), null, "admin");
        assertThat(service.get(first).getEmployeeNo()).isEqualTo("TEST-001");
        assertThat(service.search("同名老师", null, null, 1, 10).total()).isEqualTo(2);
        assertThatThrownBy(() -> service.create(input("TEST-001", "另一位老师"), null, "admin"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void filtersPagingClampsAndLiteralKeywordSearch() {
        for (int i = 0; i < 12; i++) service.create(input("PAGE-" + i, "分页老师"), null, "admin");
        PageResult<Counselor> first = service.search("PAGE-", departmentId(), EmploymentStatus.ACTIVE, 1, 10);
        PageResult<Counselor> last = service.search("PAGE-", departmentId(), EmploymentStatus.ACTIVE, 999, 10);
        assertThat(first.total()).isEqualTo(12); assertThat(first.items()).hasSize(10);
        assertThat(last.page()).isEqualTo(2); assertThat(last.items()).hasSize(2);
        assertThat(first.items()).extracting(Counselor::getId).doesNotContainAnyElementsOf(last.items().stream().map(Counselor::getId).toList());
        assertThat(service.search("PAGE-", null, EmploymentStatus.INACTIVE, 1, 10).total()).isZero();
        assertThat(service.search("%", null, null, 1, 10).total()).isZero();
        assertThat(service.search("' OR 1=1 --", null, null, 1, 10).total()).isZero();
        assertThat(service.search("", null, null, -1, 1000).size()).isEqualTo(100);
        assertThat(service.search("missing", null, null, -1, 0).page()).isEqualTo(1);
    }

    @Test void invalidFieldsReturnErrorsAndUnboundEntityFieldsCannotBeInjected() throws Exception {
        mvc.perform(post("/counselors").sessionAttr("user", "admin").param("employeeNo", " ").param("name", " ").param("departmentId", ""))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("counselorForm", "employeeNo", "name", "departmentId"));
        mvc.perform(post("/counselors").sessionAttr("user", "admin").param("employeeNo", "SAFE-1").param("name", "正常姓名")
                        .param("departmentId", String.valueOf(departmentId())).param("id", "1").param("photoPath", "/uploads/injected.png"))
                .andExpect(status().is3xxRedirection());
        Counselor saved = service.search("SAFE-1", null, null, 1, 10).items().get(0);
        assertThat(saved.getId()).isNotEqualTo(1); assertThat(saved.getPhotoPath()).isNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test void malformedStatusIsRejectedWithoutWriting() throws Exception {
        mvc.perform(post("/counselors").sessionAttr("user", "admin").param("employeeNo", "BAD-1").param("name", "老师")
                        .param("departmentId", String.valueOf(departmentId())).param("employmentStatus", "UNKNOWN"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("counselorForm", "employmentStatus"));
        assertThat(service.search("BAD-1", null, null, 1, 10).total()).isZero();
    }

    @Test void deactivateRetainsRecordAndRecordsHistoryAndCanRestore() {
        int id = service.create(input("STATUS-1", "状态老师"), null, "admin");
        service.deactivate(id, 0, "operator");
        assertThat(service.get(id).getEmploymentStatus()).isEqualTo(EmploymentStatus.INACTIVE);
        assertThat(service.history(id)).hasSize(2);
        assertThat(service.history(id).get(0).getActor()).isEqualTo("operator");
        CounselorForm restore = input("STATUS-1", "状态老师"); restore.setVersion(1);
        service.update(id, restore, null, "admin");
        assertThat(service.history(id)).hasSize(3);
        assertThat(service.get(id).getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test void staleEditAndDeactivateDoNotOverwriteOrAppendHistory() {
        int id = service.create(input("LOCK-1", "原始姓名"), null, "admin");
        service.update(id, input("LOCK-1", "先提交者"), null, "admin");
        assertThatThrownBy(() -> service.update(id, input("LOCK-1", "后提交者"), null, "admin")).isInstanceOf(EditConflictException.class);
        assertThatThrownBy(() -> service.deactivate(id, 0, "admin")).isInstanceOf(EditConflictException.class);
        assertThat(service.get(id).getName()).isEqualTo("先提交者");
        assertThat(service.get(id).getVersion()).isEqualTo(1);
        assertThat(service.history(id)).hasSize(1);
    }

    @Test void departmentCannotBeDeletedWhenReferencedAndInactiveCannotReceiveNewRecords() {
        DepartmentForm form = new DepartmentForm(); form.setName("测试院系");
        int department = departments.save(null, form);
        CounselorForm input = input("DEPT-1", "院系老师"); input.setDepartmentId(department);
        int id = service.create(input, null, "admin");
        assertThatThrownBy(() -> departments.delete(department, 0)).hasMessageContaining("引用");
        form.setActive(false); departments.save(department, form);
        CounselorForm newInput = input("DEPT-2", "新老师"); newInput.setDepartmentId(department);
        assertThatThrownBy(() -> service.create(newInput, null, "admin")).hasMessageContaining("已停用");
        input.setName("保留原关联"); service.update(id, input, null, "admin");
        assertThat(service.get(id).getDepartmentId()).isEqualTo(department);
        newInput.setDepartmentId(Integer.MAX_VALUE);
        assertThatThrownBy(() -> service.create(newInput, null, "admin")).hasMessageContaining("不存在");
    }

    @Test void unreferencedDepartmentCanBeEditedAndDeletedWithVersionCheck() {
        DepartmentForm form = new DepartmentForm(); form.setName("临时院系");
        int id = departments.save(null, form); form.setName("修改名称"); departments.save(id, form);
        assertThatThrownBy(() -> departments.save(id, form)).isInstanceOf(EditConflictException.class);
        assertThatThrownBy(() -> departments.delete(id, 0)).isInstanceOf(EditConflictException.class);
        departments.delete(id, 1); assertThatThrownBy(() -> departments.get(id)).hasMessageContaining("不存在");
    }

    @Test void failedEditDeletesOnlyNewUploadAndRetainsWinningImage() throws Exception {
        Path oldImage = uploads.resolve("existing.png"); Files.write(oldImage, new byte[]{1});
        int id = service.create(input("IMAGE-1", "原始姓名"), "/uploads/existing.png", "admin");
        service.update(id, input("IMAGE-1", "已更新姓名"), null, "admin");
        mvc.perform(multipart("/counselors/" + id).file(new MockMultipartFile("photo", "new.png", "image/png", new byte[]{1,2,3}))
                        .sessionAttr("user", "admin").param("employeeNo", "IMAGE-1").param("name", "过期修改")
                        .param("departmentId", String.valueOf(departmentId())).param("version", "0"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("重新打开最新档案")))
                .andExpect(model().attribute("conflict", true));
        assertThat(service.get(id).getPhotoPath()).isEqualTo("/uploads/existing.png");
        try (var files = Files.list(uploads)) { assertThat(files.toList()).containsExactly(oldImage); }
        Files.delete(oldImage);
    }

    @Test void detailShowsStatusHistoryAndDeactivatePostWorks() throws Exception {
        int id = service.create(input("WEB-1", "详情老师"), null, "admin");
        mvc.perform(post("/counselors/" + id + "/deactivate").sessionAttr("user", "admin").param("version", "0"))
                .andExpect(redirectedUrl("/counselors/" + id));
        mvc.perform(get("/counselors/" + id).sessionAttr("user", "admin"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("状态记录")))
                .andExpect(content().string(containsString("在职 → 停用")));
    }
    @Test void directoryUsesTwoSqlStatementsRegardlessOfPageSize() {
        for (int i = 0; i < 12; i++) service.create(input("SQL-" + i, "查询老师"), null, "admin");
        SqlCounter.reset();
        service.search("SQL-", null, null, 1, 1);
        assertThat(SqlCounter.count()).isEqualTo(2);
        SqlCounter.reset();
        service.search("查询老师", null, null, 1, 20);
        assertThat(SqlCounter.count()).isEqualTo(2);
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class CounterConfiguration {
        @org.springframework.context.annotation.Bean SqlCounter sqlCounter() { return new SqlCounter(); }
    }

    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(
            type = org.apache.ibatis.executor.statement.StatementHandler.class, method = "prepare",
            args = {java.sql.Connection.class, Integer.class}))
    static class SqlCounter implements org.apache.ibatis.plugin.Interceptor {
        private static final ThreadLocal<Integer> COUNT = ThreadLocal.withInitial(() -> 0);
        static void reset() { COUNT.set(0); }
        static int count() { return COUNT.get(); }
        @Override public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            COUNT.set(COUNT.get() + 1);
            return invocation.proceed();
        }
    }

    @Test void editRequiresAnExplicitVersion() throws Exception {
        mvc.perform(post("/counselors/1").sessionAttr("user", "admin").param("employeeNo", "DEMO-001")
                        .param("name", "缺少版本").param("departmentId", String.valueOf(departmentId())))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/departments/1").sessionAttr("user", "admin").param("name", "缺少版本"))
                .andExpect(status().isBadRequest());
    }

}
