#!/bin/sh
set -eu
if [ "$#" -ne 0 ]; then
    printf '%s\n' 'Usage: scripts/run-deploy.sh (configure through environment variables; no command-line overrides)' >&2
    exit 2
fi
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
app_jar=${APP_JAR:-"$project_dir/target/campus-counselor-management-0.1.0-SNAPSHOT.jar"}
if [ ! -f "$app_jar" ]; then
    printf '%s\n' 'Application JAR is missing. Run ./mvnw --batch-mode --no-transfer-progress clean verify first.' >&2
    exit 2
fi
exec "${JAVA_BIN:-java}" -jar "$app_jar" --spring.profiles.active=deploy
