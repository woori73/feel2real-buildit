package f2r.tutor_solution.private_tutor_solution_back.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.PasswordResetToken;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.PasswordResetTokenRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig + JwtAuthenticationFilter + AuthController가 함께 동작하는 end-to-end 흐름.
 * 비밀번호 검증 → 쿠키 발급 → 쿠키로 /me 호출 → 로그아웃 → 다시 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Auth Flow 통합 테스트")
class AuthFlowIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository userRepository;
  @Autowired PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired PasswordEncoder passwordEncoder;

  // Spring Boot 4.0의 starter-web 자동구성 변화로 ObjectMapper가 컨텍스트에 자동 등록되지 않아
  // 테스트에서는 단순 인스턴스를 직접 만들어 쓴다 (직렬화 옵션 default로 충분).
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String LOGIN_ID = "jwt-test-user";
  private static final String PASSWORD = "jwt-test-pw-1234";

  @BeforeEach
  void seedUser() {
    userRepository.findByLoginId(LOGIN_ID).ifPresent(userRepository::delete);
    userRepository.flush();
    userRepository.save(User.builder()
        .loginId(LOGIN_ID)
        .password(passwordEncoder.encode(PASSWORD))
        .name("JWT테스트")
        .nickname("JWT테스트")
        .email("jwt-reset@example.com")
        .role(Role.STUDENT)
        .build());
  }

  @Test
  @DisplayName("올바른 자격증명으로 로그인하면 HttpOnly 쿠키가 발급된다")
  void loginIssuesHttpOnlyCookie() throws Exception {
    // given
    String body = objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "password", PASSWORD));

    // when & then
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loginId").value(LOGIN_ID))
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(cookie().exists("ACCESS_TOKEN"))
        .andExpect(cookie().httpOnly("ACCESS_TOKEN", true))
        .andExpect(header().string("Set-Cookie",
            org.hamcrest.Matchers.containsString("SameSite=Lax")));
  }

  @Test
  @DisplayName("/me는 인증 쿠키가 있을 때만 본인 정보를 반환한다")
  void meRequiresCookie() throws Exception {
    // given — 인증 없이 호출하면 401
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized());

    // when — 로그인으로 쿠키 발급
    String body = objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "password", PASSWORD));
    MvcResult login = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andReturn();
    Cookie token = login.getResponse().getCookie("ACCESS_TOKEN");
    assertThat(token).isNotNull();

    // then — 같은 쿠키 들고 /me 재호출하면 200
    mockMvc.perform(get("/api/auth/me").cookie(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loginId").value(LOGIN_ID));
  }

  @Test
  @DisplayName("위변조된 쿠키는 인증 미설정 상태로 통과 → 보호 엔드포인트에서 401")
  void tamperedCookieRejected() throws Exception {
    // given — 형식만 비슷한 쓰레기 값
    Cookie evil = new Cookie("ACCESS_TOKEN", "totally.fake.jwt");

    // when & then
    mockMvc.perform(get("/api/auth/me").cookie(evil))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("로그아웃 호출 시 Max-Age=0 쿠키로 덮인다")
  void logoutClearsCookie() throws Exception {
    // when & then
    mockMvc.perform(post("/api/auth/logout"))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie",
            org.hamcrest.Matchers.containsString("Max-Age=0")));
  }

  @Test
  @DisplayName("V2 마이그레이션의 admin-f2r 계정으로 로그인하면 ADMIN 권한 쿠키가 발급된다")
  void prodAdminCanLogin() throws Exception {
    // given — V2 마이그레이션이 admin-f2r/admin-mochi-96을 INSERT했어야 한다.
    String body = objectMapper.writeValueAsString(
        Map.of("loginId", "admin-f2r", "password", "admin-mochi-96"));

    // when & then
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(cookie().exists("ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("틀린 비밀번호는 400으로 떨어진다 (쿠키 발급 안 됨)")
  void wrongPasswordRejected() throws Exception {
    // given
    String body = objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "password", "wrong"));

    // when & then
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().is4xxClientError())
        .andExpect(cookie().doesNotExist("ACCESS_TOKEN"));
  }



  @Test
  @DisplayName("아이디 찾기는 이메일에 연결된 아이디와 역할을 반환한다")
  void findLoginIdsReturnsAccountsByEmail() throws Exception {
    // given
    String body = objectMapper.writeValueAsString(Map.of("email", "jwt-reset@example.com"));

    // when & then
    mockMvc.perform(post("/api/auth/login-id/find")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("jwt-reset@example.com"))
        .andExpect(jsonPath("$.accounts[0].loginId").value(LOGIN_ID))
        .andExpect(jsonPath("$.accounts[0].roleLabel").value("학생"));
  }

  @Test
  @DisplayName("비밀번호 재설정 요청은 public이며 가입 이메일이면 임시 비밀번호로 변경한다")
  void passwordResetRequestAppliesTemporaryPassword() throws Exception {
    // given
    long before = passwordResetTokenRepository.count();
    String body = objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "email", "jwt-reset@example.com"));

    // when & then
    mockMvc.perform(post("/api/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("jwt-reset@example.com"))
        .andExpect(jsonPath("$.temporaryPassword").value("reset1234"));
    assertThat(passwordResetTokenRepository.count()).isEqualTo(before);
  }

  @Test
  @DisplayName("비밀번호 재설정 요청은 이메일이 비어 있으면 누락 메시지를 반환한다")
  void passwordResetRequestRejectsBlankEmailAsMissingEmail() throws Exception {
    // given
    String body = objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "email", "   "));

    // when & then
    mockMvc.perform(post("/api/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string("이메일을 입력해 주세요."));
  }

  @Test
  @DisplayName("비밀번호 재설정 요청은 없는 이메일이면 400과 명시 메시지를 반환한다")
  void passwordResetRequestRejectsUnknownEmail() throws Exception {
    // given
    String body = objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "email", "missing@example.com"));

    // when & then
    mockMvc.perform(post("/api/auth/password-reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string("아이디와 이메일이 일치하는 계정이 없습니다."));
  }

  @Test
  @DisplayName("비밀번호 재설정 확정은 유효 토큰으로 비밀번호를 변경하고 토큰을 재사용 불가 처리한다")
  void passwordResetConfirmChangesPassword() throws Exception {
    // given
    User user = userRepository.findByLoginId(LOGIN_ID).orElseThrow();
    passwordResetTokenRepository.save(new PasswordResetToken(
        user,
        sha256("known-reset-token"),
        LocalDateTime.now().plusMinutes(30)));
    String body = objectMapper.writeValueAsString(
        Map.of("token", "known-reset-token", "newPassword", "changed-password"));

    // when & then
    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "loginId", LOGIN_ID,
                "password", "changed-password"))))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("ACCESS_TOKEN"));

    mockMvc.perform(post("/api/auth/password-reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string("이미 사용된 비밀번호 재설정 링크입니다."));
  }

  private String sha256(String value) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("/api/tutor/** 는 인증 없으면 401로 차단된다")
  void tutorApiRequiresAuthentication() throws Exception {
    // given

    // when & then
    mockMvc.perform(post("/api/tutor/academy"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("/api/tutor/** 는 STUDENT 토큰이면 403으로 차단된다")
  void tutorApiRejectsStudentRole() throws Exception {
    // given
    String body = objectMapper.writeValueAsString(Map.of("loginId", LOGIN_ID, "password", PASSWORD));
    MvcResult login = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andReturn();
    Cookie token = login.getResponse().getCookie("ACCESS_TOKEN");
    assertThat(token).isNotNull();

    // when & then
    mockMvc.perform(post("/api/tutor/academy").cookie(token))
        .andExpect(status().isForbidden());
  }
}
