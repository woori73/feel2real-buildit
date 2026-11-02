package f2r.tutor_solution.private_tutor_solution_back.domain.sns.instagram;

/**
 * Instagram API with Instagram Login(graph.instagram.com) 호출 경계. 테스트에서는 가짜로 바꾼다.
 * 모든 예외 메시지에 토큰·비밀값을 싣지 않는다.
 */
public interface InstagramApi {

  record TokenResult(String accessToken, String userId, long expiresInSeconds) {}

  record Profile(String userId, String username) {}

  class InstagramApiException extends RuntimeException {
    public InstagramApiException(String message) {
      super(message);
    }
  }

  String authorizeUrl(String state);

  /** OAuth code → 짧은 토큰(1시간). */
  TokenResult exchangeCode(String code);

  /** 짧은 토큰 → 긴 토큰(60일). */
  TokenResult exchangeLongLived(String shortToken);

  /** 긴 토큰 갱신(발급 24시간 뒤부터, 만료 전). */
  TokenResult refresh(String longToken);

  Profile me(String token);

  /** 이미지 컨테이너 만들기 → creation id. */
  String createImageContainer(String igUserId, String token, String imageUrl, String caption);

  /** FINISHED / IN_PROGRESS / ERROR / EXPIRED / PUBLISHED. */
  String containerStatus(String containerId, String token);

  /** 게시 → 게시물(media) id. */
  String publish(String igUserId, String token, String creationId);
}
