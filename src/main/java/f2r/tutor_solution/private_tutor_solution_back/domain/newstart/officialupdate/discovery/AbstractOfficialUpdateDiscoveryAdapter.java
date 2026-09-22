package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class AbstractOfficialUpdateDiscoveryAdapter implements OfficialUpdateDiscoveryAdapter {

  private static final Pattern ISO_DATE_IN_TEXT = Pattern.compile("(20\\d{2})[-.](\\d{1,2})[-.](\\d{1,2})");

  @Autowired(required = false)
  protected DiscoveryHttpClient httpClient;

  @Override
  public List<DiscoveredCandidate> discover(LocalDateTime since) {
    if (httpClient == null) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.FETCH_FAILED,
          "DiscoveryHttpClient 가 주입되지 않아 공식 페이지를 조회할 수 없음");
    }
    return doDiscover(since);
  }

  protected abstract List<DiscoveredCandidate> doDiscover(LocalDateTime since);

  @Override
  public DiscoveredCandidate enrich(DiscoveredCandidate candidate) {
    if (httpClient == null || candidate.originalUrl() == null) {
      return candidate;
    }
    return doEnrich(candidate);
  }

  protected DiscoveredCandidate doEnrich(DiscoveredCandidate candidate) {
    return candidate;
  }

  protected String fetchHtmlRequired(String url, int maxRetries) {
    DiscoverySourceException last = null;
    for (int i = 0; i < maxRetries; i++) {
      try {
        return httpClient.fetchHtml(url);
      } catch (Exception e) {
        log.debug("시도 {}/{} 실패: {} - {}", i + 1, maxRetries, url, e.getMessage());
        last = new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.FETCH_FAILED,
            "목록/상세 페이지 조회 실패 " + url + " (" + e.getMessage() + ")", e);
      }
    }
    throw last;
  }

  /**
   * "HTTP 200 인데 항목이 존재하는데 parse 0건" 을 성공으로 넘기지 않기 위한 가드.
   * structuralRows: 목록 페이지에서 실제로 발견된 행(항목) 수.
   * parsedRows: 행에서 필수 필드(문서ID/제목)를 뽑아 파싱에 성공한 수 (키워드 필터 제외 순수 파싱 실패만 계산).
   */
  protected void guardParsedNotEmpty(int structuralRows, int parsedRows) {
    if (structuralRows > 0 && parsedRows == 0) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "목록 페이지에 행 " + structuralRows + "건이 있는데 파싱 결과 0건 - 셀렉터/사이트 구조 변경 점검 필요");
    }
  }

  protected List<String> extractAttachments(org.jsoup.nodes.Document doc, String baseUrl, String... selectors) {
    List<String> attachments = new ArrayList<>();
    for (String selector : selectors) {
      Elements links = doc.select(selector);
      for (Element a : links) {
        String href = a.attr("href");
        if (href.isBlank() || href.startsWith("javascript:") || href.equals("#")) {
          continue;
        }
        String fileName = a.attr("download");
        if (fileName.isBlank()) {
          fileName = a.text().trim();
        }
        if (fileName.isBlank()) {
          fileName = href.substring(href.lastIndexOf('/') + 1);
        }
        attachments.add(fileName + "|" + resolveUrl(baseUrl, href));
      }
      if (!attachments.isEmpty()) {
        return attachments;
      }
    }
    return attachments;
  }

  protected static String resolveUrl(String baseUrl, String relativeUrl) {
    if (relativeUrl.startsWith("http")) return relativeUrl;
    try {
      return new URI(baseUrl).resolve(relativeUrl).toString();
    } catch (Exception e) {
      return relativeUrl;
    }
  }

  /** "2026-09-21", "2026.09.21", "2026. 9. 21." 등混杂 날짜에서 첫 ISO 날짜를 추출한다. */
  protected static LocalDateTime parseLooseDate(String text) {
    if (text == null || text.isBlank()) return null;
    Matcher m = ISO_DATE_IN_TEXT.matcher(text);
    if (!m.find()) return null;
    try {
      return LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
          Integer.parseInt(m.group(3)), 0, 0, 0);
    } catch (DateTimeParseException | IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public RelevancePriority evaluateRelevance(DiscoveredCandidate candidate) {
    String text = (candidate.title() + " " + candidate.summary() + " " + candidate.detailHtml()).toLowerCase(Locale.ROOT);

    if (containsAny(text,
        "추가감면", "감면율", "감면조건", "감면대상", "적용조건 변경",
        "제도시행", "제도종료", "시행일", "종료일",
        "자격변경", "대상변경", "지원자격", "신청기한", "마감임박",
        "감면한도", "우대감면", "10%감면", "10%p")) {
      return RelevancePriority.CRITICAL;
    }

    if (containsAny(text,
        "재취업", "취업교육", "취업지원", "취업프로그램",
        "재창업", "창업교육", "창업지원", "사업화",
        "희망리턴패키지", "원스톱폐업", "재기지원", "재도전",
        "폐업자", "부실차주", "새출발기금연계", "연계프로그램",
        "경영개선", "채무조정", "법률자문",
        "모집", "신청", "접수", "교육생")) {
      return RelevancePriority.HIGH;
    }

    if (containsAny(text,
        "강사모집", "컨설턴트모집", "수행기관모집", "PM모집", "프로젝트매니저모집",
        "용역입찰", "위탁운영", "운영기관선정", "평가위원",
        "채용공고", "직원채용", "인턴채용",
        "행사안내", "세미나", "워크숍", "설명회")) {
      return RelevancePriority.IRRELEVANT;
    }

    return RelevancePriority.NORMAL;
  }

  @Override
  public String buildDedupKey(DiscoveredCandidate candidate) {
    String documentId = normalize(candidate.officialDocumentId());
    String url = normalizeUrl(candidate.originalUrl());
    String title = normalizeTitle(candidate.title());
    SourceType sourceType = getSourceType();

    if (documentId != null) {
      return "DOC:" + sourceType.name() + ":" + documentId.toLowerCase(Locale.ROOT);
    }
    if (url != null) {
      return "URL:" + url;
    }
    return "TITLE_DATE:" + sourceType.name() + ":" + title + ":"
        + (candidate.publishedAt() == null ? "NO_DATE" : candidate.publishedAt().toLocalDate());
  }

  protected String normalizeUrl(String rawUrl) {
    String value = normalize(rawUrl);
    if (value == null) return null;
    try {
      URI uri = new URI(value);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!"http".equals(scheme) && !"https".equals(scheme)) {
        return null;
      }
      if (uri.getHost() == null) {
        return null;
      }
      String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
      String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
      return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + path + query;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  protected String normalizeTitle(String title) {
    return normalize(title).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  protected String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private boolean containsAny(String text, String... keywords) {
    for (String kw : keywords) {
      if (text.contains(kw.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }
}