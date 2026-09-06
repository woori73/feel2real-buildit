package f2r.tutor_solution.private_tutor_solution_back.domain.wiki.support;

/**
 * 공개 Wiki 화면(최근 가입 회원 · WIKI LIVE)에 회원을 표시할 때 쓰는 마스킹 규칙.
 *
 * <p>노출 금지: 실명 전체 · 이메일 · loginId 전체 · 전화번호 · 내부 user id · role.
 * 규칙: 실명({@code users.name}) 기준으로 <b>첫 글자 + "OO"</b> (예: "김민수" → "김OO", "Kim" → "KOO").
 * loginId 는 기본값이 loginId 그대로라 nickname 은 쓰지 않는다.
 */
public final class PublicDisplayName {

  private static final String ANONYMOUS = "익명";
  private static final String SUFFIX = "OO";

  private PublicDisplayName() {}

  /** 실명을 마스킹한 공개 표시명. null/blank 는 "익명". */
  public static String mask(String realName) {
    if (realName == null) {
      return ANONYMOUS;
    }
    String trimmed = realName.trim();
    if (trimmed.isEmpty()) {
      return ANONYMOUS;
    }
    int firstCodePoint = trimmed.codePointAt(0);
    return new String(Character.toChars(firstCodePoint)) + SUFFIX;
  }
}
