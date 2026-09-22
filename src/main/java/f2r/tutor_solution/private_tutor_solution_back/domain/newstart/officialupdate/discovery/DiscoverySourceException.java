package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

/**
 * 공식 출처 discovery 실패를 Service 레이어에 알리기 위한 예외.
 * HTTP 200 이라도 "페이지에 항목이 있는데 parse 0건" 이면 성공으로 처리하지 않는다.
 */
public class DiscoverySourceException extends RuntimeException {

  public enum Reason {
    FETCH_FAILED,
    PARSE_EMPTY_WITH_ITEMS,
    NOT_IMPLEMENTED
  }

  private final Reason reason;

  public DiscoverySourceException(String sourceName, Reason reason, String message) {
    super("[" + sourceName + "] " + reason + ": " + message);
    this.reason = reason;
  }

  public DiscoverySourceException(String sourceName, Reason reason, String message, Throwable cause) {
    super("[" + sourceName + "] " + reason + ": " + message, cause);
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
