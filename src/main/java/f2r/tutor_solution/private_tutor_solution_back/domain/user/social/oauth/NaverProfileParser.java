package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * 네이버 프로필 API({@code GET https://openapi.naver.com/v1/nid/me}) 응답 파싱.
 *
 * <p>공식 문서(naver/naver-openapi-guide, ko/login/profile/profile.md, 확인 2026-09-21) 기준
 * 회원 필드는 최상위 {@code "response"} 키 아래 중첩된다 — {@code "result"}가 아니다. V1/V2가
 * {@code "result"} 키를 사용해 필드가 항상 비어있었던 것이 과거 {@code no_stable_id} 실패의 근본
 * 원인으로 추정된다(docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md §7).
 *
 * <p>{@code response.id}는 애플리케이션별 회원 고유 식별자이며 실제 네이버 로그인 ID가 아니다.
 * {@code enc_id}는 네이버 내부 전용이므로 사용하지 않는다.
 */
public final class NaverProfileParser {

  static final String RESPONSE_KEY = "response";
  static final String ID_KEY = "id";

  private NaverProfileParser() {
  }

  /**
   * {@code rawAttributes}는 네이버 프로필 API 응답 최상위 JSON({@code resultcode}/{@code message}/
   * {@code response})을 그대로 담은 맵이다. {@code response} 아래 필드만 언랩해 반환한다.
   *
   * @throws OAuth2AuthenticationException {@code response}가 없거나 객체가 아니거나,
   *     {@code response.id}가 없거나 빈 값인 경우
   */
  public static Map<String, Object> parseResponse(Map<String, Object> rawAttributes) {
    Object responseAttr = rawAttributes == null ? null : rawAttributes.get(RESPONSE_KEY);
    if (!(responseAttr instanceof Map<?, ?> rawResponseMap)) {
      throw missingResponseError();
    }

    Map<String, Object> response = new LinkedHashMap<>();
    rawResponseMap.forEach((key, value) -> response.put(String.valueOf(key), value));

    Object idAttr = response.get(ID_KEY);
    String providerUserId = idAttr == null ? null : String.valueOf(idAttr);
    if (providerUserId == null || providerUserId.isBlank()) {
      throw missingResponseIdError();
    }

    return response;
  }

  private static OAuth2AuthenticationException missingResponseError() {
    return new OAuth2AuthenticationException(new OAuth2Error(
        NaverOAuthErrorCodes.MISSING_RESPONSE,
        "Naver profile response is missing the 'response' object",
        null));
  }

  private static OAuth2AuthenticationException missingResponseIdError() {
    return new OAuth2AuthenticationException(new OAuth2Error(
        NaverOAuthErrorCodes.MISSING_RESPONSE_ID,
        "Naver profile response is missing 'response.id'",
        null));
  }
}
