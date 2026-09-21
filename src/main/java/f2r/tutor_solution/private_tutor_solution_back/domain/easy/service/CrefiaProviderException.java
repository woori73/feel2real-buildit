package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/**
 * 매통조 OpenAPI(provider) 호출 실패 — 인증 이외의 사유(타임아웃, 5xx, 파싱 실패 등).
 * 메시지에 client_secret·사업자번호 등 민감값을 절대 포함하지 않는다.
 */
public class CrefiaProviderException extends RuntimeException {

  public CrefiaProviderException(String message) {
    super(message);
  }

  public CrefiaProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
