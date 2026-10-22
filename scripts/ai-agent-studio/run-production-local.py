#!/usr/bin/env python3
"""Resume only the isolated database/storage produced by StudioProductionImportMySqlTest."""
import json,os,pathlib,re
root=pathlib.Path(__file__).resolve().parents[2]
f=json.loads(pathlib.Path('/tmp/f2r-assets-browser-fixture.json').read_text())
assert re.fullmatch(r'jdbc:mysql://127\.0\.0\.1:3318/f2r_assets_[a-f0-9]{12}\?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci',f['jdbcUrl'])
assert re.fullmatch(r'/tmp/f2r_assets_[a-f0-9]{12}-files',f['storagePath'])
java=pathlib.Path(os.environ.get('JAVA_HOME','/workspace/tools/jdk'))/'bin/java'
args=[str(java),'-Xmx1200m','-jar',str(root/'build/libs/private_tutor_solution_back-0.0.1-SNAPSHOT.jar'),
 '--server.address=127.0.0.1','--server.port=18090','--spring.profiles.active=local',
 '--spring.datasource.url='+f['jdbcUrl'],'--spring.datasource.username=f2r_test','--spring.datasource.password=isolated_test_password',
 '--spring.flyway.locations=classpath:db/migration','--spring.jpa.show-sql=false','--app.file.upload-dir='+f['storagePath'],
 '--app.mcp.enabled=true','--app.mcp.public-base-url=http://127.0.0.1:18090','--app.mcp.internal-base-url=http://127.0.0.1:18090',
 '--app.mcp.redirect-uris=https://claude.ai/api/mcp/auth_callback,https://chatgpt.com/connector/oauth/isolated-test-callback',
 '--spring.web.resources.static-locations=file:'+str(root/'src/main/resources/static')+'/,classpath:/static/',
 '--spring.thymeleaf.prefix=file:'+str(root/'src/main/resources/templates')+'/', '--spring.thymeleaf.cache=false']
os.chdir(root);os.execv(str(java),args)
