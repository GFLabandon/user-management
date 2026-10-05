package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.gflabandon.counselor.security.DatabaseUserDetailsService;
import io.github.gflabandon.counselor.service.CounselorService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CounselorListContextTests {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("app.upload-dir", () -> uploads.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired DatabaseUserDetailsService users;
    @Autowired CounselorService counselors;
    private RequestPostProcessor admin() { return user(users.loadUserByUsername("admin")); }
    private RequestPostProcessor replaceParam(String name, String value) {
        return request -> { request.setParameter(name, value); return request; };
    }

    private MockHttpServletRequestBuilder context(MockHttpServletRequestBuilder request) {
        return request.param("listKeyword", "DEMO-").param("listStatus", "ACTIVE")
                .param("listPage", "2").param("listSize", "1");
    }
    private String contextQuery() { return "?listKeyword=DEMO-&listStatus=ACTIVE&listPage=2&listSize=1"; }
    private String listLink() { return "href=\"/counselors?keyword=DEMO-&amp;status=ACTIVE&amp;page=2&amp;size=1\""; }
    private MockHttpServletRequestBuilder validEdit() {
        var c = counselors.get(1);
        return post("/counselors/1").with(admin()).with(csrf()).param("version", "" + c.getVersion())
                .param("employeeNo", c.getEmployeeNo()).param("name", c.getName())
                .param("departmentId", "" + c.getDepartmentId());
    }

    @Test void listLinksCarryClampedPageAndAllFilters() throws Exception {
        int department = counselors.get(1).getDepartmentId();
        String html = mvc.perform(get("/counselors").with(admin()).param("keyword", " DEMO- ")
                        .param("departmentId", "" + department).param("status", "ACTIVE")
                        .param("page", "999").param("size", "10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String query = "?listKeyword=DEMO-&amp;listDepartmentId=" + department
                + "&amp;listStatus=ACTIVE&amp;listPage=1&amp;listSize=10";
        assertThat(html).contains("href=\"/counselors/1" + query + "\"",
                "href=\"/counselors/1/edit" + query + "\"", "href=\"/counselors/new" + query + "\"")
                .doesNotContain("listPage=999");
    }

    @Test void detailEditAndValidationFailureKeepReturnPosition() throws Exception {
        for (String path : new String[]{"/counselors/1", "/counselors/1/edit", "/counselors/new"}) {
            String html = mvc.perform(context(get(path)).with(admin())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).contains(listLink(), "name=\"listKeyword\" value=\"DEMO-\"",
                    "name=\"listPage\" value=\"2\"");
        }
        String html = mvc.perform(context(validEdit()).with(replaceParam("name", "")))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains(listLink(), "name=\"listPage\" value=\"2\"");
        assertThat(counselors.get(1).getName()).isEqualTo("示例张老师");
    }

    @Test void saveAndConflictReloadPreserveContextWithoutOverwritingWinner() throws Exception {
        int version = counselors.get(1).getVersion();
        mvc.perform(context(validEdit()).param("remark", "已保存备注"))
                .andExpect(redirectedUrl("/counselors/1" + contextQuery()));
        String html = mvc.perform(context(validEdit()).with(replaceParam("version", "" + version)).param("remark", "过期备注"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("过期备注", listLink(),
                "href=\"/counselors/1/edit" + contextQuery().replace("&", "&amp;") + "\"");
        assertThat(counselors.get(1).getRemark()).isEqualTo("已保存备注");
    }

    @Test void createAndDeactivateKeepContextAndReturnedPageReclamps() throws Exception {
        mvc.perform(context(post("/counselors")).with(admin()).with(csrf()).param("employeeNo", "CONTEXT-NEW")
                        .param("name", "新档案").param("departmentId", "" + counselors.get(1).getDepartmentId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith(contextQuery())));
        mvc.perform(context(post("/counselors/1/deactivate")).with(admin()).with(csrf()).param("version", "0"))
                .andExpect(redirectedUrl("/counselors/1" + contextQuery()));
        String html = mvc.perform(get("/counselors").with(admin()).param("keyword", "DEMO-")
                        .param("status", "ACTIVE").param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("第 1 / 1 页", "listPage=1").doesNotContain("listPage=2");
    }

    @Test void contextEncodesReservedCharactersAndCannotChooseRedirectDestination() throws Exception {
        String keyword = "中文 + & # % //example.invalid";
        String encoded = "%E4%B8%AD%E6%96%87%20%2B%20%26%20%23%20%25%20%2F%2Fexample.invalid";
        String html = mvc.perform(get("/counselors/1").with(admin()).param("listKeyword", keyword)
                        .param("listPage", "1").param("returnUrl", "https://example.invalid"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("href=\"/counselors?keyword=" + encoded + "&amp;page=1&amp;size=10\"")
                .doesNotContain("href=\"https://example.invalid");
        mvc.perform(validEdit().param("listKeyword", keyword).param("listPage", "1")
                        .param("returnUrl", "https://example.invalid"))
                .andExpect(redirectedUrl("/counselors/1?listKeyword=" + encoded + "&listPage=1&listSize=10"));
        mvc.perform(get(java.net.URI.create("/counselors?keyword=" + encoded + "&page=1&size=10")).with(admin()))
                .andExpect(status().isOk()).andExpect(model().attribute("keyword", keyword));
    }

    @Test void directLinksAndSeparateRequestsDoNotInheritPreviousContext() throws Exception {
        mvc.perform(context(get("/counselors/1")).with(admin())).andExpect(status().isOk());
        String direct = mvc.perform(get("/counselors/1").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(direct).contains("href=\"/counselors\"").doesNotContain("name=\"listKeyword\"", "listPage=");
        mvc.perform(validEdit().param("returnUrl", "//example.invalid"))
                .andExpect(redirectedUrl("/counselors/1"));
        String other = mvc.perform(get("/counselors/1").with(admin()).param("listKeyword", "另一标签")
                        .param("listPage", "3").param("listSize", "20"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(other).contains("name=\"listPage\" value=\"3\"").doesNotContain("DEMO-&amp;");
    }

    @Test void invalidContextNeverWritesAndViewerStillCannotEdit() throws Exception {
        int version = counselors.get(1).getVersion();
        mvc.perform(validEdit().param("listPage", "not-a-number"))
                .andExpect(status().isBadRequest());
        mvc.perform(validEdit().param("listKeyword", "x".repeat(101)))
                .andExpect(status().isBadRequest());
        mvc.perform(context(get("/counselors/1/edit")).with(user(users.loadUserByUsername("viewer"))))
                .andExpect(status().isForbidden());
        assertThat(counselors.get(1).getVersion()).isEqualTo(version);
    }
}
