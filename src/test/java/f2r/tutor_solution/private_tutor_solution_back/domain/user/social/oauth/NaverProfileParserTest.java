package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * 네이버 프로필 JSON 파싱 검증. 공식 문서(확인 2026-09-21) 기준 필드는 "response" 키 아래
 * 중첩되며, "result"는 V1/V2가 잘못 사용했던 키다(docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md §7).
 */
@DisplayName("NaverProfileParser")
class NaverProfileParserTest {

  @Test
  @DisplayName("response.id가 있으면 response 객체를 그대로 반환한다")
  void parsesResponseId() {
    Map<String, Object> raw = Map.of(
        "resultcode", "00",
        "message", "success",
        "response", Map.of(
            "id", "naver-stable-id-123",
            "email", "user@example.com",
            "name", "홍길동"));

    Map<String, Object> response = NaverProfileParser.parseResponse(raw);

    assertThat(response.get("id")).isEqualTo("naver-stable-id-123");
    assertThat(response.get("email")).isEqualTo("user@example.com");
  }

  @Test
  @DisplayName("email/name이 없어도 response.id만 있으면 성공한다 (필수 identity는 id뿐)")
  void succeedsWithOnlyId() {
    Map<String, Object> raw = Map.of(
        "resultcode", "00",
        "message", "success",
        "response", Map.of("id", "naver-stable-id-456"));

    Map<String, Object> response = NaverProfileParser.parseResponse(raw);

    assertThat(response.get("id")).isEqualTo("naver-stable-id-456");
  }

  @Test
  @DisplayName("'result' 키(V1/V2가 잘못 쓴 키)만 있고 'response'가 없으면 provider id로 인정하지 않는다")
  void rejectsResultKeyInsteadOfResponse() {
    Map<String, Object> raw = Map.of(
        "resultcode", "00",
        "message", "success",
        "result", Map.of("id", "should-not-be-used"));

    assertThatThrownBy(() -> NaverProfileParser.parseResponse(raw))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
            .isEqualTo(NaverOAuthErrorCodes.MISSING_RESPONSE));
  }

  @Test
  @DisplayName("'response' 키 자체가 없으면 controlled failure로 처리한다")
  void rejectsMissingResponseKey() {
    Map<String, Object> raw = Map.of("resultcode", "00", "message", "success");

    assertThatThrownBy(() -> NaverProfileParser.parseResponse(raw))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
            .isEqualTo(NaverOAuthErrorCodes.MISSING_RESPONSE));
  }

  @Test
  @DisplayName("'response'는 있지만 'response.id'가 없으면 controlled failure로 처리한다")
  void rejectsMissingResponseId() {
    Map<String, Object> raw = Map.of(
        "resultcode", "00",
        "message", "success",
        "response", Map.of("email", "user@example.com"));

    assertThatThrownBy(() -> NaverProfileParser.parseResponse(raw))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
            .isEqualTo(NaverOAuthErrorCodes.MISSING_RESPONSE_ID));
  }

  @Test
  @DisplayName("'response.id'가 빈 문자열이면 controlled failure로 처리한다")
  void rejectsBlankResponseId() {
    Map<String, Object> raw = Map.of(
        "resultcode", "00",
        "message", "success",
        "response", Map.of("id", "  "));

    assertThatThrownBy(() -> NaverProfileParser.parseResponse(raw))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
            .isEqualTo(NaverOAuthErrorCodes.MISSING_RESPONSE_ID));
  }

  @Test
  @DisplayName("'response'가 객체(Map)가 아니면 controlled failure로 처리한다")
  void rejectsNonMapResponse() {
    Map<String, Object> raw = Map.of("resultcode", "00", "message", "success", "response", "not-an-object");

    assertThatThrownBy(() -> NaverProfileParser.parseResponse(raw))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
            .isEqualTo(NaverOAuthErrorCodes.MISSING_RESPONSE));
  }
}
