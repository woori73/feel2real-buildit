package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/**
 * CODEF 이용기관 자격증명(client_id/secret) 제공자. 값은 DB·Git·로그에 저장하지 않으며 운영에서는
 * 프로세스 환경변수({@code CODEF_CLIENT_ID}/{@code CODEF_CLIENT_SECRET}, {@code /etc/f2r/env} 패턴)로만
 * 주입한다. 계약 전에는 값이 없어 은행 어댑터가 명시적으로 차단된다.
 */
public interface CodefCredentialProvider {

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
      return "CodefCredentials[present=" + present() + "]";
    }
  }

  static CodefCredentialProvider env() {
    return () -> new Credentials(System.getenv("CODEF_CLIENT_ID"),
        System.getenv("CODEF_CLIENT_SECRET"));
  }
}
