FROM eclipse-temurin:17.0.20_8-jdk-jammy@sha256:ef4374b4b6b9d813dd3f5b593a35ec9a820cfb64a55994798147cc73435a0208 AS build
# Ubuntu USN-8847-1: keep the native TLS libraries at the verified patched version.
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade libssl3=3.0.2-0ubuntu1.30 openssl=3.0.2-0ubuntu1.30 \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 builder \
    && useradd --uid 10001 --gid builder --create-home builder \
    && mkdir /workspace && chown builder:builder /workspace
WORKDIR /workspace
USER builder
COPY --chown=builder:builder .mvn .mvn
COPY --chown=builder:builder mvnw pom.xml ./
COPY --chown=builder:builder src src
COPY --chown=builder:builder docker/HealthProbe.java docker/HealthProbe.java
RUN --mount=type=cache,target=/home/builder/.m2,uid=10001,gid=10001 sh ./mvnw --batch-mode --no-transfer-progress verify \
    && javac -d /workspace/health docker/HealthProbe.java

# Isolated migration harness uses the exact packaged Flyway, driver and V1-V4 classes.
FROM build AS migration-verification
COPY --chown=builder:builder docker/MysqlMigrationVerification.java /workspace/verification/
RUN mkdir /workspace/migration-runtime \
    && cd /workspace/migration-runtime \
    && jar xf /workspace/target/campus-counselor-management-0.1.0-SNAPSHOT.jar \
    && javac -cp 'BOOT-INF/lib/*:BOOT-INF/classes' -d . /workspace/verification/MysqlMigrationVerification.java
WORKDIR /workspace/migration-runtime
ENTRYPOINT ["java", "-cp", ".:BOOT-INF/lib/*:BOOT-INF/classes", "MysqlMigrationVerification"]

FROM eclipse-temurin:17.0.20_8-jre-jammy@sha256:ec72ba5962b45ae4e7f96bfb5ebf6eeb34a488b967f937c8e14f0aaec688954f
# Ubuntu USN-8847-1: keep the native TLS libraries at the verified patched version.
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade libssl3=3.0.2-0ubuntu1.30 openssl=3.0.2-0ubuntu1.30 \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 counselor \
    && useradd --uid 10001 --gid counselor --no-create-home --shell /usr/sbin/nologin counselor \
    && mkdir -p /app/uploads \
    && chown 10001:10001 /app/uploads
WORKDIR /app
COPY --from=build /workspace/target/campus-counselor-management-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --from=build /workspace/health /opt/health
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=8s --start-period=60s --retries=3 \
    CMD ["java", "-cp", "/opt/health", "HealthProbe"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-Djava.awt.headless=true", "-jar", "/app/app.jar", "--spring.profiles.active=deploy"]
