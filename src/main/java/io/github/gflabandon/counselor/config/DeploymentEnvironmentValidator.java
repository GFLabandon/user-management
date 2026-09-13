package io.github.gflabandon.counselor.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Validate resolved configuration before a DataSource, Flyway or web server exists. */
public class DeploymentEnvironmentValidator implements EnvironmentPostProcessor, Ordered {
    private static final Set<String> URL_OPTIONS = Set.of("sslmode", "usessl", "allowpublickeyretrieval",
            "servertimezone", "connectiontimezone", "connecttimeout", "sockettimeout", "useunicode", "characterencoding");

    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Set<String> profiles = new HashSet<>(Arrays.asList(env.getActiveProfiles()));
        if (profiles.isEmpty()) profiles.addAll(Arrays.asList(env.getDefaultProfiles()));
        if (!profiles.contains("deploy")) return;
        if (!Set.of("deploy", "mysql").containsAll(profiles) || flag(env, "app.demo", false)) {
            fail("deploy cannot be combined with demo or unrelated profiles.");
        }
        String url = required(env, "spring.datasource.url", "DB_URL");
        validateUrl(url);
        String username = required(env, "spring.datasource.username", "DB_USERNAME");
        if (username.trim().equalsIgnoreCase("root")) fail("DB_USERNAME must be a dedicated account, not root.");
        required(env, "spring.datasource.password", "DB_PASSWORD");
        if (!"com.mysql.cj.jdbc.Driver".equals(value(env, "spring.datasource.driver-class-name"))) {
            fail("deploy requires the MySQL JDBC driver.");
        }
        // Reject alternate connection paths that could bypass the checked URL and account.
        for (String key : List.of("spring.datasource.jndi-name", "spring.datasource.type",
                "spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.username", "spring.datasource.hikari.password",
                "spring.datasource.hikari.data-source-class-name", "spring.datasource.hikari.data-source-j-n-d-i",
                "spring.flyway.url", "spring.flyway.user", "spring.flyway.password")) {
            if (!value(env, key).isEmpty()) fail("Use the primary datasource settings; alternate datasource or Flyway credentials are not supported.");
        }
        rejectHikariProperties(env);
        if (!"never".equals(value(env, "spring.sql.init.mode"))
                || !flag(env, "spring.flyway.enabled", true)
                || !flag(env, "spring.flyway.clean-disabled", true)
                || flag(env, "spring.flyway.baseline-on-migrate", false)
                || !standardMigrationLocations(env)
                || !value(env, "spring.flyway.target").isEmpty()) {
            fail("deploy requires the standard complete Flyway migrations, no SQL seed data, and disabled clean/baseline.");
        }
        verifyUploadDirectory(required(env, "app.upload-dir", "UPLOAD_DIR"));
    }

    private static boolean standardMigrationLocations(ConfigurableEnvironment env) {
        try {
            return Binder.get(env).bind("spring.flyway.locations", Bindable.listOf(String.class))
                    .orElse(List.of()).equals(List.of("classpath:db/migration"));
        } catch (RuntimeException ignored) { return false; }
    }

    private static void rejectHikariProperties(ConfigurableEnvironment env) {
        boolean present;
        try {
            present = !value(env, "spring.datasource.hikari.data-source-properties").isEmpty()
                    || Binder.get(env).bind("spring.datasource.hikari.data-source-properties", Bindable.mapOf(String.class, String.class)).isBound();
        } catch (RuntimeException ignored) {
            fail("Invalid alternate Hikari datasource properties; use DB_URL connection options.");
            return;
        }
        if (present) fail("Use DB_URL connection options instead of alternate Hikari datasource properties.");
    }

    private static void validateUrl(String url) {
        try {
            if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            URI uri = new URI(url.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_-]+")
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1) throw new IllegalArgumentException();
            String query = uri.getRawQuery();
            if (query != null) {
                Set<String> seen = new HashSet<>();
                for (String pair : query.split("&", -1)) {
                    String[] option = pair.split("=", 2);
                    String key = URLDecoder.decode(option[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (option.length != 2 || !URL_OPTIONS.contains(key) || !seen.add(key)) throw new IllegalArgumentException();
                }
            }
        } catch (Exception ignored) {
            // Deliberately omit URI and nested parser errors: they may contain credentials.
            fail("DB_URL must use jdbc:mysql://host[:port]/database with documented connection options and no embedded credentials.");
        }
    }

    private static void verifyUploadDirectory(String configured) {
        Path probe = null;
        try {
            Path directory = Path.of(configured);
            if (!directory.isAbsolute() || !Files.isDirectory(directory) || !Files.isWritable(directory)) {
                fail("UPLOAD_DIR must be an existing writable absolute directory.");
            }
            // isWritable alone is insufficient on ACLs/mounts: prove create, write and delete access.
            probe = Files.createTempFile(directory, ".counselor-startup-", ".tmp");
            Files.writeString(probe, "startup-check", StandardOpenOption.TRUNCATE_EXISTING);
            Files.delete(probe);
        } catch (Exception ignored) {
            if (probe != null) try { Files.deleteIfExists(probe); } catch (Exception cleanupIgnored) { /* Report below. */ }
            fail("UPLOAD_DIR must support creating, writing and deleting files; check permissions and any .counselor-startup- files.");
        }
    }

    private static String required(ConfigurableEnvironment env, String key, String label) {
        String result = value(env, key);
        if (result.isBlank()) fail(label + " is required in deploy mode.");
        return result;
    }
    private static String value(ConfigurableEnvironment env, String key) {
        try { return env.getProperty(key, ""); }
        catch (RuntimeException ignored) { fail("A required deployment setting could not be resolved; check environment variable names."); return ""; }
    }
    private static boolean flag(ConfigurableEnvironment env, String key, boolean fallback) {
        String value = value(env, key);
        if (value.isEmpty()) return fallback;
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) fail("Deployment boolean settings must be true or false.");
        return Boolean.parseBoolean(value);
    }
    private static void fail(String message) { throw new IllegalStateException("[DEPLOY_CONFIG] " + message); }
}
