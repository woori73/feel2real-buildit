package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public class CcrsDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  private static final String LIST_URL = "https://www.ccrs.or.kr/portal/notice/list.do?menuNo=200019";
  private static final String DETAIL_URL_BASE = "https://www.ccrs.or.kr";
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final Pattern SEQ_PATTERN = Pattern.compile("noticeId=(\\d+)");

  private static final String[] RELEVANT_KEYWORDS = {
      "채무조정", "신용회복", "연체", "추심", "금융취약계층", "새출발기금",
      "채무조정제도", "개인워크아웃", "프리워크아웃", "채무자", "지원"
  };

  @Override
  public SourceType getSourceType() {
    return SourceType.CCRS;
  }

  @Override
  public String getSourceName() {
    return "신용회복위원회";
  }

  @Override
  protected List<DiscoveredCandidate> doDiscover(LocalDateTime since) {
    List<DiscoveredCandidate> candidates = new ArrayList<>();
    log.info("신용회복위원회 discovery 실행 (since={})", since);

    try {
      String html = fetchHtmlWithRetry(LIST_URL, 3);
      if (html == null) {
        log.warn("신용회복위원회 목록 페이지 조회 실패");
        return candidates;
      }

      Document doc = Jsoup.parse(html);
      Elements rows = doc.select("table.board_list tbody tr, table.tbl_board tbody tr, .board_list tbody tr");

      for (Element row : rows) {
        try {
          Element linkEl = row.selectFirst("td.subject a, td.title a, td.tit a, a[href*='noticeId']");
          if (linkEl == null) continue;

          String href = linkEl.attr("href");
          Matcher m = SEQ_PATTERN.matcher(href);
          if (!m.find()) continue;
          String seq = m.group(1);

          String title = linkEl.text().trim();
          if (title.isBlank()) continue;

          boolean relevant = false;
          for (String kw : RELEVANT_KEYWORDS) {
            if (title.contains(kw)) {
              relevant = true;
              break;
            }
          }
          if (!relevant) continue;

          Element dateEl = row.selectFirst("td.date, td.regDt, td:nth-last-child(2)");
          LocalDateTime publishedAt = parseDate(dateEl != null ? dateEl.text().trim() : null);

          if (since != null && publishedAt != null && publishedAt.isBefore(since)) {
            continue;
          }

          String detailUrl = DETAIL_URL_BASE + href;
          String officialDocId = "CCRS-" + seq;

          DiscoveredCandidate candidate = new DiscoveredCandidate(
              title,
              title,
              detailUrl,
              officialDocId,
              publishedAt,
              null,
              null,
              Category.NOTICE,
              null
          );

          candidates.add(candidate);

        } catch (Exception e) {
          log.debug("행 파싱 중 오류: {}", e.getMessage());
        }
      }

      log.info("신용회복위원회 관련 후보 {}건 발견", candidates.size());

    } catch (Exception e) {
      log.error("신용회복위원회 discovery 실패: {}", e.getMessage(), e);
    }

    return candidates;
  }

  @Override
  protected DiscoveredCandidate doEnrich(DiscoveredCandidate candidate) {
    try {
      String html = fetchHtmlWithRetry(candidate.originalUrl(), 2);
      if (html == null) return candidate;

      Document doc = Jsoup.parse(html);
      Element contentEl = doc.selectFirst(".board_view .cont, .view_cont, .board_cont, #content");
      String bodyText = contentEl != null ? contentEl.text() : doc.body().text();

      Elements attachEl = doc.select(".file_down a, .attach a, a[href*='downFile']");
      StringBuilder attachInfo = new StringBuilder();
      for (Element a : attachEl) {
        String fileName = a.text().trim();
        String fileUrl = a.attr("href");
        if (!fileName.isBlank()) {
          if (attachInfo.length() > 0) attachInfo.append("; ");
          attachInfo.append(fileName).append("|").append(resolveUrl(candidate.originalUrl(), fileUrl));
        }
      }

      String summary = bodyText.length() > 300 ? bodyText.substring(0, 300) + "..." : bodyText;
      if (attachInfo.length() > 0) {
        summary += " [첨부: " + attachInfo + "]";
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
          bodyText
      );

    } catch (Exception e) {
      log.debug("상세 페이지 보강 실패: {}", e.getMessage());
      return candidate;
    }
  }

  private String fetchHtmlWithRetry(String url, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
      try {
        return httpClient.fetchHtml(url);
      } catch (Exception e) {
        log.debug("시도 {}/{} 실패: {} - {}", i + 1, maxRetries, url, e.getMessage());
        if (i == maxRetries - 1) {
          log.warn("최대 재시도 초과: {}", url);
        }
      }
    }
    return null;
  }

  private LocalDateTime parseDate(String text) {
    if (text == null || text.isBlank()) return null;
    try {
      String cleaned = text.replace(".", "-").replace("/", "-");
      return LocalDateTime.parse(cleaned + "T00:00:00");
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private String resolveUrl(String baseUrl, String relativeUrl) {
    if (relativeUrl.startsWith("http")) return relativeUrl;
    try {
      java.net.URI base = new java.net.URI(baseUrl);
      return base.resolve(relativeUrl).toString();
    } catch (Exception e) {
      return relativeUrl;
    }
  }
}