package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.QrSvg;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppLoginThrottle;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppPrincipals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 재원생 앱 작은 부품: 로그인 표시·QR·로그인 시도 제한. */
class StudentAppSmallPartsTest {

  @Test
  @DisplayName("given 앱 로그인 표시 when 읽으면 then 학생 번호(음수 userId의 반대)·코드 번호가 나오고, 일반 회원은 앱 학생이 아니다")
  void principals() {
    // given
    AuthenticatedPrincipal app = new AuthenticatedPrincipal(-7L, "app:3", "STUDENT_APP", null);
    AuthenticatedPrincipal member = new AuthenticatedPrincipal(7L, "app:3", "STUDENT", null);
    // when / then
    assertThat(StudentAppPrincipals.is(app)).isTrue();
    assertThat(StudentAppPrincipals.studentId(app)).isEqualTo(7L);
    assertThat(StudentAppPrincipals.codeId(app)).isEqualTo(3L);
    assertThat(StudentAppPrincipals.is(member)).isFalse();
    assertThat(StudentAppPrincipals.studentId(member)).isNull();
    assertThat(StudentAppPrincipals.codeId(new AuthenticatedPrincipal(-7L, "app:x", "STUDENT_APP", null))).isNull();
  }

  @Test
  @DisplayName("given 앱 주소 when QR로 그리면 then 외부 서비스 없이 SVG 한 장이 나온다")
  void qrSvg() {
    // given / when
    String svg = QrSvg.of("https://feel2real.co.kr/naesintest/app#code=AB2CDE3F");
    // then
    assertThat(svg).startsWith("<svg").contains("viewBox=\"0 0 ").contains("<path").doesNotContain("http://chart");
  }

  @Test
  @DisplayName("given 같은 IP when 10분에 11번 로그인하면 then 11번째는 막히고 10분이 지나면 다시 된다")
  void throttle() {
    // given
    StudentAppLoginThrottle t = new StudentAppLoginThrottle();
    // when
    boolean last = true;
    for (int i = 0; i < 10; i++) assertThat(t.tryAcquire("1.2.3.4", 1000 + i)).isTrue();
    last = t.tryAcquire("1.2.3.4", 2000);
    // then
    assertThat(last).isFalse();
    assertThat(t.tryAcquire("5.6.7.8", 2000)).isTrue();
    assertThat(t.tryAcquire("1.2.3.4", 1000 + 10 * 60 * 1000L + 20)).isTrue();
  }
}
