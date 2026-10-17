package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import java.util.regex.Pattern;

/**
 * 상담신청 입력 검증·정규화(순수 로직). 오류 메시지에는 입력값을 싣지 않는다(개인정보·로그 노출 방지).
 */
public final class InquiryInput {

  public static final int NAME_MAX = 50;
  public static final int MESSAGE_MAX = 1000;
  static final int MAX_LINKS = 1;
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
  private static final Pattern PHONE_ALLOWED = Pattern.compile("^[0-9+\\-()\\s]+$");
  private static final Pattern LINK = Pattern.compile("(?i)(https?://|www\\.)");

  /** 검증을 통과한 접수 값. */
  public record Clean(String name, String phone, String message) {}

  /** 검증 실패. message는 사용자에게 보여도 되는 고정 문구뿐이다. */
  public static final class Invalid extends RuntimeException {
    public Invalid(String message) {
      super(message, null, false, false);
    }
  }

  private InquiryInput() {}

  public static Clean validate(String name, String phone, String message, Boolean consent) {
    if (!Boolean.TRUE.equals(consent)) {
      throw new Invalid("개인정보 수집·이용에 동의해 주세요.");
    }
    String n = clean(name);
    if (n.isEmpty() || n.length() > NAME_MAX || n.contains("<") || n.contains(">")) {
      throw new Invalid("이름을 " + NAME_MAX + "자 이하로 입력해 주세요.");
    }
    String p = normalizePhone(phone);
    String m = cleanMessage(message);
    if (m.isEmpty() || m.length() > MESSAGE_MAX) {
      throw new Invalid("문의 내용을 " + MESSAGE_MAX + "자 이하로 입력해 주세요.");
    }
    if (LINK.matcher(m).results().count() > MAX_LINKS) {
      throw new Invalid("문의 내용에 링크는 1개까지만 넣을 수 있어요.");
    }
    return new Clean(n, p, m);
  }

  /** 숫자 8~15자리만 허용. 저장 형식은 선택적 앞 '+' + 숫자(구분 기호 제거). */
  static String normalizePhone(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty() || !PHONE_ALLOWED.matcher(s).matches()) {
      throw new Invalid("연락처는 숫자 8~15자리로 입력해 주세요.");
    }
    String digits = s.replaceAll("[^0-9]", "");
    if (digits.length() < 8 || digits.length() > 15) {
      throw new Invalid("연락처는 숫자 8~15자리로 입력해 주세요.");
    }
    return (s.startsWith("+") ? "+" : "") + digits;
  }

  private static String clean(String v) {
    return v == null ? "" : CONTROL.matcher(v).replaceAll("").replaceAll("\\s+", " ").trim();
  }

  private static String cleanMessage(String v) {
    return v == null ? "" : CONTROL.matcher(v.replace("\r\n", "\n")).replaceAll("").trim();
  }
}
