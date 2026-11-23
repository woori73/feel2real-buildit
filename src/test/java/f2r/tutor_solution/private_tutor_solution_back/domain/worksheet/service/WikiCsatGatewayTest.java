package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiIdentitySigner;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WikiCsatGateway — 내신위키 수능 문항 읽기(로그인한 사람의 신원 서명)")
class WikiCsatGatewayTest {

  static final AuthenticatedPrincipal OWNER = new AuthenticatedPrincipal(1L, "owner", "ADMIN");
  HttpServer server;
  final AtomicReference<String> signedUser = new AtomicReference<>();
  WikiCsatGateway gateway;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/csat/exams", ex -> {
      signedUser.set(ex.getRequestHeaders().getFirst("X-BuildIt-Login-Id"));
      String path = ex.getRequestURI().getPath();
      String body = path.endsWith("/questions")
          ? "[{\"question_no\":18,\"csat_type_name\":\"글의 목적\",\"passage\":\"P\",\"prompt\":\"Q\",\"choices\":[\"a\",\"b\"],\"answer\":\"3\",\"solution\":\"S\",\"group_label\":\"\"}]"
          : path.equals("/api/csat/exams/9/broken") ? "x" : "[{\"id\":5,\"exam_year\":2026,\"exam_type\":\"CSAT\",\"label\":\"\",\"question_count\":28}]";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(path.contains("/missing") ? 404 : 200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    gateway = new WikiCsatGateway(new WikiIdentitySigner("12345678901234567890123456789012"), new ObjectMapper(),
        "http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  @DisplayName("given 위키 시험·문항 when exams·questions then 로그인한 사람으로 서명해 부르고 필요한 칸만 읽는다")
  void readsExamsAndQuestions() {
    // given & when
    List<WikiCsatGateway.Exam> exams = gateway.exams(OWNER);
    List<WikiCsatGateway.Item> items = gateway.questions(OWNER, 5L);

    // then
    assertThat(signedUser.get()).isEqualTo("owner");
    assertThat(exams).containsExactly(new WikiCsatGateway.Exam(5L, 2026, "CSAT", "", 28));
    assertThat(items).containsExactly(new WikiCsatGateway.Item(18, "글의 목적", "P", "Q", List.of("a", "b"), "3", "S", ""));
  }

  @Test
  @DisplayName("given 로그인 정보 없음·404·위키 꺼짐 when 부르면 then 빈 목록(오류를 던지지 않는다)")
  void failuresAreEmpty() {
    // given
    WikiCsatGateway down = new WikiCsatGateway(new WikiIdentitySigner("12345678901234567890123456789012"), new ObjectMapper(),
        "http://127.0.0.1:1");

    // when & then
    assertThat(gateway.exams(null)).isEmpty();
    assertThat(gateway.get(OWNER, "/api/csat/exams/missing")).isEmpty();
    assertThat(down.exams(OWNER)).isEmpty();
  }
}
