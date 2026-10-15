package f2r.tutor_solution.private_tutor_solution_back.program;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReturnUrlPolicy — 복귀 URL open redirect 방지")
class ReturnUrlPolicyTest {

  @Test
  @DisplayName("given 내부 절대 경로 when 검사하면 then 허용한다")
  void allows_internal_paths() {
    assertThat(ReturnUrlPolicy.isSafeInternal("/designcraft/editor")).isTrue();
    assertThat(ReturnUrlPolicy.isSafeInternal("/designcraft/editor?a=1&b=2")).isTrue();
  }

  @Test
  @DisplayName("given 외부 스킴·프로토콜 상대·백슬래시·경로 탐색·제어문자 when 검사하면 then 모두 차단한다")
  void blocks_unsafe_urls() {
    for (String bad : new String[] {
        null, "", "  ", "https://evil.com", "HTTP://evil.com", "javascript:alert(1)", "data:text/html,x",
        "//evil.com", "/\\evil.com", "/designcraft/../admin", "designcraft/editor", "/a\nb", "/a\u0000b"}) {
      assertThat(ReturnUrlPolicy.isSafeInternal(bad)).as("bad=%s", bad).isFalse();
    }
  }
}
