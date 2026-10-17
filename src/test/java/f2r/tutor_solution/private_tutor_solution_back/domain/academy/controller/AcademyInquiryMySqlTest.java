package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 상담신청(academy_inquiries, 실제 V1086) MySQL 통합 테스트.
 * 핵심 불변식: 학원별 격리, 원장/관리자만 조회, 동의 필수, 스팸·중복 비저장, 상태 3단계.
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
@DisplayName("상담신청 MySQL 통합 테스트 — 격리·권한·접수 규칙")
class AcademyInquiryMySqlTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper om;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private UserRepository users;
  @Autowired private SubjectRepository subjects;
  @Autowired private JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  private record Owner(User user, Cookie cookie, String slug, long academyId) {}

  private Owner a;
  private Owner b;
  private static final java.util.concurrent.atomic.AtomicInteger IP_SEQ = new java.util.concurrent.atomic.AtomicInteger(10);

  @BeforeEach
  void setUp() throws Exception {
    subjects.findByNameKo("영어").orElseGet(() -> subjects.save(new Subject("ENGLISH", "영어")));
    a = publishedAcademyOwner("A");
    b = publishedAcademyOwner("B");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private Owner publishedAcademyOwner(String tag) throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    User u = users.save(new User("inq_" + suffix, "pw", "원장" + tag, "별명", null, null, Role.TUTOR));
    Cookie c = new Cookie("ACCESS_TOKEN", tokens.issue(u.getUserId(), u.getLoginId(), "TUTOR"));
    auth(u);
    MvcResult turn = mockMvc.perform(post("/api/tutor/academy/assistant/turn").cookie(c).contentType(MediaType.APPLICATION_JSON)
        .content(om.writeValueAsString(Map.of("academyName", "상담학원" + tag + suffix, "sampleKey", "junior-english",
            "region", "서울 강남구", "subject", "영어", "target", "중학생")))).andReturn();
    auth(u);
    long rev = om.readTree(turn.getResponse().getContentAsString()).get("revision").asLong();
    mockMvc.perform(post("/api/tutor/academy/assistant/publish").cookie(c).contentType(MediaType.APPLICATION_JSON)
        .content("{\"revision\":" + rev + "}")).andReturn();
    auth(u);
    em.flush();
    Map<String, Object> row = jdbc.queryForMap("SELECT academy_id, slug FROM academies WHERE tutor_user_id = ?", u.getUserId());
    return new Owner(u, c, (String) row.get("slug"), ((Number) row.get("academy_id")).longValue());
  }

  private void auth(User u) {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new JwtTokenProvider.AuthenticatedPrincipal(u.getUserId(), u.getLoginId(), "TUTOR"), null, List.of()));
  }

  private MvcResult submit(String slug, Map<String, Object> body) throws Exception {
    String ip = "10.9." + (IP_SEQ.incrementAndGet() / 250) + "." + (IP_SEQ.get() % 250); // 호출마다 다른 IP → 속도 제한 격리(테스트 클래스 전체에서 유일)
    return mockMvc.perform(post("/api/academies/" + slug + "/inquiries").with(r -> { r.setRemoteAddr(ip); return r; })
        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))).andReturn();
  }

  private static Map<String, Object> valid(String phone) {
    return Map.of("name", "김학부모", "phone", phone, "message", "중2 영어 상담 원해요", "consent", true, "website", "");
  }

  private int count(long academyId) {
    em.flush();
    return jdbc.queryForObject("SELECT COUNT(*) FROM academy_inquiries WHERE academy_id = ?", Integer.class, academyId);
  }

  private JsonNode list(Owner o, String query) throws Exception {
    MvcResult r = mockMvc.perform(get("/api/tutor/academy/inquiries" + query).cookie(o.cookie())).andReturn();
    auth(o.user());
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
    return om.readTree(r.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("given 공개된 학원 when 로그인 없이 동의하고 접수하면 then 202로 저장되고 NEW 상태·동의·보관기한이 남는다")
  void publicSubmitStores() throws Exception {
    // given / when
    MvcResult r = submit(a.slug(), valid("010-1111-2222"));
    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(count(a.academyId())).isEqualTo(1);
    Map<String, Object> row = jdbc.queryForMap("SELECT status, phone, consent_version, DATEDIFF(retention_until, DATE(consented_at)) AS days FROM academy_inquiries WHERE academy_id = ?", a.academyId());
    assertThat(row.get("status")).isEqualTo("NEW");
    assertThat(row.get("phone")).isEqualTo("01011112222");
    assertThat(row.get("consent_version")).isNotNull();
    assertThat(((Number) row.get("days")).intValue()).isEqualTo(365);
  }

  @Test
  @DisplayName("given 동의 없음·잘못된 입력 when 접수하면 then 400이고 저장되지 않는다")
  void invalidNotStored() throws Exception {
    // given / when / then
    assertThat(submit(a.slug(), Map.of("name", "김", "phone", "01011112222", "message", "문의", "consent", false)).getResponse().getStatus()).isEqualTo(400);
    assertThat(submit(a.slug(), Map.of("name", "김", "phone", "12", "message", "문의", "consent", true)).getResponse().getStatus()).isEqualTo(400);
    assertThat(count(a.academyId())).isZero();
  }

  @Test
  @DisplayName("given 함정 칸이 채워진 요청·같은 연락처 반복 when 접수하면 then 202지만 저장하지 않거나 1건만 저장한다")
  void spamAndDuplicate() throws Exception {
    // given / when
    Map<String, Object> bot = new java.util.HashMap<>(valid("01033334444"));
    bot.put("website", "http://spam.example");
    assertThat(submit(a.slug(), bot).getResponse().getStatus()).isEqualTo(202);
    assertThat(count(a.academyId())).isZero();
    submit(a.slug(), valid("01055556666"));
    submit(a.slug(), valid("010-5555-6666"));
    // then
    assertThat(count(a.academyId())).isEqualTo(1);
  }

  @Test
  @DisplayName("given 같은 IP when 1분에 4번 접수하면 then 4번째는 429다")
  void rateLimited() throws Exception {
    // given
    String ip = "198.51.100." + (System.nanoTime() % 200 + 1);
    int last = 0;
    // when
    for (int i = 0; i < 4; i++) {
      Map<String, Object> body = valid("0107777000" + i);
      last = mockMvc.perform(post("/api/academies/" + a.slug() + "/inquiries").with(r -> { r.setRemoteAddr(ip); return r; })
          .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }
    // then
    assertThat(last).isEqualTo(429);
  }

  @Test
  @DisplayName("given 존재하지 않는 slug when 접수하면 then 404다")
  void unknownSlug() throws Exception {
    // given / when / then
    assertThat(submit("no-such-academy-zzz", valid("01011112222")).getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  @DisplayName("given 두 학원에 접수 when 각 원장이 조회하면 then 자기 학원 접수만 보이고 남의 접수 상태 변경은 404다")
  void tenantIsolation() throws Exception {
    // given
    submit(a.slug(), valid("01011110001"));
    submit(b.slug(), valid("01022220002"));
    em.flush();
    long bId = jdbc.queryForObject("SELECT inquiry_id FROM academy_inquiries WHERE academy_id = ?", Long.class, b.academyId());
    // when
    JsonNode aList = list(a, "");
    JsonNode bList = list(b, "");
    MvcResult cross = mockMvc.perform(patch("/api/tutor/academy/inquiries/" + bId + "/status").cookie(a.cookie())
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andReturn();
    auth(a.user());
    // then
    assertThat(aList.get("total").asInt()).isEqualTo(1);
    assertThat(aList.get("items").get(0).get("phone").asText()).isEqualTo("01011110001");
    assertThat(bList.get("items").get(0).get("phone").asText()).isEqualTo("01022220002");
    assertThat(cross.getResponse().getStatus()).isEqualTo(404);
    em.flush();
    assertThat(jdbc.queryForObject("SELECT status FROM academy_inquiries WHERE inquiry_id = ?", String.class, bId)).isEqualTo("NEW");
  }

  @Test
  @DisplayName("given 접수 1건 when 상태를 처리중→완료로 바꾸면 then 반영되고 잘못된 상태는 400이다")
  void statusFlow() throws Exception {
    // given
    submit(a.slug(), valid("01099990000"));
    em.flush();
    long id = jdbc.queryForObject("SELECT inquiry_id FROM academy_inquiries WHERE academy_id = ?", Long.class, a.academyId());
    // when / then
    for (String s : new String[] {"IN_PROGRESS", "DONE"}) {
      MvcResult r = mockMvc.perform(patch("/api/tutor/academy/inquiries/" + id + "/status").cookie(a.cookie())
          .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}")).andReturn();
      auth(a.user());
      assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }
    em.flush();
    assertThat(jdbc.queryForObject("SELECT status FROM academy_inquiries WHERE inquiry_id = ?", String.class, id)).isEqualTo("DONE");
    assertThat(list(a, "?status=DONE").get("total").asInt()).isEqualTo(1);
    assertThat(list(a, "").get("newCount").asInt()).isZero();
    MvcResult bad = mockMvc.perform(patch("/api/tutor/academy/inquiries/" + id + "/status").cookie(a.cookie())
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}")).andReturn();
    auth(a.user());
    assertThat(bad.getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  @DisplayName("given 비로그인·학생 when 목록을 조회하면 then 거절되고 다른 학원 관리자 조회 API는 원장에게 403이다")
  void accessControl() throws Exception {
    // given
    User student = users.save(new User("st_" + UUID.randomUUID().toString().substring(0, 8), "pw", "학생", "s", null, null, Role.STUDENT));
    Cookie sc = new Cookie("ACCESS_TOKEN", tokens.issue(student.getUserId(), student.getLoginId(), "STUDENT"));
    // when / then
    int anon = mockMvc.perform(get("/api/tutor/academy/inquiries")).andReturn().getResponse().getStatus();
    int stu = mockMvc.perform(get("/api/tutor/academy/inquiries").cookie(sc)).andReturn().getResponse().getStatus();
    int tutorOnAdmin = mockMvc.perform(get("/api/admin/academies/" + a.academyId() + "/inquiries").cookie(a.cookie())).andReturn().getResponse().getStatus();
    assertThat(anon).isIn(401, 403);
    assertThat(stu).isEqualTo(403);
    assertThat(tutorOnAdmin).isEqualTo(403);
  }
}
