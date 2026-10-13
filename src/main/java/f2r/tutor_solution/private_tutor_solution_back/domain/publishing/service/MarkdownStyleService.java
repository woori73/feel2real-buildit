package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Markdown 구조 자동 스타일 적용 서비스 (P4).
 *
 * <p>CLI의 {@code file.place}는 Markdown 구조를 파싱하지 않고 raw text로 반입하므로,
 * {@code #}, {@code ##} 등의 마커가 그대로 남고 모든 문단에 [Basic Paragraph]가 적용된다.
 * 이 서비스는:
 * <ol>
 *   <li>Markdown을 파싱하여 제목/본문을 구분한다</li>
 *   <li>마커를 제거한 clean text를 생성한다 (반입용)</li>
 *   <li>반입 후 각 제목 텍스트를 {@code find.find}로 찾아 {@code style.paragraph.apply}로
 *       ko-heading1/ko-heading2/ko-heading3 스타일을 적용한다</li>
 * </ol>
 *
 * <p>본문은 styleMap({@code {"[Basic Paragraph]":"ko-body"}})으로 처리하므로 충돌하지 않는다.
 */
@Slf4j
@Service
public class MarkdownStyleService {

  private static final Pattern H1 = Pattern.compile("^#\\s+(.+)$");
  private static final Pattern H2 = Pattern.compile("^##\\s+(.+)$");
  private static final Pattern H3 = Pattern.compile("^###\\s+(.+)$");
  private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
  private static final Pattern LIST = Pattern.compile("^[-*]\\s+(.+)$");

  /**
   * 파싱된 블록.
   * @param text clean text (마커 제거)
   * @param original 마커 포함 원본 (예: "# 제목")
   * @param style 적용할 스타일 (예: "ko-heading1")
   * @param paragraphIndex 원본에서의 문단 인덱스 (0-based)
   * @param markerBytes 마커의 UTF-8 바이트 길이 (예: "# "=2, "## "=3)
   */
  public record Block(String text, String original, String style,
      int paragraphIndex, int markerBytes) {}

  /** 파싱 결과: clean text + 스타일 매핑 */
  public record ParsedMarkdown(String cleanText, List<Block> headingBlocks) {}

  /**
   * Markdown 텍스트를 파싱한다.
   *
   * @param markdown 원본 markdown
   * @return clean text(마커 제거)와 제목 블록 목록
   */
  public ParsedMarkdown parse(String markdown) {
    StringBuilder clean = new StringBuilder();
    List<Block> headings = new ArrayList<>();
    int paraIndex = 0;

    for (String line : markdown.split("\\R")) {
      String trimmed = line.trim();
      Matcher m1 = H1.matcher(trimmed);
      Matcher m2 = H2.matcher(trimmed);
      Matcher m3 = H3.matcher(trimmed);

      if (m1.matches()) {
        String text = m1.group(1).trim();
        clean.append(text).append("\n");
        headings.add(new Block(text, line.trim(), "ko-heading1", paraIndex, 2));
        paraIndex++;
      } else if (m2.matches()) {
        String text = m2.group(1).trim();
        clean.append(text).append("\n");
        headings.add(new Block(text, line.trim(), "ko-heading2", paraIndex, 3));
        paraIndex++;
      } else if (m3.matches()) {
        String text = m3.group(1).trim();
        clean.append(text).append("\n");
        headings.add(new Block(text, line.trim(), "ko-heading3", paraIndex, 4));
        paraIndex++;
      } else if (QUOTE.matcher(trimmed).matches()) {
        String text = QUOTE.matcher(trimmed).replaceFirst("$1").trim();
        clean.append(text).append("\n");
        int markerLen = line.trim().startsWith("> ") ? 2 : 1;
        headings.add(new Block(text, line.trim(), "ko-quote", paraIndex, markerLen));
        paraIndex++;
      } else if (LIST.matcher(trimmed).matches()) {
        // 목록은 본문 스타일 유지 (ko-body), 마커만 제거
        String text = LIST.matcher(trimmed).replaceFirst("$1").trim();
        clean.append("• ").append(text).append("\n");
        paraIndex++;
      } else {
        clean.append(line).append("\n");
        // 빈 줄도 문단으로 카운트 (CLI가 빈 줄을 문단으로 처리)
        if (!trimmed.isEmpty() || true) paraIndex++;
      }
    }

    log.info("Markdown 파싱: 제목 {}개 (h1/h2/h3/quote)", headings.size());
    return new ParsedMarkdown(clean.toString(), headings);
  }
}
