# Rebuild the unchanged gosu 1.19 source with a supported, patched Go toolchain.
FROM golang:1.27.2-bookworm@sha256:5cf287a799e6b94384bad13d16b14904c531f51ba65792237e122ce42b392f61 AS gosu-build
ENV GOTOOLCHAIN=local CGO_ENABLED=0
WORKDIR /src
ADD --checksum=sha256:33d7537d588ea49458b9509bcf4554bdf5ceacc66da71e5caa1058ea3b689c3b https://codeload.github.com/tianon/gosu/tar.gz/6456aaa0f3c854d199d0f037f068eb97515b7513 /tmp/gosu.tar.gz
RUN tar -xzf /tmp/gosu.tar.gz --strip-components=1 \
    && test "$(go env GOVERSION)" = go1.27.2 \
    && go mod download && go mod verify \
    && go build -mod=readonly -trimpath -buildvcs=false -ldflags '-d -w' -o /out/gosu . \
    && go version -m /out/gosu > /out/build-info.txt

# Keep the upstream server, CLI, dump tools, configuration and entrypoint unchanged.
FROM mysql:8.4.11@sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242
LABEL io.counselor.mysql.variant="8.4.11-runtime-2" \
      io.counselor.gosu.source="6456aaa0f3c854d199d0f037f068eb97515b7513" \
      io.counselor.gosu.go="1.27.2"
# Oracle Linux 9 OpenSSL patch for CVE-2026-84782; require the exact verified RPMs.
RUN microdnf install -y openssl-3.5.8-2.0.1.el9_8 openssl-libs-3.5.8-2.0.1.el9_8 \
    && test "$(rpm -q --qf '%{VERSION}-%{RELEASE}' openssl)" = 3.5.8-2.0.1.el9_8 \
    && test "$(rpm -q --qf '%{VERSION}-%{RELEASE}' openssl-libs)" = 3.5.8-2.0.1.el9_8 \
    && microdnf clean all
# mysql-shell owns the affected private Python packages. No installed package requires it.
# rpm resolves the dependency check and removes only this package; do not prune arbitrary files.
RUN rpm -q mysql-shell \
    && rpm -e --test mysql-shell \
    && rpm -e mysql-shell \
    && ! rpm -q mysql-shell \
    && test ! -e /usr/bin/mysqlsh \
    && if [ -d /usr/lib/mysqlsh ]; then \
         test -z "$(find /usr/lib/mysqlsh ! -type d ! -name '*.pyc' -print)" \
         && find /usr/lib/mysqlsh -type f -name '*.pyc' -delete \
         && find /usr/lib/mysqlsh -depth -type d -empty -delete; \
       fi \
    && test ! -d /usr/lib/mysqlsh \
    && mysqld --version && mysql --version && mysqldump --version
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
COPY --from=gosu-build /out/build-info.txt /usr/share/doc/counselor-gosu/build-info.txt
COPY --from=gosu-build /src/LICENSE /usr/share/doc/counselor-gosu/LICENSE
RUN gosu --version \
    && test "$(gosu mysql id -u)" = 999 \
    && test "$(gosu mysql id -g)" = 999 \
    && gosu nobody true
