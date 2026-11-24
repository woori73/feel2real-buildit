package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** 재원생 앱 쿠키 필터: 학생 앱 API·문항 그림에서만, 코드가 아직 쓰일 때만 ROLE_STUDENT_APP(무DB). */
class StudentAppTokenFilterTest {

  private final JwtTokenProvider tokens = new JwtTokenProvider(
      new JwtProperties("test-jwt-secret-must-be-at-least-32-bytes-long-for-hs256", 3600L, "ACCESS_TOKEN", false, null));
  private final StudentAppCodeService codes = mock(StudentAppCodeService.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<StudentAppCodeService> provider = mock(ObjectProvider.class);
  private final StudentAppTokenFilter filter = new StudentAppTokenFilter(tokens, provider);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private String token(long studentId, long codeId) {
    return tokens.issue(-studentId, "app:" + codeId, "STUDENT_APP");
  }

  private List<Authentication> run(String uri, String cookie) throws Exception {
    when(provider.getIfAvailable()).thenReturn(codes);
    MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
    req.setRequestURI(uri);
    if (cookie != null) req.setCookies(new Cookie(StudentAppTokenFilter.COOKIE, cookie));
    List<Authentication> seen = new ArrayList<>();
    filter.doFilter(req, new MockHttpServletResponse(), (rq, rs) -> seen.add(SecurityContextHolder.getContext().getAuthentication()));
    return seen;
  }

  private void active(long codeId, long studentId) {
    StudentAppCode c = new StudentAppCode(1L, studentId, "h".repeat(64), "QT", 1L, LocalDateTime.now());
    ReflectionTestUtils.setField(c, "codeId", codeId);
    when(codes.activeSession(codeId, studentId)).thenReturn(Optional.of(c));
  }

  @Test
  @DisplayName("given 사용 중 코드의 쿠키 when 학생 앱 API를 부르면 then ROLE_STUDENT_APP 학생(userId=-학생번호)으로 인증된다")
  void validCookie_onAppApi_authenticates() throws Exception {
    // given
    active(3L, 7L);
    // when
    List<Authentication> seen = run("/api/naesintest/app/me", token(7, 3));
    // then
    assertThat(seen.get(0).getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STUDENT_APP");
    assertThat(((AuthenticatedPrincipal) seen.get(0).getPrincipal()).userId()).isEqualTo(-7L);
  }

  @Test
  @DisplayName("given 폐기된 코드의 쿠키 when 학생 앱 API를 부르면 then 인증 없음")
  void revokedCode_noAuth() throws Exception {
    // given
    when(codes.activeSession(3L, 7L)).thenReturn(Optional.empty());
    // when / then
    assertThat(run("/api/naesintest/app/me", token(7, 3)).get(0)).isNull();
  }

  @Test
  @DisplayName("given 같은 쿠키 when 관리자·일반 API를 부르면 then 이 쿠키로는 아무 권한도 생기지 않는다")
  void otherPaths_ignored() throws Exception {
    // given
    active(3L, 7L);
    // when / then
    assertThat(run("/api/admin/easy/student-app/students", token(7, 3)).get(0)).isNull();
    assertThat(run("/api/testprep/attempts", token(7, 3)).get(0)).isNull();
    assertThat(run("/dashboard/exam-results", token(7, 3)).get(0)).isNull();
  }

  @Test
  @DisplayName("given 일반 회원 JWT(학생 앱 역할 아님) when 앱 쿠키 자리에 넣으면 then 인증 없음")
  void nonAppToken_rejected() throws Exception {
    // given
    String member = tokens.issue(5L, "member", "STUDENT");
    // when / then
    assertThat(run("/api/naesintest/app/me", member).get(0)).isNull();
    assertThat(run("/api/naesintest/app/me", "garbage").get(0)).isNull();
  }

  @Test
  @DisplayName("given 네이버 로그인만 있는 휴대폰 when 학생 앱 API를 부르면 then 인증을 비워 401이 나게 한다(앱은 코드 학생만)")
  void otherLoginOnAppApi_cleared() throws Exception {
    // given
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedPrincipal(5L, "m", "STUDENT", null), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    // when / then
    assertThat(run("/api/naesintest/app/me", null).get(0)).isNull();
  }

  @Test
  @DisplayName("given 다른 로그인이 없을 때 when 문항 그림을 열면 then 앱 쿠키로 인증, 다른 로그인이 있으면 그대로 둔다")
  void images_onlyWhenNoOtherLogin() throws Exception {
    // given
    active(3L, 7L);
    // when
    List<Authentication> alone = run("/naesin/apm2/r1/q1.webp", token(7, 3));
    Authentication member = new UsernamePasswordAuthenticationToken(
        new AuthenticatedPrincipal(5L, "m", "STUDENT", null), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    SecurityContextHolder.getContext().setAuthentication(member);
    List<Authentication> withMember = run("/naesin/apm2/r1/q1.webp", token(7, 3));
    // then
    assertThat(alone.get(0).getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STUDENT_APP");
    assertThat(withMember.get(0)).isSameAs(member);
  }

  @Test
  @DisplayName("given 다른 로그인이 없을 때 when TOEIC 사진(/testprep/img)을 열면 then 앱 쿠키로 인증되고, 응시 화면 주소(/testprep/run)에는 권한을 주지 않는다")
  void testprepImage_onlyImagesGetAppRole() throws Exception {
    // given
    active(3, 7);
    // when
    Authentication image = run("/testprep/img/toeic_full01_p1_01.webp", token(7, 3)).get(0);
    Authentication page = run("/testprep/run", token(7, 3)).get(0);
    // then
    assertThat(image).isNotNull();
    assertThat(image.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STUDENT_APP");
    assertThat(page).isNull();
  }
}
