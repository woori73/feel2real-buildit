package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/**
 * 매통조 OpenAPI 이용기관 자격증명(client_id/secret) 제공자.
 * 자격증명은 DB·Git·로그에 절대 저장하지 않으며, 운영에서는 프로세스 환경변수
 * ({@code CREFIA_CLIENT_ID}/{@code CREFIA_CLIENT_SECRET}, {@code /etc/f2r/env} 패턴)로만
 * 주입한다. 이용기관 승인 전에는 값이 없어 어댑터가 명시적으로 차단된다.
 */
public interface CrefiaCredentialProvider {

  /** 이용기관 자격증명. 미발급 상태면 empty 성격의 null 필드로 반환한다. */
  Credentials credentials();

  /** client_id/secret 쌍. toString에 값을 노출하지 않는다. */
  final class Credentials {
    private final String clientId;
    private final String clientSecret;

    public Credentials(String clientId, String clientSecret) {
      this.clientId = clientId;
      this.clientSecret = clientSecret;
    }

    public String clientId() {
      return clientId;
    }

    public String clientSecret() {
      return clientSecret;
    }

    public boolean present() {
      return clientId != null && !clientId.isBlank()
          && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
      return "CrefiaCredentials[present=" + present() + "]";
    }
  }

  /** 운영 기본값: 환경변수에서 읽는다. */
  static CrefiaCredentialProvider env() {
    return () -> new Credentials(
        System.getenv("CREFIA_CLIENT_ID"),
        System.getenv("CREFIA_CLIENT_SECRET"));
  }
}
