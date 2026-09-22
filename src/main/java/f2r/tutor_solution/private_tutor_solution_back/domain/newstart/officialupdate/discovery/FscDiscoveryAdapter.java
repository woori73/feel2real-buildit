package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FscDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  static final String LIST_URL = "https://www.fsc.go.kr/po040301?curPage=1&srchKey=&srchWord=";
  private static final Pattern NOTICE_ID_PATTERN = Pattern.compile("noticeId=(\\d+)");

  private static final String[] RELEVANT_KEYWORDS = {
      "새출발기금", "채무조정", "소상공인", "자영업자", "부실차주", "부실우려차주",
      "재기지원", "채권매입", "원금조정", "감면", "연체", "코로나", "금융지원"
  };

  @Override
  public SourceType getSourceType() {
    return SourceType.FSC;
  }

  @Override
  public String getSourceName() {
    return "금융위원회";
  }

  @Override
  protected List<DiscoveredCandidate> doDiscover(LocalDateTime since) {
    log.info("금융위원회 discovery 실행 (since={})", since);
    String html = fetchHtmlRequired(LIST_URL, 3);
    List<DiscoveredCandidate> all = parseListPage(html);

    List<DiscoveredCandidate> relevant = new ArrayList<>();
    for (DiscoveredCandidate candidate : all) {
      if (since != null && candidate.publishedAt() != null && candidate.publishedAt().isBefore(since)) {
        continue;
      }
      String title = candidate.title();
      if (containsAny(title, RELEVANT_KEYWORDS)) {
        relevant.add(candidate);
      }
    }
    log.info("금융위원회 관련 후보 {}건 발견 (전체 행 {}건)", relevant.size(), all.size());
    return relevant;
  }

  List<DiscoveredCandidate> parseListPage(String html) {
    Document doc = Jsoup.parse(html);
    Elements rows = doc.select("div.board-list-wrap ul li");
    List<DiscoveredCandidate> parsed = new ArrayList<>();
    int structuralRows = 0;

    for (Element row : rows) {
      Element linkEl = row.selectFirst("div.subject a");
      if (linkEl == null) {
        continue;
      }
      structuralRows++;
      Matcher m = NOTICE_ID_PATTERN.matcher(linkEl.attr("href"));
      if (!m.find()) {
        continue;
      }
      String seq = m.group(1);

      String title = linkEl.attr("title");
      if (title.isBlank()) {
        title = linkEl.text().trim();
      }
      if (title.isBlank()) {
        continue;
      }

      Element infoEl = row.selectFirst("div.info");
      LocalDateTime publishedAt = parseLooseDate(infoEl != null ? infoEl.text() : null);
      String detailUrl = resolveUrl(LIST_URL, linkEl.attr("href"));

      parsed.add(new DiscoveredCandidate(
          title,
          title,
          detailUrl,
          "FSC-" + seq,
          publishedAt,
          null,
          null,
          Category.POLICY,
          null));
    }

    if (structuralRows > 0 && parsed.isEmpty()) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "공지 목록 행 " + structuralRows + "건 중 파싱 성공 0건 - 사이트 구조 변경 가능성");
    }
    return parsed;
  }

  @Override
  protected DiscoveredCandidate doEnrich(DiscoveredCandidate candidate) {
    String html = fetchHtmlRequired(candidate.originalUrl(), 2);
    Document doc = Jsoup.parse(html);
    Element contentEl = doc.selectFirst("div.view_cont, div.cont_view, .board_view .cont, #content");
    String bodyText = contentEl != null ? contentEl.text() : doc.body().text();

    List<String> attachments = extractAttachments(doc, candidate.originalUrl(),
        "div.file-list a[href*=/comm/getFile]", ".file-list a[href]", "div.file a[href]");

    StringBuilder fileNames = new StringBuilder();
    for (Element name : doc.select("div.file-list span.name")) {
      if (fileNames.length() > 0) {
        fileNames.append("; ");
      }
      fileNames.append(name.text().trim());
    }

    String summary = bodyText.length() > 300 ? bodyText.substring(0, 300) + "..." : bodyText;
    if (summary.isBlank()) {
      summary = candidate.title();
    }
    if (!attachments.isEmpty()) {
      summary += " [첨부: " + String.join("; ", attachments) + "]";
    } else if (!fileNames.isEmpty()) {
      summary += " [첨부파일명: " + fileNames + "]";
    }

    return new DiscoveredCandidate(
        candidate.title(),
        summary,
        candidate.originalUrl(),
        candidate.officialDocumentId(),
        candidate.publishedAt(),
        candidate.effectiveFrom(),
        candidate.effectiveTo(),
        candidate.category(),
        bodyText);
  }

  private static boolean containsAny(String text, String... keywords) {
    for (String kw : keywords) {
      if (text.contains(kw)) {
        return true;
      }
    }
    return false;
  }
}
