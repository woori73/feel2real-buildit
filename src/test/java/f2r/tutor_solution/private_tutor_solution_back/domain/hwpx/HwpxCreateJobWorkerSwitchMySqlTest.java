package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.config.HwpxProperties;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HWPX 작업 생성 API와 Worker 스위치(hwpx.worker-enabled) MySQL 통합 테스트.
 * 꺼져 있으면 503이고 작업·파일이 생기지 않으며, 켜져 있으면 기존대로 202로 접수된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "hwpx.worker-poll-seconds=3600"
})
@DisplayName("HWPX 작업 생성 — Worker 스위치(503/202)")
class HwpxCreateJobWorkerSwitchMySqlTest {

  private static final Path STORAGE = createStorage();

  private static Path createStorage() {
    try {
      return Files.createTempDirectory("hwpx-switch-it-");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("hwpx.storage-root", STORAGE::toString);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private HwpxProperties props;
  @Autowired private JdbcTemplate jdbc;

  private boolean originalEnabled;

  @BeforeEach
  void remember() throws IOException {
    originalEnabled = props.isWorkerEnabled();
    // 테스트 간 저장 파일이 섞이지 않게 비운다(저장소 루트 자체는 유지).
    try (Stream<Path> s = Files.walk(STORAGE)) {
      s.sorted(java.util.Comparator.reverseOrder()).filter(p -> !p.equals(STORAGE)).forEach(p -> p.toFile().delete());
    }
  }

  @AfterEach
  void restore() {
    props.setWorkerEnabled(originalEnabled);
    jdbc.update("delete from hwpx_jobs where tutor_id in (990101, 990102)");
  }

  private Cookie auth(long userId, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(userId, "hwpx_sw_" + userId, role));
  }

  private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload() {
    MockMultipartFile file = new MockMultipartFile("file", "t.hwpx", "application/octet-stream", "dummy".getBytes());
    MockMultipartFile edits = new MockMultipartFile("edits", "", "application/json",
        "{\"title\":\"스위치 테스트\",\"edits\":[{\"table\":0,\"row\":0,\"col\":0,\"text\":\"x\"}]}".getBytes());
    return multipart("/api/hwpx/jobs").file(file).file(edits).param("title", "스위치 테스트");
  }

  private long jobCount() {
    return jdbc.queryForObject("select count(*) from hwpx_jobs where tutor_id in (990101, 990102)", Long.class);
  }

  private long storedFileCount() throws IOException {
    try (Stream<Path> s = Files.walk(STORAGE)) {
      return s.filter(Files::isRegularFile).count();
    }
  }

  @Test
  @DisplayName("given Worker 비활성 when 학생이 작업을 만들면 then 503과 안내 문구, 작업·파일은 생기지 않는다")
  void disabled_returns_503_and_creates_nothing() throws Exception {
    // given
    props.setWorkerEnabled(false);
    // when / then
    mockMvc.perform(upload().cookie(auth(990101L, "STUDENT")).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("일시적으로 제공되지 않습니다")));
    assertThat(jobCount()).isZero();
    assertThat(storedFileCount()).isZero();
  }

  @Test
  @DisplayName("given Worker 비활성 when 튜터가 작업을 만들면 then 역시 503이다")
  void disabled_returns_503_for_tutor() throws Exception {
    props.setWorkerEnabled(false);
    mockMvc.perform(upload().cookie(auth(990102L, "TUTOR")).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isServiceUnavailable());
    assertThat(jobCount()).isZero();
  }

  @Test
  @DisplayName("given Worker 비활성 when 비로그인이 요청하면 then 기존 인증 정책대로 401이다")
  void disabled_keeps_401_for_anonymous() throws Exception {
    props.setWorkerEnabled(false);
    mockMvc.perform(upload().accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given Worker 활성 when 학생·튜터가 작업을 만들면 then 기존대로 202로 접수되고 파일이 저장된다")
  void enabled_keeps_202_flow() throws Exception {
    // given
    props.setWorkerEnabled(true);
    // when / then
    mockMvc.perform(upload().cookie(auth(990101L, "STUDENT")).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
    mockMvc.perform(upload().cookie(auth(990102L, "TUTOR")).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
    assertThat(jobCount()).isEqualTo(2);
    assertThat(storedFileCount()).isEqualTo(2);
  }
}
