package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.ReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewStartOfficialUpdateService {

  private final NewStartOfficialUpdateRepository repository;

  /** 자동 게시된 항목의 reviewedBy 표식 — 관리자 사후 검토(게시취소/보관) 대상 식별용. */
  public static final String AUTO_PUBLISHED_BY = "AUTO_DISCOVERY";

  @Transactional
  public NewStartOfficialUpdateResponse createReview(NewStartOfficialUpdateRequest request) {
    return createReview(request, RelevancePriority.NORMAL, null);
  }

  @Transactional
  public NewStartOfficialUpdateResponse createReview(NewStartOfficialUpdateRequest request,
      RelevancePriority relevancePriority, LocalDateTime discoveredAt) {
    String url = normalizeUrl(request.originalUrl());
    String title = normalizeTitle(request.title());
    String documentId = normalize(request.officialDocumentId());
    String dedupKey = dedupKey(request.sourceType(), documentId, url, title, request.publishedAt());
    if (repository.findByDedupKey(dedupKey).isPresent()) {
      throw new IllegalArgumentException("동일한 공식 업데이트가 이미 등록되어 있습니다.");
    }

    NewStartOfficialUpdate saved = repository.save(NewStartOfficialUpdate.review(
        request.sourceType(), requireText(request.sourceName(), "출처명"), title,
        requireText(request.summary(), "요약"), sanitizeContent(request.content()), url, documentId, request.publishedAt(),
        request.effectiveFrom(), request.effectiveTo(), request.category(), relevancePriority, dedupKey, discoveredAt));
    return NewStartOfficialUpdateResponse.from(saved);
  }

  /**
   * 자동 게시 정책(2026-09-23): 공식 출처에서 정상 수집된 항목은 중요도·관련성과 무관하게
   * 즉시 PUBLISHED 한다. 관리자는 사후에 게시취소(reject)/보관(archive)으로 정리한다.
   * 원문 URL 누락·형식 오류는 entity.publish() 기존 검증이 차단한다(technical block).
   * 자동 게시는 뉴스 게시판까지만이며 Knowledge ACTIVE/OFFICIAL_CURRENT 승격과 무관하다.
   */
  @Transactional
  public NewStartOfficialUpdateResponse createPublished(NewStartOfficialUpdateRequest request,
      RelevancePriority relevancePriority, LocalDateTime discoveredAt) {
    NewStartOfficialUpdateResponse created = createReview(request, relevancePriority, discoveredAt);
    return publish(created.updateId(), AUTO_PUBLISHED_BY);
  }

  @Transactional(readOnly = true)
  public List<NewStartOfficialUpdateResponse> publicUpdates(SourceType sourceType) {
    List<NewStartOfficialUpdate> updates = sourceType == null
        ? repository.findByReviewStatusOrderByFeaturedDescPublishedAtDescUpdateIdDesc(ReviewStatus.PUBLISHED)
        : repository.findByReviewStatusAndSourceTypeOrderByFeaturedDescPublishedAtDescUpdateIdDesc(
            ReviewStatus.PUBLISHED, sourceType);
    return updates.stream().map(NewStartOfficialUpdateResponse::from).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<NewStartOfficialUpdateResponse> latestPublished(int limit) {
    return publicUpdates(null).stream().limit(limit).toList();
  }

  /**
   * NewStart 내부 상세보기용. 게시(PUBLISHED)된 항목만 반환하며, 본문은 저장된 공식 원문만 사용한다.
   * sourceUrl 검증 실패와 무관하게 내용은 항상 열람 가능하다(linkOnly 검증 실패 시 보조 링크만 숨김).
   */
  @Transactional(readOnly = true)
  public NewStartOfficialUpdateDetailResponse publicUpdateDetail(Long updateId) {
    NewStartOfficialUpdate update = repository.findById(updateId)
        .filter(u -> u.getReviewStatus() == ReviewStatus.PUBLISHED)
        .orElseThrow(() -> new IllegalArgumentException("게시된 공식 업데이트를 찾을 수 없습니다."));
    String verifiedUrl = OfficialSourceLinkPolicy.verifiedUrl(update.getSourceType(), update.getOriginalUrl());
    return NewStartOfficialUpdateDetailResponse.from(update, verifiedUrl);
  }

  @Transactional(readOnly = true)
  public List<NewStartOfficialUpdateResponse> adminUpdates(SourceType sourceType, ReviewStatus status) {
    List<NewStartOfficialUpdate> updates;
    if (sourceType != null) {
      updates = status == null
          ? repository.findBySourceTypeOrderByUpdatedAtDescUpdateIdDesc(sourceType)
          : repository.findByReviewStatusAndSourceTypeOrderByFeaturedDescPublishedAtDescUpdateIdDesc(status, sourceType);
    } else {
      updates = status == null
          ? repository.findAllByOrderByUpdatedAtDescUpdateIdDesc()
          : repository.findByReviewStatusOrderByFeaturedDescPublishedAtDescUpdateIdDesc(status);
    }
    return updates.stream().map(NewStartOfficialUpdateResponse::from).toList();
  }

  @Transactional
  public NewStartOfficialUpdateResponse publish(Long id, String reviewer) {
    NewStartOfficialUpdate update = get(id);
    update.publish(reviewer);
    return NewStartOfficialUpdateResponse.from(update);
  }

  @Transactional
  public NewStartOfficialUpdateResponse reject(Long id, String reviewer) {
    NewStartOfficialUpdate update = get(id);
    update.reject(reviewer);
    return NewStartOfficialUpdateResponse.from(update);
  }

  @Transactional
  public NewStartOfficialUpdateResponse archive(Long id, String reviewer) {
    NewStartOfficialUpdate update = get(id);
    update.archive(reviewer);
    return NewStartOfficialUpdateResponse.from(update);
  }

  @Transactional
  public NewStartOfficialUpdateResponse returnToReview(Long id, String reviewer) {
    NewStartOfficialUpdate update = get(id);
    update.returnToReview(reviewer);
    return NewStartOfficialUpdateResponse.from(update);
  }

  @Transactional
  public NewStartOfficialUpdateResponse setFeatured(Long id, boolean featured) {
    NewStartOfficialUpdate update = get(id);
    update.setFeatured(featured);
    return NewStartOfficialUpdateResponse.from(update);
  }

  private NewStartOfficialUpdate get(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("공식 업데이트를 찾을 수 없습니다."));
  }

  static String dedupKey(SourceType sourceType, String officialDocumentId, String normalizedUrl,
      String normalizedTitle, LocalDateTime publishedAt) {
    if (officialDocumentId != null) {
      return "DOC:" + sourceType.name() + ":" + officialDocumentId.toLowerCase(Locale.ROOT);
    }
    if (normalizedUrl != null) {
      return "URL:" + normalizedUrl;
    }
    return "TITLE_DATE:" + sourceType.name() + ":" + normalizedTitle + ":"
        + (publishedAt == null ? "NO_DATE" : publishedAt.toLocalDate());
  }

  /** `https://host/https://host/...` 형태 이중 도메인 오염 (Production QA #1 결함)를 복구한다. */
  private static final Pattern DOUBLE_DOMAIN_URL =
      Pattern.compile("(?i)^https?://[^/]+/(https?://.+)$");

  static String normalizeUrl(String rawUrl) {
    String value = normalize(rawUrl);
    if (value == null) return null;
    Matcher doubleDomain = DOUBLE_DOMAIN_URL.matcher(value);
    if (doubleDomain.matches()) {
      return normalizeUrl(doubleDomain.group(1));
    }
    try {
      URI uri = new URI(value);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!"http".equals(scheme) && !"https".equals(scheme)) {
        throw new IllegalArgumentException("공식 원문 URL은 http 또는 https여야 합니다.");
      }
      if (uri.getHost() == null) {
        throw new IllegalArgumentException("공식 원문 URL 형식이 올바르지 않습니다.");
      }
      String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
      String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
      String fragment = uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
      return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + path + query + fragment;
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("공식 원문 URL 형식이 올바르지 않습니다.", e);
    }
  }

  static String normalizeTitle(String title) {
    return requireText(title, "제목").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /** 저장된 TEXT 컬럼 상한(65,535 bytes) 대비 안전한 문자 상한. 공식 원문 본문은 백분율 생략 없이 앞 부분만 보존한다. */
  static final int CONTENT_MAX_CHARS = 60_000;

  /**
   * 공식 출처에서 수집된 원문(detailHtml) 저장 전 XSS 방어용 화이트리스트 클리닝.
   * 본문 "내용"은 수정하지 않는다 — 태그만 정제하고 텍스트는 그대로 둔다.
   */
  static String sanitizeContent(String detailHtml) {
    String value = normalize(detailHtml);
    if (value == null) return null;
    String cleaned = Jsoup.clean(value, Safelist.relaxed());
    return cleaned.length() > CONTENT_MAX_CHARS ? cleaned.substring(0, CONTENT_MAX_CHARS) : cleaned;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private static String requireText(String value, String label) {
    String normalized = normalize(value);
    if (Objects.isNull(normalized)) throw new IllegalArgumentException(label + "은(는) 필수입니다.");
    return normalized;
  }
}
