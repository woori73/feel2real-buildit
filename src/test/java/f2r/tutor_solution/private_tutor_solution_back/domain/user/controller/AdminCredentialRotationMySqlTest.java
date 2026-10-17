package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 MySQL·전체 보안 필터에서 ADMIN ID·비밀번호 교체의 종단 동작을 검증한다.
 * (ADMIN 세션 stamp 검사는 이 테스트에서만 켠다 — 운영 기본값과 동일.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.admin-session-binding.enabled=true")
@DisplayName("ADMIN ID·비밀번호 교체 — 구 자격 증명·구 세션 무효, 다른 계정 무영향")
class AdminCredentialRotationMySqlTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JwtTokenProvider tokens;
  private final ObjectMapper json = new ObjectMapper();

  // 테스트마다 무작위로 만든다(고정된 비밀번호 문자열을 코드에 두지 않는다).
  private final String suffix = UUID.randomUUID().toString().substring(0, 8);
  private final String adminId = "t-admin-" + suffix;
  private final String oldPw = "old-" + UUID.randomUUID();
  private final String newId = "t-owner-" + suffix;
  private final String newPw = "new-" + UUID.randomUUID();
  private final String studentId = "t-stu-" + suffix;
  private final String studentPw = "stu-" + UUID.randomUUID();
  private Long adminUserId;

  @BeforeEach
  void seed() {
    adminUserId = users.save(User.builder().loginId(adminId).password(encoder.encode(oldPw)).name("관리자")
        .nickname("관리자").role(Role.ADMIN).build()).getUserId();
    users.save(User.builder().loginId(studentId).password(encoder.encode(studentPw)).name("학생")
        .nickname("학생").role(Role.STUDENT).build());
    users.flush();
  }

  private Cookie login(String id, String pw) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("loginId", id, "password", pw))))
        .andExpect(status().isOk()).andReturn();
    return r.getResponse().getCookie("ACCESS_TOKEN");
  }

  private int loginStatus(String id, String pw) throws Exception {
    return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("loginId", id, "password", pw)))).andReturn().getResponse().getStatus();
  }

  /** 관리자 API 전체가 같은 필터를 쓰는지 확인하기 위한 대표 엔드포인트 묶음. */
  private static final String[] ADMIN_APIS = {
      "/api/admin/analytics/summary", "/api/admin/analytics/breakdown", "/api/admin/members",
      "/api/admin/lesson-communities", "/api/admin/analyzes", "/api/admin/wiki/originals", "/api/admin/wiki/ops-overview"};

  private int adminApiAt(String path, Cookie c) throws Exception {
    return mockMvc.perform(get(path).cookie(c)).andReturn().getResponse().getStatus();
  }

  private int adminApi(Cookie c) throws Exception {
    return mockMvc.perform(get("/api/admin/analytics/summary").cookie(c)).andReturn().getResponse().getStatus();
  }

  private MvcResult rotate(Cookie c, String current, String id, String pw, String confirm) throws Exception {
    return mockMvc.perform(post("/api/admin/account/credentials").cookie(c).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("currentPassword", current, "newLoginId", id, "confirmLoginId", id, "newPassword", pw,
            "confirmPassword", confirm)))).andReturn();
  }

  @Test
  @DisplayName("given 로그인한 ADMIN when 교체하면 then 204·쿠키 삭제, 구 세션·구 ID/비밀번호는 거부되고 새 ID/비밀번호는 같은 계정으로 로그인된다")
  void rotation_endToEnd() throws Exception {
    // given
    Cookie oldSession = login(adminId, oldPw);
    Cookie secondOldSession = login(adminId, oldPw); // 다른 기기의 세션
    assertThat(adminApi(oldSession)).isEqualTo(200);

    // when
    MvcResult res = rotate(oldSession, oldPw, newId, newPw, newPw);

    // then: 204, 현재 쿠키는 삭제 지시
    assertThat(res.getResponse().getStatus()).isEqualTo(204);
    assertThat(res.getResponse().getCookie("ACCESS_TOKEN").getMaxAge()).isZero();
    // 구 세션(두 개 모두)은 더 이상 관리자 API를 쓸 수 없다
    assertThat(adminApi(oldSession)).isEqualTo(401);
    assertThat(adminApi(secondOldSession)).isEqualTo(401);
    // 구 ID/비밀번호로는 로그인 불가(구 비밀번호+새 ID, 새 비밀번호+구 ID 모두)
    assertThat(loginStatus(adminId, oldPw)).isGreaterThanOrEqualTo(400);
    assertThat(loginStatus(adminId, newPw)).isGreaterThanOrEqualTo(400);
    assertThat(loginStatus(newId, oldPw)).isGreaterThanOrEqualTo(400);
    // 새 ID/비밀번호는 같은 계정(user_id)·같은 ADMIN 권한으로 로그인되고 관리자 API가 열린다
    Cookie fresh = login(newId, newPw);
    assertThat(adminApi(fresh)).isEqualTo(200);
    User after = users.findByLoginId(newId).orElseThrow();
    assertThat(after.getUserId()).isEqualTo(adminUserId);
    assertThat(after.getRole()).isEqualTo(Role.ADMIN);
    assertThat(after.getPassword()).doesNotContain(newPw).startsWith("$2");
  }

  @Test
  @DisplayName("given 이 기능 이전 방식으로 발급된(stamp 없는) ADMIN 토큰 when 관리자 API를 호출하면 then 거부된다")
  void legacyAdminToken_rejected() throws Exception {
    Cookie legacy = new Cookie("ACCESS_TOKEN", tokens.issue(adminUserId, adminId, "ADMIN"));
    assertThat(adminApi(legacy)).isEqualTo(401);
  }

  @Test
  @DisplayName("given 틀린 현재 비밀번호 when 교체하면 then 401이고 계정과 세션은 그대로다")
  void wrongCurrent_noChange() throws Exception {
    Cookie session = login(adminId, oldPw);
    assertThat(rotate(session, "wrong-" + UUID.randomUUID(), newId, newPw, newPw).getResponse().getStatus()).isEqualTo(401);
    assertThat(adminApi(session)).isEqualTo(200);
    assertThat(loginStatus(adminId, oldPw)).isEqualTo(200);
  }

  @Test
  @DisplayName("given 교체 when 일반 회원 세션·로그인을 보면 then 영향이 없고, 일반 회원은 교체 API를 쓸 수 없다")
  void otherAccounts_unaffected() throws Exception {
    Cookie studentSession = login(studentId, studentPw);
    Cookie adminSession = login(adminId, oldPw);
    assertThat(rotate(adminSession, oldPw, newId, newPw, newPw).getResponse().getStatus()).isEqualTo(204);

    mockMvc.perform(get("/api/auth/me").cookie(studentSession)).andExpect(status().isOk());
    assertThat(loginStatus(studentId, studentPw)).isEqualTo(200);
    // 일반 회원은 ADMIN 전용 교체 API에서 403, 익명은 401
    assertThat(rotate(studentSession, studentPw, "t-x-" + suffix, newPw, newPw).getResponse().getStatus()).isEqualTo(403);
    assertThat(mockMvc.perform(post("/api/admin/account/credentials").contentType(MediaType.APPLICATION_JSON)
        .content("{}")).andReturn().getResponse().getStatus()).isEqualTo(401);
    // TUTOR 토큰(stamp 없음)도 stamp 검사 대상이 아니다
    Cookie tutor = new Cookie("ACCESS_TOKEN", tokens.issue(987654L, "t-tutor", "TUTOR"));
    assertThat(mockMvc.perform(get("/api/auth/me").cookie(tutor)).andReturn().getResponse().getStatus())
        .isNotEqualTo(401).isNotEqualTo(403);
  }

  @Test
  @DisplayName("given 이미 사용 중인 ID when 교체하면 then 409이고 계정은 그대로다")
  void duplicateId_conflict() throws Exception {
    Cookie session = login(adminId, oldPw);
    assertThat(rotate(session, oldPw, studentId, newPw, newPw).getResponse().getStatus()).isEqualTo(409);
    assertThat(users.findByLoginId(adminId)).isPresent();
    assertThat(adminApi(session)).isEqualTo(200);
  }

  @Test
  @DisplayName("given 교체 이전에 발급된 ADMIN JWT(구 stamp·stamp 없음) when 관리자 API 전체를 호출하면 then 모두 401이고, 새 세션은 같은 API가 401이 아니다")
  void oldAdminJwt_rejectedOnAllAdminApis() throws Exception {
    // given: 교체 전 세션 2개(기기 A·B) + 기능 이전 방식 토큰
    Cookie deviceA = login(adminId, oldPw);
    Cookie deviceB = login(adminId, oldPw);
    Cookie legacy = new Cookie("ACCESS_TOKEN", tokens.issue(adminUserId, adminId, "ADMIN"));
    for (String api : ADMIN_APIS) {
      assertThat(adminApiAt(api, deviceA)).as("before " + api).isNotIn(401, 403);
    }
    // when
    assertThat(rotate(deviceA, oldPw, newId, newPw, newPw).getResponse().getStatus()).isEqualTo(204);
    Cookie fresh = login(newId, newPw);
    // then
    for (String api : ADMIN_APIS) {
      assertThat(adminApiAt(api, deviceA)).as("deviceA " + api).isEqualTo(401);
      assertThat(adminApiAt(api, deviceB)).as("deviceB " + api).isEqualTo(401);
      assertThat(adminApiAt(api, legacy)).as("legacy " + api).isEqualTo(401);
      assertThat(adminApiAt(api, fresh)).as("fresh " + api).isNotIn(401, 403);
    }
  }

  @Test
  @DisplayName("given 교체 후 when 이전 비밀번호를 이전·새 ID 어느 쪽으로 써도 then 로그인되지 않고, 새 비밀번호는 이전 ID로 로그인되지 않는다")
  void oldPasswordNeverAuthenticatesAgain() throws Exception {
    Cookie s = login(adminId, oldPw);
    assertThat(rotate(s, oldPw, newId, newPw, newPw).getResponse().getStatus()).isEqualTo(204);
    assertThat(loginStatus(adminId, oldPw)).isGreaterThanOrEqualTo(400);
    assertThat(loginStatus(newId, oldPw)).isGreaterThanOrEqualTo(400);
    assertThat(loginStatus(adminId, newPw)).isGreaterThanOrEqualTo(400);
    assertThat(loginStatus(newId, newPw)).isEqualTo(200);
  }

  @Test
  @DisplayName("given 새 ID 확인값이 다르거나 예약된 폐기 ID일 때 when 교체하면 then 400이고 계정은 그대로라 접근을 잃지 않는다")
  void mistypedOrReservedId_noLockout() throws Exception {
    Cookie s = login(adminId, oldPw);
    MvcResult mismatch = mockMvc.perform(post("/api/admin/account/credentials").cookie(s).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("currentPassword", oldPw, "newLoginId", newId, "confirmLoginId", newId + "x",
            "newPassword", newPw, "confirmPassword", newPw)))).andReturn();
    assertThat(mismatch.getResponse().getStatus()).isEqualTo(400);
    assertThat(rotate(s, oldPw, "admin-f2r", newPw, newPw).getResponse().getStatus()).isEqualTo(400);
    assertThat(adminApi(s)).isEqualTo(200);
    assertThat(loginStatus(adminId, oldPw)).isEqualTo(200);
  }

  @Test
  @DisplayName("given 폐기된 최초 관리자 ID when 일반 회원 신규 가입(가입 가능 여부·학생·튜터)을 시도하면 then 모두 막힌다")
  void reservedAdminId_cannotBeClaimedBySignup() throws Exception {
    mockMvc.perform(get("/api/users/check-login-id").param("loginId", "ADMIN-F2R"))
        .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.available").value(false));
    for (String path : new String[] {"/api/users/students", "/api/users/tutors"}) {
      int st = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
          .content(json.writeValueAsString(Map.of("loginId", "admin-f2r", "password", "signup-" + UUID.randomUUID(),
              "name", "가입시도", "nickname", "가입시도", "email", "x@example.com", "phone", "01000000000"))))
          .andReturn().getResponse().getStatus();
      assertThat(st).as(path).isNotEqualTo(200).isNotEqualTo(201);
    }
    assertThat(users.findByLoginId("admin-f2r").map(u -> u.getRole()).orElse(null)).isNotEqualTo(Role.STUDENT).isNotEqualTo(Role.TUTOR);
  }
}
