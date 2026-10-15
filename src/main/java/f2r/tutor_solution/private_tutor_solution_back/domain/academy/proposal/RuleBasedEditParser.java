package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자연어 수정 요청을 AI 없이 해석하는 결정적(규칙 기반) 파서 — AI 공급자가 꺼져 있거나 실패했을 때의 대체 경로.
 *
 * <p>뮤즈의 PR #602({@code AcademyAiEditService})의 아이디어(따옴표 안 새 텍스트 + 키워드로 대상 지정, 색·크기
 * 스타일)를 가져오되, 대상을 <b>저장된 초안의 실제 요소 목록({@link EditorOutline})</b>에서 찾도록 바꿨다
 * (원안은 {@code ve5}/{@code ve6} 고정 id 자리표시자였다). 결과는 호출부가 {@link ProposalChangeValidator}로
 * 다시 검증하므로 이 클래스는 형식만 만든다. 이해하지 못하면 {@link Optional#empty()}(추측하지 않는다).
 *
 * <p>지원 형식:
 * <ul>
 *   <li>텍스트: {@code 제목을 '새 제목'으로 바꿔줘} (제목→h1, 소제목·부제목→h2, 소개·설명→p, 버튼→a)</li>
 *   <li>텍스트(기존 문구 지정): {@code '기존 문구'를 '새 문구'로 바꿔줘} — 기존 문구를 가진 요소를 찾는다</li>
 *   <li>스타일: {@code 제목 색을 빨간색으로 바꿔줘}, {@code 소개 글자 크기를 20px로 변경}</li>
 * </ul>
 */
public final class RuleBasedEditParser {

  private static final Pattern QUOTED = Pattern.compile("'([^']+)'|\"([^\"]+)\"|‘([^’]+)’|“([^”]+)”");
  private static final Pattern SIZE = Pattern.compile("(\\d{1,3})\\s*px");
  private static final Map<String, String> COLORS = Map.of(
      "빨간", "#dc2626", "빨강", "#dc2626", "파란", "#2563eb", "파랑", "#2563eb",
      "검정", "#111827", "검은", "#111827", "초록", "#047857", "녹색", "#047857",
      "하얀", "#ffffff", "흰", "#ffffff");

  /** 키워드와 그 키워드가 가리키는 태그(우선순위 순). 긴 키워드(소제목)를 먼저 검사한다. */
  private static final List<Map.Entry<String, List<String>>> TARGETS = List.of(
      Map.entry("소제목", List.of("h2", "h3")),
      Map.entry("부제목", List.of("h2", "h3")),
      Map.entry("제목", List.of("h1", "h2")),
      Map.entry("소개", List.of("p")),
      Map.entry("설명", List.of("p")),
      Map.entry("문구", List.of("p")),
      Map.entry("버튼", List.of("a", "button")));

  private RuleBasedEditParser() {
  }

  /** 해석 결과: 요약 문장과 변경 목록. */
  public record Parsed(String summary, List<ProposalChange> changes) {
  }

  public static Optional<Parsed> parse(String command, List<EditorOutline.Item> outline) {
    if (command == null || outline == null || outline.isEmpty()) {
      return Optional.empty();
    }
    String cmd = command.trim();
    if (!(cmd.contains("바꿔") || cmd.contains("바꾸") || cmd.contains("수정") || cmd.contains("변경")
        || cmd.contains("고쳐"))) {
      return Optional.empty();
    }
    List<String> quoted = quotes(cmd);
    boolean styleWord = cmd.contains("색") || cmd.contains("크기") || SIZE.matcher(cmd).find();

    if (styleWord && quoted.isEmpty()) {
      return style(cmd, outline);
    }
    if (quoted.size() >= 2) {
      // '기존 문구'를 '새 문구'로: 기존 문구를 가진 요소가 정확히 1개여야 한다.
      String old = quoted.get(0);
      List<EditorOutline.Item> hits = outline.stream()
          .filter(i -> i.text() != null && i.text().contains(old)).toList();
      if (hits.size() != 1) {
        return Optional.empty();
      }
      return text(hits.get(0), quoted.get(1));
    }
    if (quoted.size() == 1) {
      return target(cmd, outline).flatMap(item -> text(item, quoted.get(0)));
    }
    return Optional.empty();
  }

  private static Optional<Parsed> text(EditorOutline.Item item, String after) {
    List<ProposalChange> changes = List.of(new ProposalChange(item.id(), ProposalChange.TEXT, after, null, null));
    return Optional.of(new Parsed("텍스트 변경(규칙 기반): " + item.id(), changes));
  }

  private static Optional<Parsed> style(String cmd, List<EditorOutline.Item> outline) {
    Optional<EditorOutline.Item> item = target(cmd, outline);
    if (item.isEmpty()) {
      return Optional.empty();
    }
    java.util.Map<String, String> styles = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, String> c : COLORS.entrySet()) {
      if (cmd.contains(c.getKey())) {
        styles.put("color", c.getValue());
        break;
      }
    }
    Matcher size = SIZE.matcher(cmd);
    if (size.find()) {
      styles.put("font-size", size.group(1) + "px");
    }
    if (styles.isEmpty()) {
      return Optional.empty();
    }
    List<ProposalChange> changes = new ArrayList<>();
    changes.add(new ProposalChange(item.get().id(), ProposalChange.STYLE, null, styles, null));
    return Optional.of(new Parsed("스타일 변경(규칙 기반): " + item.get().id(), changes));
  }

  /** 키워드→태그로 요소를 고른다. 같은 태그가 여럿이면 문서 순서상 첫 요소(히어로 제목 등). */
  private static Optional<EditorOutline.Item> target(String cmd, List<EditorOutline.Item> outline) {
    for (Map.Entry<String, List<String>> t : TARGETS) {
      if (!cmd.contains(t.getKey())) {
        continue;
      }
      for (String tag : t.getValue()) {
        Optional<EditorOutline.Item> hit = outline.stream().filter(i -> tag.equalsIgnoreCase(i.tag())).findFirst();
        if (hit.isPresent()) {
          return hit;
        }
      }
    }
    return Optional.empty();
  }

  private static List<String> quotes(String cmd) {
    List<String> out = new ArrayList<>();
    Matcher m = QUOTED.matcher(cmd);
    while (m.find()) {
      for (int g = 1; g <= 4; g++) {
        if (m.group(g) != null && !m.group(g).isBlank()) {
          out.add(m.group(g).trim());
          break;
        }
      }
    }
    return out;
  }
}
