package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 스위치 OFF(buildit.assistant.enabled=false) — 화면만 숨기는 게 아니라 서버가 대화 API·AI 작업을 막는지 검증한다.
 * 실제 보안 필터 체인과 MySQL을 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "buildit.assistant.enabled=false"
})
@DisplayName("기능 스위치 OFF — 대화 API 서버 차단 (MySQL 통합)")
class AssistantSwitchOffMySqlTest {

  private static final String API = "/api/tutor/academy/assistant";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
  @MockitoBean private AiProvider aiProvider;

  private User tutor;
  private Cookie cookie;

  /** 계정을 만들고 그 계정으로 로그인한 상태로 둔다. */
  private void loginAs(String loginId) {
    tutor = userRepository.save(new User(loginId, "pw", "스위치강사", "별명", null, null, Role.TUTOR));
    cookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    authenticate();
  }

  @BeforeEach
  void stubAi() {
    subjectRepository.findByNameKo("영어").orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    org.mockito.Mockito.when(aiProvider.isReady()).thenReturn(true);
    org.mockito.Mockito.when(aiProvider.name()).thenReturn("scripted");
    org.mockito.Mockito.when(aiProvider.complete(any(), any())).thenReturn(AiProvider.AiCompletion.failed("no script"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), "TUTOR"),
        null, List.of()));
  }

  private MvcResult turn(Map<String, Object> body) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/turn").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn();
    authenticate();
    return r;
  }

  private MvcResult postJson(String path, String body) throws Exception {
    MvcResult r = mockMvc.perform(post(API + path).cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    authenticate();
    return r;
  }

  private MvcResult upload() throws Exception {
    MockMultipartFile image = new MockMultipartFile("image", "p.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    MvcResult r = mockMvc.perform(multipart(API + "/media").file(image)
        .param("slot", "principal").param("revision", "1").cookie(cookie)).andReturn();
    authenticate();
    return r;
  }

  /** 기존 방식(대화 기록 없음)으로 학원을 만든다. */
  private void createLegacyAcademy() throws Exception {
    Integer subjectId = subjectRepository.findByNameKo("영어").orElseThrow().getSubjectId();
    String body = objectMapper.writeValueAsString(Map.of(
        "name", "기존방식학원", "intro", "기존 빌더로 만든 학원", "subjectIds", List.of(subjectId),
        "targetGroups", List.of("MIDDLE"), "regions", List.of(Map.of("city", "서울", "district", "강남구"))));
    MvcResult r = mockMvc.perform(post("/api/tutor/academy").cookie(cookie)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    authenticate();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }

  private int count(String sql) {
    em.flush();
    return jdbc.queryForObject(sql, Integer.class, tutor.getUserId());
  }

  private int academies() { return count("SELECT COUNT(*) FROM academies WHERE tutor_user_id = ?"); }

  private int messages() {
    return count("SELECT COUNT(*) FROM academy_assistant_message m JOIN academies a ON a.academy_id = m.academy_id WHERE a.tutor_user_id = ?");
  }

  private int workingCopies() {
    return count("SELECT COUNT(*) FROM academy_working_copy w JOIN academies a ON a.academy_id = w.academy_id WHERE a.tutor_user_id = ?");
  }

  private String liveJson() {
    em.flush();
    return jdbc.queryForObject("SELECT CONCAT_WS('|', name, intro, CAST(page_settings AS CHAR)) FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private void assertDisabled(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("ASSISTANT_DISABLED");
  }

  private static final Map<String, Object> CREATE = Map.of(
      "academyName", "새봄영어학원", "sampleKey", "exam", "region", "서울 강남구", "subject", "영어", "target", "중학생");

  @Test
  @DisplayName("given 스위치 OFF·학원 없는 강사, when 최초 생성 턴, then 403 ASSISTANT_DISABLED이고 학원·대화 기록이 생기지 않으며 AI도 호출되지 않는다")
  void createTurnIsBlocked() throws Exception {
    // given
    loginAs("sw_off_" + UUID.randomUUID().toString().substring(0, 8));

    // when
    MvcResult r = turn(CREATE);

    // then
    assertDisabled(r);
    assertThat(academies()).isZero();
    assertThat(messages()).isZero();
    verify(aiProvider, never()).complete(any(), any());
  }

  @Test
  @DisplayName("given 스위치 OFF, when 상태·되돌리기·게시·사진 올리기, then 모두 403 ASSISTANT_DISABLED")
  void everyAssistantEndpointIsBlocked() throws Exception {
    // given
    loginAs("sw_off_" + UUID.randomUUID().toString().substring(0, 8));

    // when / then
    assertDisabled(mockMvc.perform(get(API + "/state").cookie(cookie)).andReturn());
    assertDisabled(postJson("/undo", "{\"revision\":1}"));
    assertDisabled(postJson("/publish", "{\"revision\":1}"));
    assertDisabled(upload());
    assertThat(academies()).isZero();
  }

  @Test
  @DisplayName("given 스위치 OFF·기존 방식 학원, when 대화 수정 턴, then 403이고 작업본·대화 기록·공개 데이터가 그대로이며 AI도 호출되지 않는다")
  void legacyAcademyIsUntouched() throws Exception {
    // given
    loginAs("sw_off_" + UUID.randomUUID().toString().substring(0, 8));
    createLegacyAcademy();
    String liveBefore = liveJson();
    int copiesBefore = workingCopies();

    // when
    MvcResult r = turn(Map.of("message", "주색을 진한 파란색으로 바꿔줘", "revision", 1));

    // then
    assertDisabled(r);
    assertThat(messages()).isZero();
    assertThat(workingCopies()).isEqualTo(copiesBefore);
    assertThat(liveJson()).isEqualTo(liveBefore);
    verify(aiProvider, never()).complete(any(), any());
  }

  @Test
  @DisplayName("given 스위치 OFF·기존 방식 학원, when 기존 빌더의 작업본 API를 부르면, then 영향 없이 정상 동작한다")
  void classicBuilderStillWorks() throws Exception {
    // given
    loginAs("sw_off_" + UUID.randomUUID().toString().substring(0, 8));
    createLegacyAcademy();

    // when
    MvcResult r = mockMvc.perform(get("/api/tutor/academy/working-copy").cookie(cookie)).andReturn();
    authenticate();

    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }
}
