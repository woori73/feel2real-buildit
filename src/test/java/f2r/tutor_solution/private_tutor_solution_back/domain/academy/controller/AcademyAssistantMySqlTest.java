package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 통합 화면 2-A — 대화 턴·대화 기록·원클릭 게시 MySQL 통합 테스트 (실제 V1061 마이그레이션).
 *
 * <p>핵심 불변식: 생성은 작업본에만 쓰고, '게시하기' 전에는 공개 내용이 바뀌지 않으며,
 * 최초 게시에서만 DRAFT → PUBLISHED가 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "buildit.assistant.enabled=true"
})
@DisplayName("대화 서비스 MySQL 통합 테스트 — 작업본 생성·원클릭 게시")
class AcademyAssistantMySqlTest {

  private static final String API = "/api/tutor/academy/assistant";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private User tutor;
  private Cookie authCookie;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tutor = userRepository.save(new User("as_" + suffix, "pw", "대화강사", "별명", null, null, Role.TUTOR));
    authCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(tutor.getUserId(), tutor.getLoginId(), "TUTOR"));
    subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    authenticate();
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

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private MvcResult turn(Map<String, Object> body) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/turn").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn();
    authenticate();
    return r;
  }

  private MvcResult publish(long revision) throws Exception {
    MvcResult r = mockMvc.perform(post(API + "/publish").cookie(authCookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + revision + "}")).andReturn();
    authenticate();
    return r;
  }

  private int academyCount() {
    em.flush();
    return jdbc.queryForObject("SELECT COUNT(*) FROM academies WHERE tutor_user_id = ?", Integer.class, tutor.getUserId());
  }

  private String liveColumn(String column) {
    em.flush();
    return jdbc.queryForObject(
        "SELECT " + column + " FROM academies WHERE tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private String workingCopyColumn(String column) {
    em.flush();
    return jdbc.queryForObject(
        "SELECT w." + column + " FROM academy_working_copy w JOIN academies a ON a.academy_id = w.academy_id "
            + "WHERE a.tutor_user_id = ?", String.class, tutor.getUserId());
  }

  private int messageCount() {
    em.flush();
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM academy_assistant_message m JOIN academies a ON a.academy_id = m.academy_id "
            + "WHERE a.tutor_user_id = ?", Integer.class, tutor.getUserId());
  }

  private MvcResult createFromSample(String sampleKey) throws Exception {
    return turn(Map.of("academyName", "새봄영어학원", "sampleKey", sampleKey, "region", "서울 강남구",
        "subject", "영어", "target", "중학생"));
  }

  @Test
  @DisplayName("given 신규 사용자, when 학원 이름 없이 대화하면, then 이름을 되묻고 학원은 만들어지지 않는다")
  void asksAcademyNameFirst() throws Exception {
    // given: 학원이 없는 신규 TUTOR
    // when
    JsonNode body = json(turn(Map.of("sampleKey", "exam")));
    // then
    assertThat(body.get("needs").toString()).contains("academyName");
    assertThat(body.get("created").asBoolean()).isFalse();
    assertThat(academyCount()).isZero();
  }

  @Test
  @DisplayName("given 이름과 샘플만, when 지역이 없으면, then 지역을 되묻고 주소를 지어내지 않는다")
  void asksRegionInsteadOfInventingAddress() throws Exception {
    // when
    JsonNode body = json(turn(Map.of("academyName", "새봄영어학원", "sampleKey", "exam")));
    // then
    assertThat(body.get("needs").toString()).contains("region");
    assertThat(academyCount()).isZero();
  }

  @Test
  @DisplayName("given 종합학원 샘플과 지역, when 과목을 말하지 않으면, then 과목을 되묻고 영어로 확정하지 않는다")
  void generalSampleAsksSubjectInsteadOfAssumingEnglish() throws Exception {
    // when
    JsonNode body = json(turn(Map.of("academyName", "새봄학원", "sampleKey", "general", "region", "서울 강남구")));
    // then
    assertThat(body.get("needs").toString()).contains("subject");
    assertThat(academyCount()).isZero();
  }

  @Test
  @DisplayName("given 프리미엄 샘플, when 대상을 말하지 않으면, then 대상을 되묻는다")
  void premiumSampleAsksTarget() throws Exception {
    // when
    JsonNode body = json(turn(Map.of("academyName", "새봄학원", "sampleKey", "premium",
        "region", "서울 강남구", "subject", "영어")));
    // then
    assertThat(body.get("needs").toString()).contains("target");
    assertThat(academyCount()).isZero();
  }

  @Test
  @DisplayName("given 이름·샘플·지역, when 첫 턴을 보내면, then DRAFT 학원과 작업본이 생기고 공개 내용은 비어 있다")
  void createsDraftAcademyAndWorkingCopyOnly() throws Exception {
    // when
    JsonNode body = json(createFromSample("exam"));
    // then: 학원은 DRAFT, 프리셋은 작업본에만 있다
    assertThat(body.get("created").asBoolean()).isTrue();
    assertThat(body.get("designPreset").asText()).isEqualTo("EXAM");
    assertThat(liveColumn("status")).isEqualTo("DRAFT");
    assertThat(String.valueOf(liveColumn("page_settings"))).doesNotContain("EXAM");
    assertThat(workingCopyColumn("page_settings_json")).contains("\"designPreset\":\"EXAM\"");
    assertThat(liveColumn("intro")).contains("준비하고 있습니다");
    assertThat(messageCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("given 샘플로 생성, when 결과를 보면, then 말하지 않은 전화번호·수강료·강사는 비어 있다")
  void doesNotInventUnverifiedFacts() throws Exception {
    // when
    createFromSample("premium");
    // then
    String draft = workingCopyColumn("page_settings_json");
    assertThat(draft)
        .doesNotContain("ctaPhone").doesNotContain("monthlyTuition")
        .doesNotContain("principalName").doesNotContain("principalCareer");
  }

  @Test
  @DisplayName("given DRAFT 작업본, when 게시하기를 누르면, then 한 번에 PUBLISHED가 되고 두 번째는 상태 변화가 없다")
  void firstPublishConvertsDraftOnce() throws Exception {
    // given
    long revision = json(createFromSample("minimal")).get("revision").asLong();
    // when
    JsonNode first = json(publish(revision));
    // then
    assertThat(first.get("firstPublish").asBoolean()).isTrue();
    assertThat(liveColumn("status")).isEqualTo("PUBLISHED");
    assertThat(liveColumn("page_settings")).contains("MINIMAL");
    assertThat(liveColumn("intro")).doesNotContain("준비하고 있습니다");
    // when: 다시 게시
    JsonNode second = json(publish(first.get("revision").asLong()));
    // then
    assertThat(second.get("firstPublish").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("given 낡은 revision, when 게시하면, then 409로 거부되고 DRAFT가 유지된다")
  void staleRevisionDoesNotPublish() throws Exception {
    // given
    long revision = json(createFromSample("exam")).get("revision").asLong();
    // when
    MvcResult r = publish(revision + 99);
    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(liveColumn("status")).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("given 이미 학원이 있는 사용자, when 대화하면, then 새 학원을 만들지 않고 공개 내용도 바뀌지 않는다")
  void existingAcademyIsNotOverwritten() throws Exception {
    // given
    createFromSample("exam");
    String introBefore = liveColumn("intro");
    // when
    JsonNode body = json(turn(Map.of("message", "분위기를 바꿔 주세요", "academyName", "다른이름")));
    // then
    assertThat(body.get("created").asBoolean()).isFalse();
    assertThat(academyCount()).isEqualTo(1);
    assertThat(liveColumn("intro")).isEqualTo(introBefore);
  }

  @Test
  @DisplayName("given 로그인 사용자, when 샘플 목록을 조회하면, then 6종이 모두 기존 프리셋을 가리킨다")
  void samplesUseExistingPresets() throws Exception {
    // when
    JsonNode samples = json(mockMvc.perform(get(API + "/samples").cookie(authCookie)).andReturn());
    // then
    assertThat(samples).hasSize(6);
    for (JsonNode s : samples) {
      assertThat(f2r.tutor_solution.private_tutor_solution_back.domain.academy.service
          .AcademyAiCreateService.VALID_PRESETS).contains(s.get("preset").asText());
    }
  }
}
