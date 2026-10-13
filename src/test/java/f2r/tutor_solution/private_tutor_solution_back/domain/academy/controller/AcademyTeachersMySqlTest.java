package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 강사진 키(builderDraft.teachersJson)가 기존 작업본·게시·충돌 보호 구조 위에서 그대로 동작하는지 확인한다.
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
@DisplayName("강사진(teachersJson) MySQL 통합 테스트 — 작업본 분리·게시 전 불변·검증·권한")
class AcademyTeachersMySqlTest {

  private static final String MEMBER_WC = "/api/tutor/academy/working-copy";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider tokenProvider;
  @Autowired private AcademyService academyService;
  @Autowired private UserRepository userRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private User admin;
  private User owner;
  private Cookie adminCookie;
  private Cookie ownerCookie;
  private AcademyResponse target;

  @BeforeEach
  void setUp() {
    String s = UUID.randomUUID().toString().substring(0, 8);
    admin = userRepository.save(new User("cm_" + s, "pw", "관리자", "관리", null, null, Role.ADMIN));
    owner = userRepository.save(new User("co_" + s, "pw", "원장", "원장", null, null, Role.TUTOR));
    adminCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(admin.getUserId(), admin.getLoginId(), "ADMIN"));
    ownerCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(owner.getUserId(), owner.getLoginId(), "TUTOR"));
    Subject subject = subjectRepository.findByNameKo("영어")
        .orElseGet(() -> subjectRepository.save(new Subject("ENGLISH", "영어")));
    var principal = new JwtTokenProvider.AuthenticatedPrincipal(owner.getUserId(), owner.getLoginId(), "TUTOR");
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    target = academyService.createDraft(principal,
        new AcademyUpsertRequest("충돌학원", null, "기준 소개", Set.of(subject.getSubjectId()), Set.of(TargetGroup.MIDDLE),
            List.of(new AcademyRegionRequest("서울특별시", "강남구")),
            new AcademyPageSettings(null, null, null, null, null, null,
                Map.of("designPreset", "ENGLISH", "introHeading", "기준 제목", "introMotto", "기준 모토"), null)),
        "c-" + UUID.randomUUID());
    em.flush();
    jdbc.update("UPDATE academies SET status='PUBLISHED', is_visible=true WHERE academy_id=?", target.academyId());
    em.clear();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private String api(String suffix) {
    return "/api/admin/academies/" + target.academyId() + "/builder" + suffix;
  }

  private MvcResult call(RequestBuilder rb) throws Exception {
    return mockMvc.perform(rb).andReturn();
  }

  private JsonNode json(MvcResult r) throws Exception {
    return objectMapper.readTree(r.getResponse().getContentAsString());
  }

  private JsonNode adminOpen() throws Exception {
    return json(call(get(api("")).cookie(adminCookie)));
  }

  private MvcResult adminSaveDraft(long rev, Map<String, String> draft) throws Exception {
    return call(put(api("")).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("revision", rev, "pageSettings", Map.of("builderDraft", draft)))));
  }

  private MvcResult adminPost(String suffix, Map<String, Object> body) throws Exception {
    return call(post(api(suffix)).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)));
  }

  private Map<String, Object> publishBody(long rev) {
    return Map.of("revision", rev, "confirmAcademyId", target.academyId(), "confirmHandle", target.handle());
  }

  /** 회원이 자기 작업본을 고쳐 게시한다 → 공개 내용이 바뀐다. */
  private void memberPublishes(Map<String, String> draft) throws Exception {
    long rev = json(call(get(MEMBER_WC).cookie(ownerCookie))).get("revision").asLong();
    MvcResult saved = call(put(MEMBER_WC).cookie(ownerCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("revision", rev, "pageSettings", Map.of("builderDraft", draft)))));
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    long rev2 = json(saved).get("revision").asLong();
    MvcResult pub = call(post(MEMBER_WC + "/publish").cookie(ownerCookie).contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + rev2 + "}"));
    assertThat(pub.getResponse().getStatus()).isEqualTo(200);
  }

  private String liveDraftValue(String key) {
    em.flush();
    String json = jdbc.queryForObject("SELECT page_settings FROM academies WHERE academy_id=?", String.class, target.academyId());
    try {
      JsonNode node = objectMapper.readTree(json).get("builderDraft");
      return node == null || node.get(key) == null ? null : node.get(key).asText();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final String TEACHERS = "[{\"n\":\"김서연\",\"r\":\"수학 · 입시\",\"c\":[\"서울대 수학과 졸업\"]}]";

  private String publicApi() {
    return "/api/academies/" + target.slug();
  }

  private JsonNode publicBody() throws Exception {
    return json(call(get(publicApi())));
  }

  private JsonNode memberState() throws Exception {
    return json(call(get(MEMBER_WC).cookie(ownerCookie)));
  }

  private MvcResult memberSave(long rev, Map<String, String> draft) throws Exception {
    return call(put(MEMBER_WC).cookie(ownerCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("revision", rev, "pageSettings", Map.of("builderDraft", draft)))));
  }

  @Test
  @DisplayName("given 기존 학원(teachersJson 없음), when 공개 API를 조회하면, then 강사진 키가 없다(기존 학원 불변)")
  void existingAcademyHasNoTeachers() throws Exception {
    JsonNode draft = publicBody().get("pageSettings").get("builderDraft");
    assertThat(draft.has("teachersJson")).isFalse();
    assertThat(draft.has("teachersSectionTitle")).isFalse();
  }

  @Test
  @DisplayName("given 관리자가 강사진을 작업본에 저장, when 공개 API를 조회하면, then 공개는 불변이고 게시 후에만 반영된다")
  void adminTeachersAreVisibleOnlyAfterPublish() throws Exception {
    String before = publicBody().toString();
    long rev = adminOpen().get("revision").asLong();

    MvcResult saved = adminSaveDraft(rev, Map.of("teachersJson", TEACHERS, "teachersSectionTitle", "우리 선생님"));

    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    assertThat(publicBody().toString()).isEqualTo(before);
    assertThat(adminOpen().get("pageSettings").get("builderDraft").get("teachersJson").asText()).isEqualTo(TEACHERS);
    // 미리보기(작업본)에는 보인다
    JsonNode preview = json(call(get(api("/preview")).cookie(adminCookie)));
    assertThat(preview.get("pageSettings").get("builderDraft").get("teachersJson").asText()).isEqualTo(TEACHERS);

    MvcResult published = adminPost("/publish", publishBody(adminOpen().get("revision").asLong()));

    assertThat(published.getResponse().getStatus()).isEqualTo(200);
    JsonNode live = publicBody().get("pageSettings").get("builderDraft");
    assertThat(live.get("teachersJson").asText()).isEqualTo(TEACHERS);
    assertThat(live.get("teachersSectionTitle").asText()).isEqualTo("우리 선생님");
    // 기존 필드는 그대로
    assertThat(live.get("introHeading").asText()).isEqualTo("기준 제목");
  }

  @Test
  @DisplayName("given 관리자 작업본에만 강사진이 있음, when 회원 작업본을 열면, then 회원 작업본에는 섞이지 않는다(분리 유지)")
  void adminAndMemberWorkingCopiesStaySeparate() throws Exception {
    adminSaveDraft(adminOpen().get("revision").asLong(), Map.of("teachersJson", TEACHERS));

    JsonNode member = memberState();

    assertThat(member.get("pageSettings").get("builderDraft").has("teachersJson")).isFalse();
  }

  @Test
  @DisplayName("given 회원이 강사진을 저장·게시, when 관리자가 같은 키를 다르게 수정해 반영(rebase)하면, then 공개(회원) 값을 유지하고 충돌로 알린다")
  void sameKeyConflictKeepsLiveAndReports() throws Exception {
    long rev = adminOpen().get("revision").asLong();
    adminSaveDraft(rev, Map.of("teachersJson", "[{\"n\":\"관리자 입력\"}]"));
    memberPublishes(Map.of("teachersJson", TEACHERS));

    MvcResult blocked = adminPost("/publish", publishBody(adminOpen().get("revision").asLong()));
    assertThat(blocked.getResponse().getStatus()).isEqualTo(409);

    JsonNode body = json(adminPost("/rebase", Map.of("revision", adminOpen().get("revision").asLong())));

    assertThat(body.get("conflicts").toString()).contains("builderDraft.teachersJson");
    assertThat(body.get("pageSettings").get("builderDraft").get("teachersJson").asText()).isEqualTo(TEACHERS);
    assertThat(liveDraftValue("teachersJson")).isEqualTo(TEACHERS);
  }

  @Test
  @DisplayName("given 관리자가 강사진, 회원이 다른 필드를 수정·게시, when 반영(rebase)하면, then 양쪽 변경이 모두 보존된다")
  void differentFieldsMerge() throws Exception {
    long rev = adminOpen().get("revision").asLong();
    adminSaveDraft(rev, Map.of("teachersJson", TEACHERS));
    memberPublishes(Map.of("introHeading", "회원이 바꾼 제목"));

    json(adminPost("/rebase", Map.of("revision", adminOpen().get("revision").asLong())));
    MvcResult published = adminPost("/publish", publishBody(adminOpen().get("revision").asLong()));

    assertThat(published.getResponse().getStatus()).isEqualTo(200);
    assertThat(liveDraftValue("introHeading")).isEqualTo("회원이 바꾼 제목");
    assertThat(liveDraftValue("teachersJson")).isEqualTo(TEACHERS);
  }

  @Test
  @DisplayName("given 잘못된 강사 데이터(스킴·필드·개수·형식), when 관리자·회원이 저장하면, then 400으로 거부되고 작업본은 그대로다")
  void invalidTeachersAreRejected() throws Exception {
    long rev = adminOpen().get("revision").asLong();
    for (String bad : new String[] {
        "[{\"n\":\"가\",\"p\":\"javascript:alert(1)\"}]",
        "[{\"n\":\"가\",\"p\":\"http://ex.com/a.jpg\"}]",
        "[{\"r\":\"이름 없음\"}]",
        "[{\"n\":\"가\",\"x\":\"허용 안 되는 필드\"}]",
        "{\"n\":\"배열 아님\"}",
        "깨진 JSON"}) {
      MvcResult admin = adminSaveDraft(rev, Map.of("teachersJson", bad));
      assertThat(admin.getResponse().getStatus()).as("admin: " + bad).isEqualTo(400);
      MvcResult member = memberSave(memberState().get("revision").asLong(), Map.of("teachersJson", bad));
      assertThat(member.getResponse().getStatus()).as("member: " + bad).isIn(400, 409);
    }
    assertThat(adminOpen().get("pageSettings").get("builderDraft").has("teachersJson")).isFalse();
    assertThat(memberState().get("pageSettings").get("builderDraft").has("teachersJson")).isFalse();
  }

  @Test
  @DisplayName("given 강사 9명·긴 경력, when 저장하면, then 400")
  void limitsAreEnforced() throws Exception {
    StringBuilder nine = new StringBuilder("[");
    for (int i = 0; i < 9; i++) nine.append(i > 0 ? "," : "").append("{\"n\":\"강사").append(i).append("\"}");
    long rev = adminOpen().get("revision").asLong();

    assertThat(adminSaveDraft(rev, Map.of("teachersJson", nine + "]")).getResponse().getStatus()).isEqualTo(400);
    assertThat(adminSaveDraft(rev, Map.of("teachersJson", "[{\"n\":\"가\",\"c\":[\"" + "가".repeat(81) + "\"]}]"))
        .getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("given 비로그인·STUDENT, when 관리자 강사진 저장·미리보기를 호출하면, then 거부된다(권한 변화 없음)")
  void nonAdminsCannotWriteTeachers() throws Exception {
    String s = UUID.randomUUID().toString().substring(0, 8);
    User student = userRepository.save(new User("cs_" + s, "pw", "학생", "학생", null, null, Role.STUDENT));
    Cookie studentCookie = new Cookie("ACCESS_TOKEN", tokenProvider.issue(student.getUserId(), student.getLoginId(), "STUDENT"));
    String body = objectMapper.writeValueAsString(
        Map.of("revision", 0, "pageSettings", Map.of("builderDraft", Map.of("teachersJson", TEACHERS))));

    for (Cookie c : new Cookie[] {studentCookie, ownerCookie}) {
      assertThat(call(put(api("")).cookie(c).contentType(MediaType.APPLICATION_JSON).content(body))
          .getResponse().getStatus()).isEqualTo(403);
    }
    assertThat(call(put(api("")).contentType(MediaType.APPLICATION_JSON).content(body)).getResponse().getStatus())
        .isIn(401, 403);
    assertThat(call(put(MEMBER_WC).cookie(studentCookie).contentType(MediaType.APPLICATION_JSON).content(body))
        .getResponse().getStatus()).isEqualTo(403);
    assertThat(publicBody().get("pageSettings").get("builderDraft").has("teachersJson")).isFalse();
  }

  @Test
  @DisplayName("given 회원(TUTOR) 본인 작업본, when 강사진을 저장·게시하면, then 기존 회원 흐름 그대로 동작한다")
  void memberCanSaveAndPublishOwnTeachers() throws Exception {
    memberPublishes(Map.of("teachersJson", TEACHERS));

    assertThat(liveDraftValue("teachersJson")).isEqualTo(TEACHERS);
  }
}
