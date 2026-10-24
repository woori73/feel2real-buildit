package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** 영상·음악 Range 재생: 실제 HTTP·격리 MySQL·로컬 저장소. 원본 bytes·권한 경계를 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(
    properties = {
      "server.port=18088",
      "app.mcp.enabled=true",
      "app.studio.access.operators-only=false",
      "app.mcp.public-base-url=http://127.0.0.1:18088",
      "app.mcp.internal-base-url=http://127.0.0.1:18088",
      "app.mcp.redirect-uris=https://claude.ai/api/mcp/auth_callback,https://chatgpt.com/connector/oauth/isolated-test-callback",
      "spring.datasource.url=jdbc:mysql://127.0.0.1:3318/f2r_studio_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci",
      "spring.datasource.username=f2r_test",
      "spring.datasource.password=isolated_test_password",
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.show-sql=false",
      "app.file.upload-dir=/tmp/f2r-studio-test-files",
      "logging.level.org.hibernate.SQL=WARN"
    })
@EnabledIfEnvironmentVariable(named = "F2R_STUDIO_MYSQL_TEST", matches = "true")
class StudioMediaStreamMySqlTest {
  @Autowired StudioService studio;
  @Autowired UserRepository users;
  @Autowired AdminSessionValidator stamps;
  @Autowired JwtTokenProvider jwt;
  @Autowired JdbcTemplate db;
  final HttpClient http = HttpClient.newHttpClient();
  final String base = "http://127.0.0.1:18088";

  JwtTokenProvider.AuthenticatedPrincipal user(String login, Role role) {
    var u =
        users
            .findByLoginId(login)
            .orElseGet(
                () ->
                    users.save(
                        User.builder()
                            .loginId(login)
                            .password("{synthetic-test-only}")
                            .name("격리 재생 테스트")
                            .nickname(login)
                            .role(role)
                            .build()));
    return new JwtTokenProvider.AuthenticatedPrincipal(
        u.getUserId(), u.getLoginId(), u.getRole().name(), stamps.stampFor(u.getUserId()));
  }

  String cookie(JwtTokenProvider.AuthenticatedPrincipal p) {
    return "ACCESS_TOKEN=" + jwt.issue(p.userId(), p.loginId(), p.role(), p.credentialStamp());
  }

  HttpResponse<byte[]> get(String path, String cookie, String range) throws Exception {
    var b = HttpRequest.newBuilder(URI.create(base + path)).GET();
    if (cookie != null) b.header("Cookie", cookie);
    if (range != null) b.header("Range", range);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  static byte[] wav(int dataBytes) {
    byte[] b = new byte[44 + dataBytes];
    System.arraycopy("RIFF".getBytes(StandardCharsets.ISO_8859_1), 0, b, 0, 4);
    System.arraycopy("WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1), 0, b, 8, 8);
    for (int i = 44; i < b.length; i++) b[i] = (byte) (i * 31);
    return b;
  }

  @Test
  void givenStoredAudio_whenRangeRequested_thenExactBytesWithoutChangingOriginal()
      throws Exception {
    var admin = user("studio-stream-admin", Role.ADMIN);
    var outsider = user("studio-stream-outsider", Role.STUDENT);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM studio_projects WHERE id=?", Long.class, StudioService.PROJECT)
        == 0) studio.initialize(admin);
    byte[] audio = wav(200_000 + new Random().nextInt(1000));
    var uploaded =
        studio.upload(admin, "scene-sfx-" + UUID.randomUUID() + ".wav", new ByteArrayInputStream(audio));
    String id = (String) ((Map<?, ?>) uploaded.get("file")).get("id");
    String path = "/api/studio/files/" + id + "/stream";

    var partial = get(path, cookie(admin), "bytes=100-1099");
    assertEquals(206, partial.statusCode());
    assertEquals("audio/wav", partial.headers().firstValue("Content-Type").orElse(""));
    assertEquals(
        "bytes 100-1099/" + audio.length, partial.headers().firstValue("Content-Range").orElse(""));
    assertEquals("bytes", partial.headers().firstValue("Accept-Ranges").orElse(""));
    assertArrayEquals(Arrays.copyOfRange(audio, 100, 1100), partial.body());

    var tail = get(path, cookie(admin), "bytes=-10");
    assertArrayEquals(Arrays.copyOfRange(audio, audio.length - 10, audio.length), tail.body());

    var whole = get(path, cookie(admin), null);
    assertEquals(200, whole.statusCode());
    assertArrayEquals(audio, whole.body());

    assertEquals(416, get(path, cookie(admin), "bytes=" + audio.length + "-").statusCode());
    // 권한 없는 회원·비로그인은 재생 불가
    assertTrue(Set.of(401, 403).contains(get(path, null, "bytes=0-9").statusCode()));
    assertEquals(403, get(path, cookie(outsider), "bytes=0-9").statusCode());
    // 문서·이미지는 스트림 대상이 아니다
    var md =
        studio.upload(
            admin,
            "note-" + UUID.randomUUID() + ".md",
            new ByteArrayInputStream("# note".getBytes(StandardCharsets.UTF_8)));
    String mdId = (String) ((Map<?, ?>) md.get("file")).get("id");
    assertEquals(415, get("/api/studio/files/" + mdId + "/stream", cookie(admin), null).statusCode());
    // 원본 불변
    assertArrayEquals(audio, studio.bytes(admin, id));
  }
}
