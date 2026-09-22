package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
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

/**
 * 새출발기금 공식 사이트(newstartfund.or.kr = 새출발기금.kr) 공지사항 어댑터.
 * 2026-09-22 조사로 구 URL(kinfa boardList.do) 은 eGov 에러 페이지(HTTP 200)를 반환하는 것이 확인되어 교체했다.
 */
@Component
@Slf4j
public class NewStartFundDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  static final String BOARD_LIST_URL = "https://www.newstartfund.or.kr/Board/List.do?pageIndex=%d";
  static final String BOARD_VIEW_URL = "https://www.newstartfund.or.kr/Board/View.do?idx=%d";
  private static final Pattern FN_VIEW_PATTERN = Pattern.compile("fn_view\\((\\d+)\\)");
  private static final Pattern TOTAL_PATTERN = Pattern.compile("<span>\\s*(\\d+)\\s*건");
  private static final int MAX_PAGES = 5;

  @Override
  public SourceType getSourceType() {
    return SourceType.NEWSTART_FUND;
  }

  @Override
  public String getSourceName() {
    return "새출발기금";
  }

  @Override
  protected List<DiscoveredCandidate> doDiscover(LocalDateTime since) {
    List<DiscoveredCandidate> candidates = new ArrayList<>();
    log.info("새출발기금 discovery 실행 (since={})", since);

    for (int pageIndex = 1; pageIndex <= MAX_PAGES; pageIndex++) {
      String html = fetchHtmlRequired(String.format(BOARD_LIST_URL, pageIndex), 3);
      PageParse page = parseListPage(html);
      boolean olderSeen = false;
      for (DiscoveredCandidate candidate : page.candidates()) {
        if (since != null && candidate.publishedAt() != null && candidate.publishedAt().isBefore(since)) {
          olderSeen = true;
          continue;
        }
        candidates.add(candidate);
      }
      if (page.candidates().isEmpty() || olderSeen) {
        break;
      }
    }

    log.info("새출발기금 후보 {}건 발견", candidates.size());
    return candidates;
  }

  record PageParse(List<DiscoveredCandidate> candidates, int structuralRows) {}

  PageParse parseListPage(String html) {
    Document doc = Jsoup.parse(html);
    Elements rows = doc.select("table.tbl_bd tbody tr");
    List<DiscoveredCandidate> parsed = new ArrayList<>();
    int structuralRows = 0;

    for (Element row : rows) {
      Element linkEl = row.selectFirst("td.list_tit a");
      if (linkEl == null) {
        continue;
      }
      structuralRows++;
      Matcher m = FN_VIEW_PATTERN.matcher(linkEl.attr("href"));
      if (!m.find()) {
        continue;
      }
      long idx = Long.parseLong(m.group(1));

      String title = linkEl.text().trim();
      if (title.isBlank()) {
        continue;
      }
      Element dateEl = row.selectFirst("td.list_write");
      LocalDateTime publishedAt = parseLooseDate(dateEl != null ? dateEl.text() : null);

      parsed.add(new DiscoveredCandidate(
          title,
          title,
          String.format(BOARD_VIEW_URL, idx),
          "NSF-B-" + idx,
          publishedAt,
          null,
          null,
          Category.NOTICE,
          null));
    }

    if (structuralRows > 0 && parsed.isEmpty()) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "공지 목록 행 " + structuralRows + "건 중 파싱 성공 0건 - 사이트 구조 변경 가능성");
    }
    if (structuralRows == 0) {
      Matcher total = TOTAL_PATTERN.matcher(html);
      if (total.find() && Integer.parseInt(total.group(1)) > 0) {
        throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
            "전체 " + total.group(1) + "건 표시인데 행 파싱 0건 - 셀렉터 점검 필요");
      }
    }
    return new PageParse(parsed, structuralRows);
  }

  @Override
  protected DiscoveredCandidate doEnrich(DiscoveredCandidate candidate) {
    String url = candidate.originalUrl();
    if (url == null || !url.contains("newstartfund.or.kr")) {
      return candidate;
    }
    String html = fetchHtmlRequired(url, 2);
    return enrichFromView(html, candidate);
  }

  DiscoveredCandidate enrichFromView(String html, DiscoveredCandidate candidate) {
    Document doc = Jsoup.parse(html);
    Element contentEl = doc.selectFirst("div.vp_contents");
    String bodyText = contentEl != null ? contentEl.text() : doc.body().text();

    List<String> attachments = extractAttachments(doc, candidate.originalUrl(),
        "div.board_file a[href^=/AttachFile]", "div.file_wrap a[href^=/AttachFile]",
        "a[href^=/AttachFile]");

    StringBuilder summary = new StringBuilder(
        bodyText.length() > 300 ? bodyText.substring(0, 300) + "..." : bodyText);
    if (!attachments.isEmpty()) {
      summary.append(" [첨부: ").append(String.join("; ", attachments)).append("]");
    }

    LocalDateTime publishedAt = candidate.publishedAt();
    if (publishedAt == null) {
      Element dateEl = doc.selectFirst("div.vp_date");
      publishedAt = parseLooseDate(dateEl != null ? dateEl.text() : null);
    }

    return new DiscoveredCandidate(
        candidate.title(),
        summary.toString(),
        candidate.originalUrl(),
        candidate.officialDocumentId(),
        publishedAt,
        candidate.effectiveFrom(),
        candidate.effectiveTo(),
        candidate.category(),
        bodyText);
  }
}
