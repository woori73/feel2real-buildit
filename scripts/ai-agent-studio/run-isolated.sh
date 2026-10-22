#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
# Local synthetic test DB only. Never load production .env files or profiles.
jar_path=build/libs/private_tutor_solution_back-0.0.1-SNAPSHOT.jar
if [[ ! -f "$jar_path" ]]; then printf '%s\n' 'First build the isolated tests and bootJar; see docs/ai-agent-studio/REPORT.md.' >&2; exit 1; fi
exec java -Xmx1024m -jar "$jar_path" \
 --server.port=18088 --spring.profiles.active=local \
 '--spring.datasource.url=jdbc:mysql://127.0.0.1:3318/f2r_studio_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci' \
 --spring.datasource.username=f2r_test --spring.datasource.password=isolated_test_password \
 --spring.flyway.locations=classpath:db/migration --spring.jpa.show-sql=false \
 --app.file.upload-dir=/tmp/f2r-studio-test-files \
 --app.mcp.redirect-uris=https://claude.ai/api/mcp/auth_callback,https://chatgpt.com/connector/oauth/isolated-test-callback \
 --app.mcp.enabled=true --app.mcp.public-base-url=http://127.0.0.1:18088 --app.mcp.internal-base-url=http://127.0.0.1:18088
