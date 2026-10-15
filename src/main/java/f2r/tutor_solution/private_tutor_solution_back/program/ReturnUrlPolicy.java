package f2r.tutor_solution.private_tutor_solution_back.program;

/**
 * 로그인 후 복귀 URL 검증. {@code NaverOAuth2LoginSuccessHandler}의 내부 경로 규칙과 같은 기준이다
 * (open redirect 방지: 외부 스킴, {@code //}, 백슬래시, {@code ..}, 제어문자 차단).
 */
public final class ReturnUrlPolicy {

  private ReturnUrlPolicy() {}

  public static boolean isSafeInternal(String url) {
    if (url == null || url.isBlank()) return false;
    String lower = url.toLowerCase();
    if (lower.startsWith("http://") || lower.startsWith("https://")
        || lower.startsWith("javascript:") || lower.startsWith("data:")) {
      return false;
    }
    if (!url.startsWith("/") || url.startsWith("//")) return false;
    if (url.contains("..") || url.contains("\\")) return false;
    for (int i = 0; i < url.length(); i++) {
      char c = url.charAt(i);
      if (c < 0x20 || c == 0x7f) return false;
    }
    return true;
  }
}
