package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import io.github.gflabandon.counselor.entity.User;
import io.github.gflabandon.counselor.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CounselorManagementWebTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void unauthenticatedDirectoryRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/users/list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void demoCredentialsCreateAnAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "demo-pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/list"))
                .andExpect(request -> assertThat(request.getRequest().getSession(false))
                        .isNotNull());
    }

    @Test
    void directoryRendersRelationalUserData() throws Exception {
        mockMvc.perform(get("/users/list").sessionAttr("user", "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/list"))
                .andExpect(model().attribute("userCount", 3))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alex Chen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Administrator")));
    }

    @Test
    void authenticatedUserCanCreateAProfileWithRoles() throws Exception {
        mockMvc.perform(post("/users/add")
                        .sessionAttr("user", "admin")
                        .param("username", "Taylor Wang")
                        .param("note", "QA engineer")
                        .param("deptId", "1")
                        .param("roleIds", "2", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users/list"));

        User saved = userService.findUsers("Taylor").get(0);
        assertThat(saved.getDepartment().getName()).isEqualTo("Engineering");
        assertThat(saved.getRoles()).extracting("name")
                .containsExactly("Editor", "Viewer");
    }

    @Test
    void invalidProfileReturnsValidationMessages() throws Exception {
        mockMvc.perform(post("/users/add")
                        .sessionAttr("user", "admin")
                        .param("username", "")
                        .param("note", "")
                        .param("deptId", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("users/add"))
                .andExpect(model().attributeHasFieldErrors("user", "username", "note", "deptId"));
    }
}
