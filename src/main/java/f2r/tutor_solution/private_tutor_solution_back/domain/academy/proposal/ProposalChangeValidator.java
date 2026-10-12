package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 제안 생성 시점의 입력 검증·정규화. 통과한 값만 저장되므로, 저장된 changes는 항상 이 규칙을 만족한다.
 * 위반은 {@link IllegalArgumentException}(→ 400)이다.
 */
public final class ProposalChangeValidator {

  public static final int MAX_CHANGES = 50;
  public static final int MAX_TEXT = 2000;
  public static final int MAX_SRC = 2048;
  public static final int MAX_STYLES = 20;
  public static final int MAX_STYLE_VALUE = 200;

  private static final Pattern ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
  private static final Pattern PROP = Pattern.compile("^[a-z][a-z-]{0,39}$");
  private static final Pattern SRC = Pattern.compile("^(https?://[^\\s\"'<>\\\\]+|/(?!/)[^\\s\"'<>\\\\]*)$");
  /** CSS 값에서 허용하지 않는 문자·토큰(규칙 탈출·외부 로딩·스크립트 실행 시도). */
  private static final Pattern BAD_STYLE_VALUE =
      Pattern.compile("[;{}<>\\\\\"]|url\\s*\\(|expression|@import|javascript|behavior|-moz-binding",
          Pattern.CASE_INSENSITIVE);

  private ProposalChangeValidator() {
  }

  public static List<ProposalChange> validate(List<ProposalChange> raw) {
    if (raw == null || raw.isEmpty()) {
      throw new IllegalArgumentException("changes가 비어 있습니다.");
    }
    if (raw.size() > MAX_CHANGES) {
      throw new IllegalArgumentException("changes는 최대 " + MAX_CHANGES + "건입니다.");
    }
    List<ProposalChange> out = new ArrayList<>();
    for (ProposalChange c : raw) {
      if (c == null || c.componentId() == null || !ID.matcher(c.componentId()).matches()) {
        throw new IllegalArgumentException("componentId 형식이 올바르지 않습니다.");
      }
      if (c.type() == null) {
        throw new IllegalArgumentException("type이 필요합니다.");
      }
      switch (c.type()) {
        case ProposalChange.TEXT -> out.add(new ProposalChange(
            c.componentId(), ProposalChange.TEXT, text(c.after()), null, null));
        case ProposalChange.IMAGE -> out.add(new ProposalChange(
            c.componentId(), ProposalChange.IMAGE, src(c.after()), null, null));
        case ProposalChange.STYLE -> out.add(new ProposalChange(
            c.componentId(), ProposalChange.STYLE, null, styles(c.styles()), media(c.mediaText())));
        default -> throw new IllegalArgumentException("지원하지 않는 type입니다: " + c.type());
      }
    }
    return out;
  }

  private static String text(String after) {
    if (after == null || after.isBlank()) {
      throw new IllegalArgumentException("text 변경에는 after가 필요합니다.");
    }
    if (after.length() > MAX_TEXT) {
      throw new IllegalArgumentException("text는 최대 " + MAX_TEXT + "자입니다.");
    }
    // HTML 해석·정제로 값이 달라지지 않도록 마크업 문자와 제어문자는 받지 않는다(순수 텍스트만).
    if (after.indexOf('<') >= 0 || after.indexOf('>') >= 0) {
      throw new IllegalArgumentException("text에는 HTML 태그를 넣을 수 없습니다.");
    }
    for (int i = 0; i < after.length(); i++) {
      char ch = after.charAt(i);
      if (ch < 0x20 && ch != '\n' && ch != '\t') {
        throw new IllegalArgumentException("text에 제어 문자를 넣을 수 없습니다.");
      }
    }
    return after;
  }

  private static String src(String after) {
    if (after == null || after.length() > MAX_SRC || !SRC.matcher(after).matches()) {
      throw new IllegalArgumentException("image after는 http(s) 또는 / 로 시작하는 URL이어야 합니다.");
    }
    return after;
  }

  private static Map<String, String> styles(Map<String, String> raw) {
    if (raw == null || raw.isEmpty() || raw.size() > MAX_STYLES) {
      throw new IllegalArgumentException("styles는 1~" + MAX_STYLES + "개여야 합니다.");
    }
    Map<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String k = e.getKey();
      String v = e.getValue();
      if (k == null || !PROP.matcher(k).matches()) {
        throw new IllegalArgumentException("CSS 속성명이 올바르지 않습니다: " + k);
      }
      if (v == null || v.isBlank() || v.length() > MAX_STYLE_VALUE || BAD_STYLE_VALUE.matcher(v).find()) {
        throw new IllegalArgumentException("CSS 값이 올바르지 않습니다: " + k);
      }
      out.put(k, v.trim());
    }
    return out;
  }

  private static String media(String media) {
    if (media == null || media.isBlank()) {
      return null;
    }
    if (!ProposalChange.MOBILE_MEDIA.equals(media)) {
      throw new IllegalArgumentException("mediaText는 비우거나 " + ProposalChange.MOBILE_MEDIA + "만 가능합니다.");
    }
    return media;
  }
}
