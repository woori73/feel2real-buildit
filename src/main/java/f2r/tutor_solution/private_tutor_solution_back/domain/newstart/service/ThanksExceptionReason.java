package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

/** 감사회원 셀프 인증 예외 사유 코드와 운영자용 한글 라벨. */
public final class ThanksExceptionReason {

  public static final String EMAIL_MISSING = "EMAIL_MISSING";
  public static final String EMAIL_NOT_NAVER = "EMAIL_NOT_NAVER";
  public static final String AMBIGUOUS_CANDIDATES = "AMBIGUOUS_CANDIDATES";
  public static final String ROSTER_ALREADY_LINKED = "ROSTER_ALREADY_LINKED";
  public static final String DUPLICATE_OAUTH_LINK = "DUPLICATE_OAUTH_LINK";
  public static final String REPEATED_FAILURE = "REPEATED_FAILURE";
  public static final String CONCURRENT_CONFLICT = "CONCURRENT_CONFLICT";

  private ThanksExceptionReason() {
  }

  public static String label(String code) {
    if (code == null) return "기타";
    return switch (code) {
      case EMAIL_MISSING -> "OAuth 이메일 없음";
      case EMAIL_NOT_NAVER -> "@naver.com 이메일이 아님";
      case AMBIGUOUS_CANDIDATES -> "닉네임 일치 후보가 2명 이상";
      case ROSTER_ALREADY_LINKED -> "이미 다른 계정과 연결된 명단";
      case DUPLICATE_OAUTH_LINK -> "이미 다른 명단과 연결된 계정";
      case REPEATED_FAILURE -> "인증 반복 실패";
      case CONCURRENT_CONFLICT -> "동시 연결 충돌";
      default -> "기타";
    };
  }
}
