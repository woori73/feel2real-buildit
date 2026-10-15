package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Academy handle(영문 서브도메인 식별자) 정책. 형식 검증 + 예약어 blocklist.
 *
 * <p>형식: 소문자/숫자/하이픈, 앞뒤 하이픈 금지, 3~30자(정규식 기준). 예약어는 서브도메인 충돌
 * 방지를 위해 별도 차단한다. DB unique 제약이 최종 유일성을 보장하고, 여기선 형식·예약어만 본다.
 */
public final class HandlePolicy {

  // 3~30자 강제: 첫/끝 글자 [a-z0-9] + 중간 1~28자. (옵션 그룹이면 1글자 handle이 통과해버림)
  private static final Pattern PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$");

  // "lms"는 플랫폼 차원의 lms.{base-domain}을 남겨두기 위해 막는다. 특정 학원이 선점하면
  // 그 학원 LMS 주소가 /lms/lms/cart 꼴이 되고 플랫폼 서브도메인을 영영 못 쓴다.
  private static final Set<String> RESERVED = Set.of(
      "www", "api", "admin", "mail", "static", "assets", "app", "cdn", "blog", "shop",
      "m", "dev", "stg", "test", "lms");

  private HandlePolicy() {
  }

  public static boolean isValidFormat(String handle) {
    return handle != null && PATTERN.matcher(handle).matches();
  }

  public static boolean isReserved(String handle) {
    return handle != null && RESERVED.contains(handle);
  }

  /** 형식 통과 + 예약어 아님. 중복(유니크)은 별도로 DB에서 확인한다. */
  public static boolean isAcceptable(String handle) {
    return isValidFormat(handle) && !isReserved(handle);
  }

  /** 형식/예약어 위반 시 {@link IllegalArgumentException}. 유효하면 조용히 통과. */
  public static void validate(String handle) {
    if (handle == null || handle.isBlank()) {
      throw new IllegalArgumentException("handle(영문 주소)은 필수입니다.");
    }
    if (!isValidFormat(handle)) {
      throw new IllegalArgumentException(
          "handle은 3~30자의 소문자/숫자/하이픈만 사용할 수 있고, 앞뒤에 하이픈을 둘 수 없습니다.");
    }
    if (isReserved(handle)) {
      throw new IllegalArgumentException("예약된 handle은 사용할 수 없습니다.");
    }
  }
}
