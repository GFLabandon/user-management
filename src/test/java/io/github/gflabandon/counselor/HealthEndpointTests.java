package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import io.github.gflabandon.counselor.mapper.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTests {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @MockitoSpyBean AccountMapper accounts;

    @Test void anonymousProbesOnlyReturnStatusWithoutSessionOrDetails() throws Exception {
        for (String group : new String[]{"liveness", "readiness"}) {
            var result = mvc.perform(get("/actuator/health/" + group))
                    .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"))
                    .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
            assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"status\":\"UP\"}");
        }
    }

    @Test void readinessTracksAvailabilityWithoutChangingLiveness() throws Exception {
        try {
            AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
            mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
            mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        } finally { AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC); }
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test void probesIgnoreExistingBusinessSessionAndDoNotQueryAccounts() throws Exception {
        var session = (org.springframework.mock.web.MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", "admin").param("password", "demo-admin-pass"))
                .andExpect(redirectedUrl("/counselors")).andReturn().getRequest().getSession(false);
        clearInvocations(accounts);
        mvc.perform(get("/actuator/health/liveness").session(session)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness").session(session)).andExpect(status().isOk());
        verifyNoInteractions(accounts);
        mvc.perform(get("/counselors").session(session)).andExpect(status().isOk());
        verify(accounts).findById(anyInt());
    }

    @Test void otherManagementPathsAndProbeWritesRemainForbiddenEvenForAdmin() throws Exception {
        for (String path : new String[]{"/actuator", "/actuator/health", "/actuator/health/readiness/db", "/actuator/env", "/actuator/beans", "/actuator/shutdown"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/actuator/health/liveness").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/accounts")).andExpect(redirectedUrl("/login"));
    }
}
