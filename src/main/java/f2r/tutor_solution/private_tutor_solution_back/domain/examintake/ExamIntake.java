package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Non-JPA request records: JDBC adapter and proposed schema are deliberately opt-in. */
public final class ExamIntake {
  private ExamIntake() {}

  public static final String CONSENT = "exam-intake-20261030-v1";

  public static final String PRIVACY = "exam-privacy-20261030-v1";
  public static final String TERMS = "exam-terms-20261030-v1";
  static final Set<String> AGREEMENTS =
      Set.of(
          "ageBand",
          "guardianConsentId",
          "privacyVersion",
          "termsVersion",
          "termsAccepted",
          "optionalConsent");

  public enum Kind {
    INQUIRY,
    APPLICATION
  }

  public enum State {
    RECEIVED,
    AWAITING_DEPOSIT,
    CONFIRMATION_REQUESTED,
    CONFIRMED,
    PRODUCTION,
    DELIVERED,
    COMPLETE,
    CANCELLED
  }

  public record Request(
      String id,
      long ownerId,
      Kind kind,
      State state,
      Map<String, String> details,
      Map<String, Object> offer,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record Create(
      Kind kind,
      Map<String, String> details,
      boolean consent,
      String consentVersion,
      String idempotencyKey) {}

  public record Document(
      String id,
      String requestId,
      String originalName,
      String contentType,
      long sizeBytes,
      String sha256,
      String storageName,
      String scanState,
      boolean hidden) {}

  public record Action(
      String id,
      String requestId,
      long actorId,
      String kind,
      String body,
      String state,
      Integer amountWon,
      Instant createdAt) {}

  public record Bank(
      String bankName,
      String accountNumber,
      String accountHolder,
      boolean approved,
      long version) {}

  public static long user(AuthenticatedPrincipal p) {
    if (p == null || p.userId() == null) throw error(401, "로그인이 필요합니다.");
    return p.userId();
  }

  public static boolean isAdmin(AuthenticatedPrincipal p) {
    return p != null && "ADMIN".equals(p.role());
  }

  public static void admin(AuthenticatedPrincipal p) {
    user(p);
    if (!isAdmin(p)) throw error(403, "관리자만 처리할 수 있습니다.");
  }

  public static void owner(AuthenticatedPrincipal p, Request r) {
    if (user(p) != r.ownerId() && !isAdmin(p)) throw error(404, "접수 내역을 찾을 수 없습니다.");
  }

  public static String text(String value, String field, int min, int max) {
    String v = value == null ? "" : value.strip();
    if (v.length() < min
        || v.length() > max
        || v.chars().anyMatch(c -> c == 0 || (c < 32 && c != '\n' && c != '\r' && c != '\t')))
      throw error(400, field + " 입력을 확인해 주세요.");
    return v;
  }

  public static Map<String, String> validate(Create in) {
    if (in == null || in.kind() == null || !in.consent() || !CONSENT.equals(in.consentVersion()))
      throw error(400, "개인정보 처리 안내를 확인하고 동의해 주세요.");
    text(in.idempotencyKey(), "접수 키", 16, 80);
    if (!in.idempotencyKey().matches("[A-Za-z0-9_-]+")) throw error(400, "접수 키가 올바르지 않습니다.");
    var src = in.details() == null ? Map.<String, String>of() : in.details();
    if (!PRIVACY.equals(src.get("privacyVersion"))) throw error(400, "최신 개인정보 처리방침에 동의해 주세요.");
    if (in.kind() == Kind.APPLICATION
        && (!"true".equals(src.get("termsAccepted")) || !TERMS.equals(src.get("termsVersion"))))
      throw error(400, "서비스 이용·환불 약관을 확인하고 별도로 동의해 주세요.");
    var fields = new LinkedHashMap<>(validateFields(in.kind(), src));
    if ((!fields.get("requirements").isEmpty()
            || (in.kind() == Kind.INQUIRY
                && (!fields.get("school").isEmpty() || !fields.get("grade").isEmpty())))
        && !"true".equals(src.get("optionalConsent")))
      throw error(400, "선택 정보 동의 또는 선택 정보 삭제가 필요합니다.");
    for (String key : AGREEMENTS) fields.put(key, text(src.get(key), key, 0, 80));
    return Collections.unmodifiableMap(fields);
  }

  static Map<String, String> validateFields(Kind kind, Map<String, String> src) {
    if (src == null) src = Map.of();
    var fields = new LinkedHashMap<String, String>();
    for (String f :
        List.of(
            "name",
            "school",
            "grade",
            "contactMethod",
            "contact",
            "examDate",
            "scope",
            "pastPapers",
            "questionTypes",
            "difficulty",
            "format",
            "requirements",
            "inquiryType",
            "message"))
      fields.put(
          f,
          text(
              src.get(f),
              f,
              0,
              Set.of("scope", "requirements", "message").contains(f) ? 2000 : 120));
    for (String f : List.of("name", "contactMethod", "contact")) text(fields.get(f), f, 1, 120);
    if (!Set.of("EMAIL", "PHONE", "KAKAO").contains(fields.get("contactMethod")))
      throw error(400, "연락 방법을 선택해 주세요.");
    if (fields.get("contactMethod").equals("EMAIL")
        && !fields.get("contact").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw error(400, "이메일 주소를 확인해 주세요.");
    if (fields.get("contactMethod").equals("PHONE")
        && !fields.get("contact").matches("[0-9+() -]{7,25}")) throw error(400, "전화번호를 확인해 주세요.");
    if (kind == Kind.INQUIRY) {
      text(fields.get("inquiryType"), "문의 유형", 1, 120);
      text(fields.get("message"), "문의 내용", 1, 2000);
    } else {
      for (String f : List.of("school", "grade", "format"))
        text(fields.get(f), f, 1, 120);
    }
    return Collections.unmodifiableMap(fields);
  }

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String hash(String text) {
    return hash(text.getBytes(StandardCharsets.UTF_8));
  }

  public static ResponseStatusException error(int status, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(status), message);
  }

  public static boolean beforeProduction(Request r) {
    return Set.of(
            State.RECEIVED, State.AWAITING_DEPOSIT, State.CONFIRMATION_REQUESTED, State.CONFIRMED)
        .contains(r.state());
  }
}
