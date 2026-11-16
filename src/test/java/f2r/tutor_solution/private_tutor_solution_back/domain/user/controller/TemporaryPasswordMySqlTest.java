package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.TemporaryPasswordIssueRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
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
 * 실제 MySQL·전체 보안 필터에서 관리자 임시 비밀번호의 종단 동작:
 * 발급 → 임시 비밀번호 로그인 → 다른 화면·API 차단 → 새 비밀번호 설정 → 새 비밀번호 로그인.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.admin-session-binding.enabled=true")
@DisplayName("관리자 임시 비밀번호 발급 — 로그인 후 변경 강제 종단 흐름")
class TemporaryPasswordMySqlTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired TemporaryPasswordIssueRepository issues;
  @Autowired PasswordEncoder encoder;
  private final ObjectMapper json = new ObjectMapper();

  // 테스트마다 무작위로 만든다(고정된 비밀번호 문자열을 코드에 두지 않는다).
  private final String suffix = UUID.randomUUID().toString().substring(0, 8);
  private final String adminId = "t-admin-" + suffix;
  private final String adminPw = "adm-" + UUID.randomUUID();
  private final String studentId = "test-student-" + suffix;
  private final String oldStudentPw = "old-" + UUID.randomUUID();
  private final String newStudentPw = "new-" + UUID.randomUUID();
  private Long studentUserId;
  private Long adminUserId;

  @BeforeEach
  void seed() {
    adminUserId = users.save(User.builder().loginId(adminId).password(encoder.encode(adminPw)).name("관리자")
        .nickname("관리자").role(Role.ADMIN).build()).getUserId();
    studentUserId = users.save(User.builder().loginId(studentId).password(encoder.encode(oldStudentPw)).name("학생")
        .nickname("도도").role(Role.STUDENT).build()).getUserId();
    users.flush();
  }

  private MvcResult loginResult(String id, String pw) throws Exception {
    return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("loginId", id, "password", pw)))).andReturn();
  }

  private JsonNode issue(Cookie admin, Long userId) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/admin/members/" + userId + "/temporary-password").cookie(admin))
        .andExpect(status().isOk()).andReturn();
    assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("given 이메일 없는 학생 when 관리자가 발급·학생이 로그인·변경 then 변경 전에는 다른 화면·API가 막히고 변경 후 새 비밀번호로만 로그인된다")
  void issue_login_forcedChange_relogin() throws Exception {
    // given
    Cookie admin = loginResult(adminId, adminPw).getResponse().getCookie("ACCESS_TOKEN");

    // when: 발급
    JsonNode issued = issue(admin, studentUserId);
    String temp = issued.get("temporaryPassword").asText();

    // then: 무작위 10자, 기록에는 누가·누구에게만 남는다
    assertThat(temp).hasSize(10).doesNotContainPattern("[0O1lIo]");
    assertThat(issued.get("loginId").asText()).isEqualTo(studentId);
    assertThat(issues.findAll()).anySatisfy(i -> {
      assertThat(i.getTargetUserId()).isEqualTo(studentUserId);
      assertThat(i.getIssuedByUserId()).isEqualTo(adminUserId);
      assertThat(i.getExpiresAt()).isAfter(i.getIssuedAt().plusHours(71));
    });
    // 예전 비밀번호는 더 이상 쓸 수 없다
    assertThat(loginResult(studentId, oldStudentPw).getResponse().getStatus()).isEqualTo(400);

    // when: 임시 비밀번호로 로그인
    MvcResult tempLogin = loginResult(studentId, temp);
    assertThat(tempLogin.getResponse().getStatus()).isEqualTo(200);
    assertThat(json.readTree(tempLogin.getResponse().getContentAsString()).get("passwordChangeRequired").asBoolean()).isTrue();
    Cookie tempSession = tempLogin.getResponse().getCookie("ACCESS_TOKEN");

    // then: 화면은 설정 화면으로, API는 403(안내 헤더), 설정 화면·me는 열린다
    MvcResult page = mockMvc.perform(get("/stdt_dashboard").cookie(tempSession)).andReturn();
    assertThat(page.getResponse().getStatus()).isEqualTo(302);
    assertThat(page.getResponse().getRedirectedUrl()).isEqualTo("/account/password-change");
    MvcResult api = mockMvc.perform(get("/api/me").cookie(tempSession)).andReturn();
    assertThat(api.getResponse().getStatus()).isEqualTo(403);
    assertThat(api.getResponse().getHeader("X-Password-Change-Required")).isEqualTo("true");
    assertThat(mockMvc.perform(get("/account/password-change").cookie(tempSession)).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(mockMvc.perform(get("/api/auth/me").cookie(tempSession)).andReturn().getResponse().getStatus()).isEqualTo(200);

    // when: 새 비밀번호 설정
    MvcResult changed = mockMvc.perform(post("/api/auth/password-change/temporary").cookie(tempSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("newPassword", newStudentPw, "confirmPassword", newStudentPw))))
        .andReturn();

    // then: 일반 쿠키로 바뀌어 다른 화면을 쓸 수 있고, 임시 비밀번호는 끝나고 새 비밀번호로 로그인된다
    assertThat(changed.getResponse().getStatus()).isEqualTo(200);
    Cookie normal = changed.getResponse().getCookie("ACCESS_TOKEN");
    assertThat(mockMvc.perform(get("/stdt_dashboard").cookie(normal)).andReturn().getResponse().getStatus()).isEqualTo(200);
    assertThat(loginResult(studentId, temp).getResponse().getStatus()).isEqualTo(400);
    MvcResult relogin = loginResult(studentId, newStudentPw);
    assertThat(relogin.getResponse().getStatus()).isEqualTo(200);
    assertThat(json.readTree(relogin.getResponse().getContentAsString()).get("passwordChangeRequired").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("given 유효기간이 지난 임시 비밀번호 when 로그인 then 400과 재발급 안내")
  void expired_temporaryPassword_rejected() throws Exception {
    // given
    String temp = "Ab3" + UUID.randomUUID().toString().substring(0, 7);
    User student = users.findById(studentUserId).orElseThrow();
    student.issueTemporaryPassword(encoder.encode(temp), LocalDateTime.now().minusMinutes(1));
    users.flush();

    // when
    MvcResult r = loginResult(studentId, temp);

    // then
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("유효기간");
  }

  @Test
  @DisplayName("given 학생·관리자 계정 when 학생이 발급 API를 부르거나 관리자 계정에 발급 then 403·400")
  void issue_permissions() throws Exception {
    // given
    Cookie student = loginResult(studentId, oldStudentPw).getResponse().getCookie("ACCESS_TOKEN");
    Cookie admin = loginResult(adminId, adminPw).getResponse().getCookie("ACCESS_TOKEN");

    // when & then
    assertThat(mockMvc.perform(post("/api/admin/members/" + studentUserId + "/temporary-password").cookie(student))
        .andReturn().getResponse().getStatus()).isEqualTo(403);
    assertThat(mockMvc.perform(post("/api/admin/members/" + adminUserId + "/temporary-password").cookie(admin))
        .andReturn().getResponse().getStatus()).isEqualTo(400);
  }
}
