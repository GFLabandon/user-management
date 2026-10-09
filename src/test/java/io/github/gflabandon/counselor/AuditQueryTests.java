package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Path;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import io.github.gflabandon.counselor.entity.AuditEvent;
import io.github.gflabandon.counselor.security.DatabaseUserDetailsService;
import io.github.gflabandon.counselor.service.AuditService;
import io.github.gflabandon.counselor.web.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditQueryTests {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.upload-dir", () -> uploads.toString()); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService audit;
    @Autowired DatabaseUserDetailsService users;

    private long event(String actor, String type, String id, String outcome, String time) {
        jdbc.update("INSERT INTO audit_events(actor,action,target_type,target_id,outcome,reason,occurred_at) VALUES (?,?,?,?,?,?,?)",
                actor, "ACCOUNT_UPDATE", type, id, outcome, "SUCCESS".equals(outcome) ? "OK" : "VALIDATION", LocalDateTime.parse(time));
        return jdbc.queryForObject("SELECT MAX(id) FROM audit_events", Long.class);
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() { return user(users.loadUserByUsername("admin")); }
    @SuppressWarnings("unchecked")
    private PageResult<AuditEvent> page(MvcResult result) { return (PageResult<AuditEvent>) result.getModelAndView().getModel().get("result"); }
    private AuditQuery query(String actor) { var q = new AuditQuery(); q.setActor(actor); return q; }

    private String shortcut(String path, String type, long id) throws Exception {
        var source = mvc.perform(get(path).with(admin())).andExpect(status().isOk()).andReturn();
        var link = Pattern.compile("href=\"([^\"]+)\"[^>]*>查看操作记录</a>")
                .matcher(source.getResponse().getContentAsString());
        while (link.find()) {
            String url = HtmlUtils.htmlUnescape(link.group(1));
            if (url.equals("/audit?targetType=" + type + "&targetId=" + id)) return url;
        }
        throw new AssertionError("Missing audit shortcut for " + type + " " + id + " on " + path);
    }

    @Test void objectShortcutsOpenOnlyTheirOwnEventsEvenWhenTypesShareAnId() throws Exception {
        long counselorId = jdbc.queryForObject("SELECT id FROM counselors WHERE employee_no='DEMO-001'", Long.class);
        long accountId = jdbc.queryForObject("SELECT id FROM system_accounts WHERE username='admin'", Long.class);
        long departmentId = jdbc.queryForObject("SELECT department_id FROM counselors WHERE id=?", Long.class, counselorId);
        long otherCounselorId = jdbc.queryForObject("SELECT id FROM counselors WHERE employee_no='DEMO-003'", Long.class);
        jdbc.update("UPDATE system_accounts SET counselor_id=? WHERE id=?", otherCounselorId, accountId);
        assertThat(accountId).isNotEqualTo(otherCounselorId);
        jdbc.update("DELETE FROM audit_events");
        for (long id : new HashSet<>(List.of(counselorId, accountId, departmentId, otherCounselorId))) {
            for (String type : List.of("COUNSELOR", "ACCOUNT", "DEPARTMENT")) {
                for (String outcome : List.of("SUCCESS", "FAILURE", "DENIED")) {
                    event("shortcut-" + outcome, type, Long.toString(id), outcome, "2026-01-01T12:00:00");
                }
            }
        }
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class);
        var paths = List.of("/counselors/" + counselorId, "/accounts", "/departments");
        var types = List.of("COUNSELOR", "ACCOUNT", "DEPARTMENT");
        var ids = List.of(counselorId, accountId, departmentId);
        for (int i = 0; i < paths.size(); i++) {
            String type = types.get(i), id = ids.get(i).toString();
            var result = mvc.perform(get(URI.create(shortcut(paths.get(i), type, ids.get(i)))).with(admin()))
                    .andExpect(status().isOk()).andReturn();
            assertThat(page(result).total()).isEqualTo(3);
            assertThat(page(result).items()).allSatisfy(e -> {
                assertThat(e.getTargetType()).isEqualTo(type);
                assertThat(e.getTargetId()).isEqualTo(id);
            }).extracting(AuditEvent::getOutcome).containsExactlyInAnyOrder("SUCCESS", "FAILURE", "DENIED");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class)).isEqualTo(before);
    }

    @Test void shortcutWithoutEventsKeepsTheObjectFilterAndShowsEmptyState() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM counselors WHERE employee_no='DEMO-001'", Long.class);
        jdbc.update("DELETE FROM audit_events WHERE target_type='COUNSELOR' AND target_id=?", Long.toString(id));
        var result = mvc.perform(get(URI.create(shortcut("/counselors/" + id, "COUNSELOR", id))).with(admin()))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(result).total()).isZero();
        var query = (AuditQuery) result.getModelAndView().getModel().get("auditQuery");
        assertThat(query.getTargetType()).isEqualTo("COUNSELOR");
        assertThat(query.getTargetId()).isEqualTo(Long.toString(id));
        assertThat(result.getResponse().getContentAsString()).contains("没有匹配的操作记录", "清除筛选");
    }

    @Test void viewersHaveNoShortcutsAndCannotUseAnAdministratorsCopiedLink() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM counselors WHERE employee_no='DEMO-001'", Long.class);
        String url = shortcut("/counselors/" + id, "COUNSELOR", id);
        for (String path : List.of("/counselors/" + id, "/departments")) {
            var result = mvc.perform(get(path).with(user(users.loadUserByUsername("viewer"))))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("查看操作记录", "href=\"/audit");
        }
        mvc.perform(get("/accounts").with(user(users.loadUserByUsername("viewer")))).andExpect(status().isForbidden());
        mvc.perform(get(URI.create(url)).with(user(users.loadUserByUsername("viewer")))).andExpect(status().isForbidden());
        mvc.perform(get(URI.create(url))).andExpect(redirectedUrl("/login"));
    }

    @Test void moreThanOneHundredEventsRemainReachableInStableIdOrderWithoutRewritingHistory() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 105; i++) ids.add(event("page-fixture", "ACCOUNT", "42", "SUCCESS", "2026-01-01T12:00:00"));
        var original = jdbc.queryForList("SELECT * FROM audit_events WHERE actor='page-fixture' ORDER BY id");
        var first = mvc.perform(get("/audit").with(admin()).param("actor", "page-fixture")).andExpect(status().isOk()).andReturn();
        assertThat(page(first).total()).isEqualTo(105);
        assertThat(page(first).totalPages()).isEqualTo(6);
        assertThat(page(first).items()).extracting(AuditEvent::getId).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 20).mapToObj(i -> ids.get(104 - i)).toList());
        var last = mvc.perform(get("/audit").with(admin()).param("actor", "page-fixture").param("page", "6")).andExpect(status().isOk()).andReturn();
        assertThat(page(last).items()).extracting(AuditEvent::getId).containsExactly(ids.get(4), ids.get(3), ids.get(2), ids.get(1), ids.get(0));
        var out = mvc.perform(get("/audit").with(admin()).param("actor", "page-fixture").param("page", "2147483647"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(out).page()).isEqualTo(6);
        assertThat(out.getResponse().getContentAsString()).contains("页码超出范围", "共 105 条");
        assertThat(jdbc.queryForList("SELECT * FROM audit_events WHERE actor='page-fixture' ORDER BY id")).isEqualTo(original);
    }

    @Test void combinedFiltersIncludeBothDateBoundariesAndLinksKeepEveryCondition() throws Exception {
        for (int i = 0; i < 11; i++) event("filter-fixture", "ACCOUNT", "42", "SUCCESS", "2026-02-01T00:00:00");
        long late = event("filter-fixture", "ACCOUNT", "42", "SUCCESS", "2026-02-02T23:59:59.999999");
        event("filter-fixture", "ACCOUNT", "42", "SUCCESS", "2026-01-31T23:59:59");
        event("filter-fixture", "ACCOUNT", "42", "SUCCESS", "2026-02-03T00:00:00");
        event("filter-fixture-extra", "ACCOUNT", "42", "SUCCESS", "2026-02-01T12:00:00");
        event("filter-fixture", "DEPARTMENT", "42", "SUCCESS", "2026-02-01T12:00:00");
        event("filter-fixture", "ACCOUNT", "420", "SUCCESS", "2026-02-01T12:00:00");
        event("filter-fixture", "ACCOUNT", "42", "FAILURE", "2026-02-01T12:00:00");
        var response = mvc.perform(get("/audit").with(admin()).param("actor", " FILTER-FIXTURE ").param("targetType", "ACCOUNT")
                .param("targetId", "42").param("outcome", "SUCCESS").param("startDate", "2026-02-01").param("endDate", "2026-02-02").param("size", "10"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(response).total()).isEqualTo(12);
        assertThat(page(response).items()).hasSize(10);
        assertThat(page(response).items().get(0).getId()).isEqualTo(late);
        assertThat(response.getResponse().getContentAsString()).contains("更新账号", "操作完成", "当前数据库时区：", "日期包含起止当天",
                "actor=FILTER-FIXTURE&amp;targetType=ACCOUNT&amp;targetId=42&amp;outcome=SUCCESS&amp;startDate=2026-02-01&amp;endDate=2026-02-02&amp;size=10&amp;page=2");
        assertThat(response.getModelAndView().getModel().get("timeZone")).isEqualTo(jdbc.queryForObject(
                "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='TIME ZONE'", String.class));
    }

    @Test void eachOptionalFilterAndOpenEndedDatesHaveMatchingCounts() {
        event("optional-fixture", "ACCOUNT", "99", "SUCCESS", "2026-03-01T12:00:00");
        event("optional-fixture", "DEPARTMENT", "99", "FAILURE", "2026-03-02T12:00:00");
        event("optional-fixture", "REQUEST", null, "DENIED", "2026-03-03T12:00:00");
        var q = query("optional-fixture");
        assertThat(audit.search(q).page().total()).isEqualTo(3);
        q.setTargetId("99"); assertThat(audit.search(q).page().total()).isEqualTo(2);
        q.setTargetId(""); q.setTargetType("DEPARTMENT"); assertThat(audit.search(q).page().total()).isEqualTo(1);
        q.setTargetType(""); q.setOutcome("DENIED"); assertThat(audit.search(q).page().total()).isEqualTo(1);
        q.setOutcome(""); q.setStartDate("2026-03-02"); assertThat(audit.search(q).page().total()).isEqualTo(2);
        q.setStartDate(""); q.setEndDate("2026-03-02"); assertThat(audit.search(q).page().total()).isEqualTo(2);
    }

    @Test void emptyResultsHaveRecoveryAndDoNotPretendToHaveAnotherPage() throws Exception {
        var r = mvc.perform(get("/audit").with(admin()).param("actor", "absent-fixture").param("page", "2"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(r).total()).isZero();
        assertThat(page(r).page()).isEqualTo(1);
        assertThat(page(r).hasNext()).isFalse();
        assertThat(r.getResponse().getContentAsString()).contains("没有匹配的操作记录", "清除筛选", "共 0 条");
    }

    @Test void invalidDatesCodesAndPaginationReturn400AndKeepFormWithoutRunningAnUnfilteredSearch() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class);
        for (String[] bad : new String[][]{{"startDate", "2026-02-30"}, {"startDate", "2026-2-01"}, {"endDate", "9999-12-31"},
                {"endDate", "0000-01-01"}, {"page", "0"}, {"page", "-1"}, {"page", "9999999999999"}, {"page", "word"},
                {"size", "0"}, {"size", "101"}, {"size", "11"}, {"size", "word"}, {"targetType", "forged"}, {"outcome", "forged"},
                {"targetId", "1".repeat(65)}}) {
            var r = mvc.perform(get("/audit").with(admin()).param("actor", "keep-fixture").param(bad[0], bad[1]))
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(r.getModelAndView().getModel()).doesNotContainKey("result");
            assertThat(r.getResponse().getContentAsString()).contains("keep-fixture", "筛选条件有误");
        }
        mvc.perform(get("/audit").with(admin()).param("actor", "x".repeat(256))).andExpect(status().isBadRequest());
        var reversed = mvc.perform(get("/audit").with(admin()).param("startDate", "2026-03-02").param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(reversed.getResponse().getContentAsString()).contains("开始日期不能晚于结束日期", "2026-03-02", "2026-03-01");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class)).isEqualTo(before);
    }

    @Test void parametersAreLiteralAndCallerCannotChangeSortOrPageLimit() throws Exception {
        event("literal-fixture", "ACCOUNT", "42", "SUCCESS", "2026-04-01T12:00:00");
        for (String term : new String[]{"%", "_", "' OR 1=1 --"}) {
            long id = event(term, "ACCOUNT", term, "SUCCESS", "2026-04-01T12:00:00");
            var r = mvc.perform(get("/audit").with(admin()).param("actor", term).param("targetId", term)
                    .param("sort", "id ASC; DROP TABLE audit_events").param("size", "10"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(page(r).total()).isEqualTo(1);
            assertThat(page(r).items()).extracting(AuditEvent::getId).containsExactly(id);
        }
        var q = query("literal-fixture"); q.setActor("literal");
        assertThat(audit.search(q).page().total()).isZero();
    }

    @Test void unknownCodesUseSafeLabelsAndUntrustedTextIsEscaped() throws Exception {
        String actor = "<script>alert('actor')</script>";
        long id = event(actor, "<img src=x onerror=alert(1)>", "<b>42</b>", "FAILURE", "2026-05-01T12:00:00");
        jdbc.update("UPDATE audit_events SET action=?, reason=? WHERE id=?", "<script>alert(1)</script>", "sensitive-internal-marker", id);
        var r = mvc.perform(get("/audit").with(admin()).param("actor", actor)).andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("未知操作", "未知对象", "原因未分类", "&lt;script&gt;", "&lt;b&gt;42&lt;/b&gt;")
                .doesNotContain("<script>", "<img src=x", "sensitive-internal-marker");
        var unknown = new AuditEvent(); unknown.setOutcome("NEW_RESULT");
        assertThat(unknown.getOutcomeLabel()).isEqualTo("未知结果");
        assertThat(unknown.getReasonLabel()).isEqualTo("原因未分类");
    }

    @Test void onlyAdministratorsCanReadFilteredOrPagedAuditContent() throws Exception {
        event("admin-only-marker", "ACCOUNT", "42", "SUCCESS", "2026-06-01T12:00:00");
        var viewer = mvc.perform(get("/audit").with(user(users.loadUserByUsername("viewer"))).param("actor", "admin-only-marker").param("page", "1"))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(viewer.getResponse().getContentAsString()).doesNotContain("admin-only-marker");
        mvc.perform(get("/audit").param("actor", "admin-only-marker")).andExpect(redirectedUrl("/login"));
    }
}
