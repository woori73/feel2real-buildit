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
 * 관리자 작업본과 회원 작업본/공개 홈페이지 사이의 충돌 보호 — 한쪽 작업이 다른 쪽 수정을 조용히 덮어쓰지 않는다.
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
@DisplayName("관리자·회원 작업 충돌 보호 MySQL 통합 테스트")
class AdminAcademyConflictMySqlTest {

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

  @Test
  @DisplayName("given 관리자 작업본 이후 회원이 게시, when 관리자가 그냥 게시하면, then 409 LIVE_CHANGED이고 회원의 공개 수정이 보존된다")
  void adminPublishBlockedWhenLiveChanged() throws Exception {
    JsonNode opened = adminOpen();
    adminSaveDraft(opened.get("revision").asLong(), Map.of("introMotto", "관리자 모토"));
    memberPublishes(Map.of("introHeading", "회원 제목"));

    JsonNode state = adminOpen();
    assertThat(state.get("liveChanged").asBoolean()).isTrue();
    MvcResult r = adminPost("/publish", publishBody(state.get("revision").asLong()));
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(r).get("code").asText()).isEqualTo("LIVE_CHANGED");
    assertThat(liveDraftValue("introHeading")).isEqualTo("회원 제목");
  }

  @Test
  @DisplayName("given 서로 다른 필드를 바꿈, when 공개 변경을 반영(rebase)하고 게시하면, then 회원 수정과 관리자 수정이 모두 남는다")
  void rebaseKeepsBothSidesForDisjointFields() throws Exception {
    long rev = adminOpen().get("revision").asLong();
    adminSaveDraft(rev, Map.of("introMotto", "관리자 모토"));
    memberPublishes(Map.of("introHeading", "회원 제목"));

    JsonNode state = adminOpen();
    MvcResult rebased = adminPost("/rebase", Map.of("revision", state.get("revision").asLong()));
    assertThat(rebased.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = json(rebased);
    assertThat(body.get("conflicts")).isEmpty();
    assertThat(body.get("pageSettings").get("builderDraft").get("introHeading").asText()).isEqualTo("회원 제목");
    assertThat(body.get("pageSettings").get("builderDraft").get("introMotto").asText()).isEqualTo("관리자 모토");
    assertThat(body.get("liveChanged").asBoolean()).isFalse();
    assertThat(liveDraftValue("introMotto")).isEqualTo("기준 모토"); // 반영 전에는 공개 불변

    MvcResult pub = adminPost("/publish", publishBody(body.get("revision").asLong()));
    assertThat(pub.getResponse().getStatus()).isEqualTo(200);
    assertThat(liveDraftValue("introHeading")).isEqualTo("회원 제목");
    assertThat(liveDraftValue("introMotto")).isEqualTo("관리자 모토");
  }

  @Test
  @DisplayName("given 같은 필드를 양쪽이 다르게 바꿈, when rebase하면, then 공개(회원) 값을 유지하고 충돌로 알리며 감사 기록에 남는다")
  void rebaseReportsConflictAndKeepsLive() throws Exception {
    long rev = adminOpen().get("revision").asLong();
    adminSaveDraft(rev, Map.of("introHeading", "관리자 제목"));
    memberPublishes(Map.of("introHeading", "회원 제목"));

    JsonNode body = json(adminPost("/rebase", Map.of("revision", adminOpen().get("revision").asLong())));
    assertThat(body.get("conflicts").toString()).contains("builderDraft.introHeading");
    assertThat(body.get("pageSettings").get("builderDraft").get("introHeading").asText()).isEqualTo("회원 제목");
    em.flush();
    String details = jdbc.queryForObject(
        "SELECT details_json FROM academy_admin_audit WHERE academy_id=? AND summary LIKE '공개 변경 반영%' ORDER BY audit_id DESC LIMIT 1",
        String.class, target.academyId());
    assertThat(details).contains("\"rebase\":true").contains("builderDraft.introHeading");
    assertThat(liveDraftValue("introHeading")).isEqualTo("회원 제목");
  }

  @Test
  @DisplayName("given 회원이 게시 안 한 작업본이 있음, when 관리자가 열고 게시하면, then 회원 작업본 내용은 그대로이고 회원이 이후 게시하면 409로 보호된다")
  void memberDraftIsNotOverwrittenAndMemberPublishIsProtected() throws Exception {
    // 회원이 작업본을 고쳤지만 게시하지 않음
    long mrev = json(call(get(MEMBER_WC).cookie(ownerCookie))).get("revision").asLong();
    call(put(MEMBER_WC).cookie(ownerCookie).contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("revision", mrev, "intro", "회원 미게시 소개"))));

    JsonNode opened = adminOpen();
    assertThat(opened.get("memberDraftDirty").asBoolean()).isTrue();
    // 확인 화면용 현재 공개 내용
    assertThat(opened.get("live").get("intro").asText()).isEqualTo("기준 소개");
    assertThat(opened.get("live").get("builderDraft").get("introHeading").asText()).isEqualTo("기준 제목");

    adminSaveDraft(opened.get("revision").asLong(), Map.of("introMotto", "관리자 모토"));
    assertThat(adminPost("/publish", publishBody(adminOpen().get("revision").asLong())).getResponse().getStatus()).isEqualTo(200);

    // 회원 작업본 내용은 그대로(덮어쓰이지 않음)
    JsonNode member = json(call(get(MEMBER_WC).cookie(ownerCookie)));
    assertThat(member.get("intro").asText()).isEqualTo("회원 미게시 소개");
    assertThat(member.get("liveChanged").asBoolean()).isTrue();
    // 회원이 그냥 게시하면 관리자 변경을 덮어쓰지 못하고 409
    MvcResult memberPub = call(post(MEMBER_WC + "/publish").cookie(ownerCookie).contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + member.get("revision").asLong() + "}"));
    assertThat(memberPub.getResponse().getStatus()).isEqualTo(409);
    assertThat(liveDraftValue("introMotto")).isEqualTo("관리자 모토");
  }

  @Test
  @DisplayName("given 낡은 revision, when rebase하면, then 409 REVISION_CONFLICT이고 작업본은 그대로다")
  void rebaseRespectsRevision() throws Exception {
    long rev = adminOpen().get("revision").asLong();
    MvcResult r = adminPost("/rebase", Map.of("revision", rev + 7));
    assertThat(r.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(r).get("code").asText()).isEqualTo("REVISION_CONFLICT");
  }
}
