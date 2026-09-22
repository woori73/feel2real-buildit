package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewStartOfficialUpdateService {

  private final NewStartOfficialUpdateRepository repository;

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
        requireText(request.summary(), "요약"), url, documentId, request.publishedAt(),
        request.effectiveFrom(), request.effectiveTo(), request.category(), relevancePriority, dedupKey, discoveredAt));
    return NewStartOfficialUpdateResponse.from(saved);
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

  static String normalizeUrl(String rawUrl) {
    String value = normalize(rawUrl);
    if (value == null) return null;
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
      String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
      return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + path + query;
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("공식 원문 URL 형식이 올바르지 않습니다.", e);
    }
  }

  static String normalizeTitle(String title) {
    return requireText(title, "제목").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
