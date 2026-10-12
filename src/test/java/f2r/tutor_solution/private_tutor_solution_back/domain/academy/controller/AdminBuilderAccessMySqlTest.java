package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * 빌더 계열 권한(ADMIN 허용) 통합 테스트 — 실제 보안 필터 체인과 MySQL.
 * 허용 경로(생성·미리보기·게시)와 여전히 막혀야 하는 경로(TUTOR 전용, STUDENT, 타인 학원)만 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("빌더 접근 권한 MySQL 통합 테스트 — ADMIN 허용, 소유권·TUTOR 전용·STUDENT 제한 유지")
class AdminBuilderAccessMySqlTest {

  private static final String ASSISTANT = "/api/tutor/academy/assistant";
  private static final String WORKING_COPY = "/api/tutor/academy/working-copy";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private User admin;
  private User tutor;
  private User student;
  private Cookie adminCookie;
  private Cookie tutorCookie;
  private Cookie studentCookie;
  private User current;

  @BeforeEach
  void setUp() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    admin = userRepository.save(new User("ad_" + s, "pw", "관리자", "관리", null, null, Role.ADMIN));
    tutor = userRepository.save(new User("tu_" + s, "pw", "강사", "강사", null, null, Role.TUTOR));
    student = userRepository.save(new User("st_" + s, "pw", "학생", "학생", null, null, Role.STUDENT));
    adminCookie = cookie(admin, "ADMIN");
    tutorCookie = cookie(tutor, "TUTOR");
    studentCookie = cookie(student, "STUDENT");
    subjectRepository.findByNameKo("영어").orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private Cookie cookie(User u, String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(u.getUserId(), u.getLoginId(), role));
  }

  /** MockMvc 호출 뒤 SecurityContext가 비워지므로 서비스 계층 직접 호출 전에 다시 세팅한다. */
  private void as(User u, String role) {
    current = u;
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(u.getUserId(), u.getLoginId(), role), null, List.of()));
  }

  private MvcResult call(org.springframework.test.web.servlet.RequestBuilder rb) throws Exception {
    MvcResult r = mockMvc.perform(rb).andReturn();
    if (current != null) as(current, current == admin ? "ADMIN" : current == tutor ? "TUTOR" : "STUDENT");
    return r;
  }

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private MvcResult turn(Cookie c, String name) throws Exception {
    return call(post(ASSISTANT + "/turn").cookie(c).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of(
            "academyName", name, "sampleKey", "exam", "region", "서울 강남구",
            "subject", "영어", "target", "중학생"))));
  }

  private int academyCountOf(User u) {
    em.flush();
    return jdbc.queryForObject("SELECT COUNT(*) FROM academies WHERE tutor_user_id = ?", Integer.class, u.getUserId());
  }

  @Test
  @DisplayName("given ADMIN, when 대화로 생성→미리보기→게시, then 본인 학원이 만들어지고 PUBLISHED가 된다")
  void adminCanCreatePreviewAndPublishOwnAcademy() throws Exception {
    // given/when: 생성
    as(admin, "ADMIN");
    MvcResult created = turn(adminCookie, "관리자시험학원");
    // then
    assertThat(created.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(created);
    assertThat(body.get("created").asBoolean()).isTrue();
    assertThat(academyCountOf(admin)).isEqualTo(1);

    // when: 작업본 미리보기
    MvcResult preview = call(get(WORKING_COPY + "/preview").cookie(adminCookie));
    // then
    assertThat(preview.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(preview).get("name").asText()).isEqualTo("관리자시험학원");

    // when: 게시
    MvcResult published = call(post(ASSISTANT + "/publish").cookie(adminCookie)
        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + body.get("revision").asLong() + "}"));
    // then
    assertThat(published.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(published).get("firstPublish").asBoolean()).isTrue();
    em.flush();
    assertThat(jdbc.queryForObject("SELECT status FROM academies WHERE tutor_user_id = ?", String.class, admin.getUserId()))
        .isEqualTo("PUBLISHED");

    // 본인 학원 조회(me)도 막히지 않는다
    assertThat(call(get("/api/tutor/academy/me").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("given TUTOR와 ADMIN 각자 학원, when 서로 미리보기하면, then 각자 본인 학원만 보인다 (소유권 보호)")
  void eachSeesOnlyOwnAcademy() throws Exception {
    as(tutor, "TUTOR");
    assertThat(turn(tutorCookie, "강사학원").getResponse().getStatus()).isEqualTo(200);
    as(admin, "ADMIN");
    assertThat(turn(adminCookie, "관리자학원").getResponse().getStatus()).isEqualTo(200);

    assertThat(json(call(get(WORKING_COPY + "/preview").cookie(adminCookie))).get("name").asText()).isEqualTo("관리자학원");
    assertThat(json(call(get(WORKING_COPY + "/preview").cookie(tutorCookie))).get("name").asText()).isEqualTo("강사학원");
  }

  @Test
  @DisplayName("given 학원이 없는 ADMIN, when 작업본을 열면, then 타인 학원이 아니라 오류가 난다")
  void adminWithoutAcademyDoesNotSeeOthers() throws Exception {
    as(tutor, "TUTOR");
    turn(tutorCookie, "강사학원");
    as(admin, "ADMIN");
    int status = call(get(WORKING_COPY).cookie(adminCookie)).getResponse().getStatus();
    assertThat(status).isIn(400, 404);
  }

  @Test
  @DisplayName("given ADMIN, when TUTOR 전용 경로(삭제·수업상품·레이아웃·시각 편집기), then 여전히 403이다")
  void adminStillBlockedFromTutorOnlyPaths() throws Exception {
    as(admin, "ADMIN");
    turn(adminCookie, "관리자학원");
    assertThat(call(delete("/api/tutor/academy").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(403);
    assertThat(call(get("/api/tutor/academy/lessons/1").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(403);
    assertThat(call(get("/api/tutor/academy/layout").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(403);
    assertThat(call(get("/api/tutor/academy/page-settings/visual-editor/status").cookie(adminCookie))
        .getResponse().getStatus()).isEqualTo(403);
    assertThat(call(get("/api/tutor/conversations").cookie(adminCookie)).getResponse().getStatus()).isEqualTo(403);
    assertThat(academyCountOf(admin)).isEqualTo(1);
  }

  @Test
  @DisplayName("given STUDENT/비로그인, when 빌더 API 호출, then 권한이 확대되지 않아 거부된다")
  void studentAndAnonymousStillDenied() throws Exception {
    assertThat(call(post(ASSISTANT + "/turn").cookie(studentCookie).contentType(MediaType.APPLICATION_JSON)
        .content("{\"academyName\":\"x\"}")).getResponse().getStatus()).isEqualTo(403);
    assertThat(call(get(WORKING_COPY).cookie(studentCookie)).getResponse().getStatus()).isEqualTo(403);
    assertThat(call(post(ASSISTANT + "/turn").contentType(MediaType.APPLICATION_JSON)
        .content("{\"academyName\":\"x\"}")).getResponse().getStatus()).isIn(401, 403);
    assertThat(academyCountOf(student)).isZero();
  }

  @Test
  @DisplayName("given TUTOR, when 대화로 생성하면, then 기존처럼 동작한다")
  void tutorBehaviorUnchanged() throws Exception {
    as(tutor, "TUTOR");
    MvcResult r = turn(tutorCookie, "강사학원");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).get("created").asBoolean()).isTrue();
    assertThat(call(get("/api/tutor/academy/layout").cookie(tutorCookie)).getResponse().getStatus()).isNotEqualTo(403);
  }
}
