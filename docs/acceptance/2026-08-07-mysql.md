# MySQL Acceptance Report

Date: 2026-08-07

Historical acceptance of the user-management implementation before the Campus Counselor Management naming refactor. MySQL was not re-tested during the 2026-09-06 foundation refactor; database schemas, SQL, connection settings and routes remain unchanged.

## Environment

- MySQL Community Server 9.0.1 for macOS arm64
- MySQL Connector/J through the `mysql` Spring profile
- Isolated temporary database and application account
- Application port `18080`; MySQL bound only to local port `13306`

The temporary account, database, server process, data directory, cookies, response files, and uploaded acceptance image were removed after verification. No acceptance credential or database file is stored in this repository.

## Verified Flow

| Check | Result |
| --- | --- |
| Connector/J connection | Passed; HikariCP opened `com.mysql.cj.jdbc.ConnectionImpl` |
| Schema initialization | Passed; users, departments, roles, user_roles created |
| Seed data | Passed; three user rows loaded |
| Unauthenticated `/users/list` | `302` to login |
| Demo login | `302` to user directory with Session cookie |
| Add user | Passed with department, two roles, and JPEG profile image |
| Detail page | `200`; newly inserted MySQL row rendered |
| Fuzzy search | `200`; matched the note field |
| Edit user | Passed; username, note, and department persisted |
| Transactional role replacement | Passed; two roles replaced by Administrator |
| Delete user | Passed; user and user_roles rows removed |
| Image cleanup | Passed; stored profile image removed after deletion |

## Evidence Boundary

This is a repeatable local integration acceptance against a real MySQL process. It is not evidence of production deployment, performance, concurrency, failover, backup, or long-running reliability.
