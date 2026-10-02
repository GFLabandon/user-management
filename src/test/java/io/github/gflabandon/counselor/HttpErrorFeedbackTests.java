package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Uses embedded Tomcat, including actual ERROR dispatches and multipart limits. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HttpErrorFeedbackTests.Failures.class)
class HttpErrorFeedbackTests {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("app.upload-dir", () -> uploads.toString());
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:http-errors;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }
    @Value("${local.server.port}") int port;
    private URI url(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private HttpClient browser() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(); }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(url(path)).timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String csrf(HttpClient browser, String path) throws Exception {
        var response = get(browser, path); assertThat(response.statusCode()).isEqualTo(200);
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(response.body());
        assertThat(token.find()).isTrue(); return token.group(1);
    }
    private HttpClient login(String user, String password) throws Exception {
        var client = browser();
        String body = "username=" + user + "&password=" + password + "&_csrf=" + URLEncoder.encode(csrf(client, "/login"), StandardCharsets.UTF_8);
        var response = client.send(HttpRequest.newBuilder(url("/login")).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(302); return client;
    }
    private void page(HttpResponse<String> response, int status, String message) {
        assertThat(response.statusCode()).isEqualTo(status);
        String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertThat(requestId).matches("[0-9a-f-]{36}");
        assertThat(response.body()).contains(message, "返回档案列表", "重新登录", requestId)
                .doesNotContain("private-internal", "secret-password", "SELECT *", "/private/upload-path", "Whitelabel", "Stacktrace");
    }

    @Test void securityFilterDistinguishesCsrfFromRoleAndDoesNotEchoRequestBody() throws Exception {
        var viewer = login("viewer", "demo-viewer-pass");
        page(get(viewer, "/accounts"), 403, "没有操作权限");
        var admin = login("admin", "demo-admin-pass");
        var csrfFailure = admin.send(HttpRequest.newBuilder(url("/accounts")).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=forged&password=secret-password")).build(), HttpResponse.BodyHandlers.ofString());
        page(csrfFailure, 403, "表单验证已失效");
    }

    @Test void servletAndControllerFailuresUseSameSafePages() throws Exception {
        var admin = login("admin", "demo-admin-pass");
        page(get(admin, "/accounts/test/exception"), 500, "请求未能完成");
        page(get(admin, "/accounts/test/database"), 503, "服务暂时不可用");
        page(get(admin, "/accounts/test/filter-failure"), 503, "服务暂时不可用");
        page(get(admin, "/accounts/999999/edit"), 404, "页面或记录不存在");
        page(get(admin, "/accounts/999999/unknown"), 404, "页面或记录不存在");
        page(get(admin, "/error?message=secret-password&status=200"), 404, "页面或记录不存在");
    }

    @Test void expiredCookieRedirectsToActionableLoginNotice() throws Exception {
        var client = browser();
        var response = client.send(HttpRequest.newBuilder(url("/accounts")).header("Cookie", "JSESSIONID=expired-test-session").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/login?expired");
        assertThat(get(client, "/login?expired").body()).contains("登录已过期或账号信息已变更", "密码重置后请使用新密码");
    }

    @Test void realMultipartFileLimitReturns413BeforeControllerAndWithoutEchoingFields() throws Exception {
        multipartLimit(5 * 1024 * 1024 + 1, true);
    }
    @Test void browserMultipartFormWithHiddenCsrfHasActionableOversizeFailure() throws Exception {
        multipartLimit(5 * 1024 * 1024 + 1, false);
    }
    @Test void wholeRequestLimitReturns413() throws Exception {
        multipartLimit(20 * 1024 * 1024 + 1, true);
    }
    private void multipartLimit(int size, boolean headerToken) throws Exception {
        var admin = login("admin", "demo-admin-pass");
        String token = csrf(admin, "/counselors/new");
        String boundary = "ErrorFeedbackBoundary";
        var bytes = new java.io.ByteArrayOutputStream();
        bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + token + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nsecret-password\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"large.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(new byte[size]);
        bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(url("/counselors")).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()));
        if (headerToken) request.header("X-CSRF-TOKEN", token);
        var response = admin.send(request.build(), HttpResponse.BodyHandlers.ofString());
        page(response, 413, "上传内容过大");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Failures {
        @Bean FailureController failureController() { return new FailureController(); }
        @Bean FilterRegistrationBean<Filter> failingFilter() {
            var registration = new FilterRegistrationBean<Filter>((request, response, chain) -> {
                throw new DataAccessResourceFailureException("private-internal SELECT * secret-password");
            });
            registration.addUrlPatterns("/accounts/test/filter-failure");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
            return registration;
        }
    }
    @Controller
    static class FailureController {
        @GetMapping("/accounts/test/exception") @ResponseBody String fail() { throw new IllegalStateException("private-internal /private/upload-path secret-password"); }
        @GetMapping("/accounts/test/database") @ResponseBody String database() { throw new DataAccessResourceFailureException("private-internal SELECT * secret-password"); }
    }
}
