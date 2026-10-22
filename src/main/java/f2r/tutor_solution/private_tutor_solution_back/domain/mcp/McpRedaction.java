package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import java.util.regex.Pattern;

/** MCP 응답에서 개인·금융 식별정보를 줄인다. 휴리스틱이므로 권한 검사를 대체하지 않는다(최소 노출 보조 수단). */
final class McpRedaction {
  private McpRedaction() {}

  private static final Pattern PHONE =
      Pattern.compile("(?<!\\d)(01[016789])[- .]?(\\d{3,4})[- .]?(\\d{4})(?!\\d)");
  private static final Pattern INTERNATIONAL_PHONE =
      Pattern.compile(
          "(?<![\\d+])\\+82[- .]?(?:(?:\\(0\\)|0)[- .]?)?(1[016789]|2|[3-6][1-5]|70|50[2-8])[-"
              + " .]?(\\d{3,4})[- .]?(\\d{4})(?!\\d)");
  // Identifier-shaped free text is ambiguous; conservatively mask 10..19 contiguous digits.
  // JSON numeric amounts/IDs remain numbers unless their field explicitly identifies PII.
  private static final Pattern LONG_NUMBER = Pattern.compile("(?<![\\d*])\\d{10,19}(?![\\d*])");
  private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d{4}[- ]){3}(\\d{4})(?!\\d)");
  private static final Pattern EMAIL =
      Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
  private static final Pattern RRN = Pattern.compile("(?<!\\d)(\\d{6})[- ]?([1-8])\\d{6}(?!\\d)");
  private static final Pattern ACCOUNT =
      Pattern.compile(
          "(?<![\\d-])(?!(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])(?!\\d))(\\d{2,6})-(\\d{2,6})-(\\d{2,8})(?![\\d-])");
  static final Pattern SECRET_KEY =
      Pattern.compile(
          "(?i).*(password|secret|auth.?key|api.?key|access.?token|refresh.?token|credential|cookie|storage_key).*");
  private static final Pattern PII_KEY =
      Pattern.compile(
          "(?i).*(phone|mobile|tel|email|account.?(no|number)|card.?(no|number)|resident|rrn|ssn).*");

  static boolean sensitiveKey(String key) {
    return key != null && PII_KEY.matcher(key).matches();
  }

  /** 자유 텍스트 안의 전화번호·이메일·주민번호·계좌번호 형태를 가린다. */
  static String text(String value) {
    if (value == null || value.isEmpty()) return value;
    // Canonical file hashes/UUIDs must remain usable for original-byte verification and links.
    // Explicit PII field names still fall through value()'s whole-value masking below.
    if (value.matches("(?i)[a-f0-9]{64}")
        || value.matches("(?i)[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}")) return value;
    String out = RRN.matcher(value).replaceAll("$1-*******");
    out = INTERNATIONAL_PHONE.matcher(out).replaceAll("+82-$1-****-$3");
    out = PHONE.matcher(out).replaceAll("$1-****-$3");
    out = CARD.matcher(out).replaceAll("****-****-****-$1");
    out =
        LONG_NUMBER
            .matcher(out)
            .replaceAll(
                m ->
                    "*".repeat(m.group().length() - 4)
                        + m.group().substring(m.group().length() - 4));
    out = EMAIL.matcher(out).replaceAll("$1***@$2");
    return ACCOUNT.matcher(out).replaceAll("$1-****-$3");
  }

  /** 키 이름이 개인·금융 식별정보를 가리키면 값 전체를 마지막 4자만 남기고 가린다. */
  static String value(String key, String value) {
    if (value == null || value.isBlank()) return value;
    if (PII_KEY.matcher(key).matches()) {
      String t = text(value);
      if (!t.equals(value)) return t;
      int keep = Math.min(4, value.length() / 2);
      return "*".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }
    // Git SHA-1 may contain a 10..19 digit run. Preserve only explicit commit fields,
    // after the PII-key branch; ordinary account/card/free-text values still get masking.
    if (key.matches("(?i)(commit|commit_?sha|source_?commit|head_?sha|base_?sha)")
        && value.matches("(?i)[a-f0-9]{40}")
        && Pattern.compile("[a-f]", Pattern.CASE_INSENSITIVE).matcher(value).find()) return value;
    return text(value);
  }
}
