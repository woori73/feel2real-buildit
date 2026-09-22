package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import java.net.URISyntaxException;

public abstract class AbstractOfficialUpdateDiscoveryAdapter implements OfficialUpdateDiscoveryAdapter {

  @Override
  public List<DiscoveredCandidate> discover(LocalDateTime since) {
    // 기본 구현: 하위 클래스에서 오버라이드
    return Collections.emptyList();
  }

  @Override
  public DiscoveredCandidate enrich(DiscoveredCandidate candidate) {
    // 기본 구현: 상세 페이지 조회가 필요하면 하위 클래스에서 오버라이드
    return candidate;
  }

  @Override
  public RelevancePriority evaluateRelevance(DiscoveredCandidate candidate) {
    // 기본 구현: 키워드 기반 판단
    String text = (candidate.title() + " " + candidate.summary() + " " + candidate.detailHtml()).toLowerCase(Locale.ROOT);
    
    // CRITICAL 키워드
    if (containsAny(text, 
        "추가감면", "감면율", "감면조건", "감면대상", "적용조건 변경",
        "제도시행", "제도종료", "시행일", "종료일",
        "자격변경", "대상변경", "지원자격", "신청기한", "마감임박",
        "감면한도", "우대감면", "10%감면", "10%p")) {
      return RelevancePriority.CRITICAL;
    }
    
    // HIGH 키워드 - 재취업/재창업/희망리턴패키지
    if (containsAny(text,
        "재취업", "취업교육", "취업지원", "취업프로그램",
        "재창업", "창업교육", "창업지원", "사업화",
        "희망리턴패키지", "원스톱폐업", "재기지원", "재도전",
        "폐업자", "부실차주", "새출발기금연계", "연계프로그램",
        "경영개선", "채무조정", "법률자문",
        "모집", "신청", "접수", "교육생")) {
      return RelevancePriority.HIGH;
    }
    
    // IRRELEVANT 키워드 - 강사/컨설턴트/수행기관 모집 등
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