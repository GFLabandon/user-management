package io.github.gflabandon.counselor.config;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.*;
import org.springframework.mock.env.MockEnvironment;

class DeploymentEnvironmentValidatorTests {
    @TempDir Path uploads;
    private MockEnvironment valid() {
        var env = new MockEnvironment(); env.setActiveProfiles("deploy");
        return env.withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/counselor?sslMode=REQUIRED")
                .withProperty("spring.datasource.username", "counselor_app")
                .withProperty("spring.datasource.password", "private-db-secret")
                .withProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("spring.flyway.locations", "classpath:db/migration")
                .withProperty("app.upload-dir", uploads.toString());
    }
    private void validate(MockEnvironment env) { new DeploymentEnvironmentValidator().postProcessEnvironment(env, new SpringApplication()); }
    @Test void validConfigurationChecksAndCleansProbeWithoutChangingFiles() throws Exception {
        Files.writeString(uploads.resolve("existing.png"), "untouched");
        validate(valid());
        assertThat(Files.readString(uploads.resolve("existing.png"))).isEqualTo("untouched");
        try (var files = Files.list(uploads)) { assertThat(files.map(p -> p.getFileName().toString())).containsExactly("existing.png"); }
    }
    @Test void defaultDemoAndLocalMysqlAreUnchanged() {
        var env = new MockEnvironment(); env.setDefaultProfiles("demo"); validate(env);
        env.setActiveProfiles("mysql"); validate(env);
    }
    @ParameterizedTest @ValueSource(strings={"demo,deploy", "deploy,demo", "deploy,unknown"})
    void rejectsMixedProfiles(String profiles) { var env = valid(); env.setActiveProfiles(profiles.split(",")); assertThatThrownBy(() -> validate(env)).hasMessageContaining("DEPLOY_CONFIG"); }
    @Test void deploymentAsDefaultStillValidates() {
        var env = valid(); env.setActiveProfiles(); env.setDefaultProfiles("deploy"); env.setProperty("app.demo", "true");
        assertThatThrownBy(() -> validate(env)).hasMessageContaining("DEPLOY_CONFIG");
    }
    @ParameterizedTest @ValueSource(strings={"spring.datasource.url", "spring.datasource.username", "spring.datasource.password", "app.upload-dir"})
    void rejectsMissingRequiredConfiguration(String key) { var env=valid(); env.setProperty(key, " "); assertThatThrownBy(() -> validate(env)).hasMessageContaining("required"); }
    @ParameterizedTest @ValueSource(strings={"jdbc:h2:mem:test", "jdbc:mysql://root:secret@localhost/test", "jdbc:mysql://localhost/", "jdbc:mysql://localhost/test?user=root", "jdbc:mysql://localhost/test?%70assword=secret", "jdbc:mysql://localhost/test?socketFactory=example.Factory", "jdbc:mysql://localhost/test?sslMode=REQUIRED&sslMode=DISABLED"})
    void rejectsInvalidOrCredentialBearingUrlsWithoutEcho(String url) {
        var env=valid(); env.setProperty("spring.datasource.url", url);
        assertThatThrownBy(() -> validate(env)).hasMessageContaining("DB_URL").hasMessageNotContaining(url).hasNoCause();
    }
    @ParameterizedTest @ValueSource(strings={"root", "ROOT", " root "})
    void rejectsRoot(String username) {var env=valid(); env.setProperty("spring.datasource.username", username); assertThatThrownBy(() -> validate(env)).hasMessageContaining("dedicated account");}
    @ParameterizedTest @ValueSource(strings={"spring.datasource.hikari.username", "spring.datasource.hikari.jdbc-url", "spring.flyway.url", "spring.datasource.hikari.data-source-properties.user"})
    void rejectsAlternateDatasourceOverrides(String key) {var env=valid(); env.setProperty(key,"secret-alternate"); assertThatThrownBy(() -> validate(env)).hasMessageContaining("DEPLOY_CONFIG").hasMessageNotContaining("secret-alternate");}
    @Test void rejectsDisabledMigrationDemoSeedsAndH2Driver() {
        Map<String,String> bad=Map.of("spring.flyway.enabled","false", "spring.flyway.clean-disabled","false", "spring.flyway.baseline-on-migrate","true", "spring.flyway.target","2", "spring.flyway.locations","classpath:db/demo", "spring.sql.init.mode","always", "spring.datasource.driver-class-name","org.h2.Driver", "app.demo","true");
        bad.forEach((key,value) -> {var env=valid();env.setProperty(key,value);assertThatThrownBy(() -> validate(env)).hasMessageContaining("DEPLOY_CONFIG");});
        var malformed = valid().withProperty("spring.datasource.hikari.data-source-properties", "private-db-secret");
        assertThatThrownBy(() -> validate(malformed)).hasMessageContaining("DEPLOY_CONFIG").hasMessageNotContaining("private-db-secret").hasNoCause();
        var indexed = valid();
        indexed.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("override", Map.of("spring.flyway.locations[0]", "classpath:db/demo")));
        assertThatThrownBy(() -> validate(indexed)).hasMessageContaining("DEPLOY_CONFIG");
    }
    @Test void rejectsFileRelativeMissingAndReadOnlyUploadDirectories() throws Exception {
        Path file = Files.writeString(uploads.resolve("file"), "not a directory");
        for (String dir : List.of("./uploads", uploads.resolve("missing").toString(), file.toString())) {
            var env=valid();env.setProperty("app.upload-dir",dir);assertThatThrownBy(() -> validate(env)).hasMessageContaining("UPLOAD_DIR").hasMessageNotContaining(dir);
        }
        Path readOnly=Files.createDirectory(uploads.resolve("readonly"));
        var old=Files.getPosixFilePermissions(readOnly);
        try {Files.setPosixFilePermissions(readOnly, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
            var env=valid();env.setProperty("app.upload-dir",readOnly.toString());assertThatThrownBy(() -> validate(env)).hasMessageContaining("UPLOAD_DIR");
        } finally {Files.setPosixFilePermissions(readOnly,old);}
    }
    @Test void actualConfigDataLoadsAndValidatorRunsBeforeBeanCreation() {
        // A marker initializer stops the valid startup before any database is contacted.
        var app = new SpringApplication(EmptyConfiguration.class); app.setWebApplicationType(WebApplicationType.NONE); app.setLogStartupInfo(false);
        app.addInitializers(ctx -> {throw new EndOfConfigPhase();});
        Map<String,Object> properties = Map.of("DB_URL", "jdbc:mysql://127.0.0.1:1/counselor", "DB_USERNAME", "counselor_app", "DB_PASSWORD", "not-logged", "UPLOAD_DIR", uploads.toString(), "spring.main.banner-mode", "off");
        app.setDefaultProperties(properties);
        assertThatThrownBy(() -> app.run("--spring.profiles.active=deploy")).isInstanceOf(EndOfConfigPhase.class);
        assertThatThrownBy(() -> app.run("--spring.profiles.active=deploy,demo")).hasMessageContaining("DEPLOY_CONFIG");
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    static class EmptyConfiguration {}
    static class EndOfConfigPhase extends RuntimeException {}
}
