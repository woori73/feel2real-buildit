package f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.config.AcademyBlogProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademyBlogService {
  private static final int MAX_TITLE_LENGTH = 200;
  private static final int MAX_SLUG_LENGTH = 160;
  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9가-힣][a-z0-9가-힣-]{0,159}$");

  private final AcademyRepository academyRepository;
  private final AcademyBlogPostRepository postRepository;
  private final AcademyBlogPostLikeRepository likeRepository;
  private final AcademyBlogSourceRepository sourceRepository;
  private final UserRepository userRepository;
  private final AuthenticatedUserResolver authResolver;
  private final FileStorage fileStorage;
  private final ImageSanitizer imageSanitizer;
  private final AcademyBlogSanitizer sanitizer;
  private final AcademyBlogProperties properties;

  public List<AcademyBlogPostResponse> listMine(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = requireMine(principal);
    return postRepository.findByAcademy_AcademyIdOrderByUpdatedAtDesc(academy.getAcademyId())
        .stream().map(this::toResponse).toList();
  }

  public AcademyBlogPostResponse getMine(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    Academy academy = requireMine(principal);
    AcademyBlogPost post = postRepository.findById(postId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
    if (!post.getAcademy().getAcademyId().equals(academy.getAcademyId())) {
      throw new AccessDeniedException("자기 Academy 글만 수정할 수 있습니다.");
    }
    return toResponse(post);
  }

  @Transactional
  public AcademyBlogPostResponse create(JwtTokenProvider.AuthenticatedPrincipal principal, AcademyBlogPostRequest request) {
    Academy academy = requireMine(principal);
    NormalizedRequest normalized = normalizeAndValidate(request, academy.getAcademyId(), null);
    try {
      AuditorContextHolder.set(principal.loginId());
      AcademyBlogPost post = AcademyBlogPost.builder()
          .academy(academy)
          .title(normalized.title())
          .slug(normalized.slug())
          .summary(normalized.summary())
          .contentJson(normalized.contentJson())
          .contentHtml(normalized.contentHtml())
          .metaTitle(normalized.metaTitle())
          .metaDescription(normalized.metaDescription())
          .status(normalized.status())
          .visible(normalized.visible())
          .publishedAt(normalized.status() == AcademyBlogPostStatus.PUBLISHED ? LocalDateTime.now() : null)
          .build();
      AcademyBlogPost saved = postRepository.save(post);
      attachSources(academy, saved, normalized.sourceIds());
      return toResponse(saved);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public AcademyBlogPostResponse update(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId, AcademyBlogPostRequest request) {
    Academy academy = requireMine(principal);
    AcademyBlogPost post = postRepository.findById(postId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
    if (!post.getAcademy().getAcademyId().equals(academy.getAcademyId())) {
      throw new AccessDeniedException("자기 Academy 글만 수정할 수 있습니다.");
    }
    NormalizedRequest normalized = normalizeAndValidate(request, academy.getAcademyId(), postId);
    try {
      AuditorContextHolder.set(principal.loginId());
      post.update(normalized.title(), normalized.slug(), normalized.summary(), normalized.contentJson(),
          normalized.contentHtml(), normalized.metaTitle(), normalized.metaDescription(),
          normalized.status(), normalized.visible());
      attachSources(academy, post, normalized.sourceIds());
      return toResponse(post);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void delete(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    Academy academy = requireMine(principal);
    AcademyBlogPost post = postRepository.findById(postId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "블로그 글을 찾을 수 없습니다."));
    if (!post.getAcademy().getAcademyId().equals(academy.getAcademyId())) {
      throw new AccessDeniedException("자기 Academy 글만 삭제할 수 있습니다.");
    }
    List<AcademyBlogSource> sources = sourceRepository.findByPost_PostIdOrderBySortOrderAsc(postId);
    List<String> storageKeys = sources.stream().map(AcademyBlogSource::getStorageKey).toList();
    // 자식(source)을 먼저 지워 post 참조를 끊은 뒤 post를 삭제해야 flush 시 transient 참조 예외가 안 난다.
    sourceRepository.deleteAll(sources);
    postRepository.delete(post);
    storageKeys.forEach(key -> fileStorage.delete(key, "IMAGE"));
  }

  public List<AcademyBlogPostResponse> listPublic(String academySlug) {
    ensurePublicAcademy(academySlug);
    return withLikeMetadata(postRepository.findByAcademy_SlugAndStatusAndVisibleTrueOrderByPublishedAtDesc(
        academySlug, AcademyBlogPostStatus.PUBLISHED));
  }

  public List<AcademyBlogPostResponse> searchPublicPosts(String keyword, String sort) {
    String normalizedKeyword = trimToNull(keyword);
    List<AcademyBlogPost> posts = "recommended".equalsIgnoreCase(sort)
        ? postRepository.searchPublicRecommended(AcademyBlogPostStatus.PUBLISHED, normalizedKeyword, recommendationWindowStart())
        : postRepository.searchPublicLatest(AcademyBlogPostStatus.PUBLISHED, normalizedKeyword);
    return withLikeMetadata(posts);
  }

  @Transactional
  public AcademyBlogPostResponse toggleLike(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 후 좋아요를 누를 수 있습니다.");
    }
    AcademyBlogPost post = postRepository.findById(postId)
        .filter(p -> p.isPublicVisible() && p.getAcademy().getStatus() == AcademyStatus.PUBLISHED && p.getAcademy().isVisible())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 글을 찾을 수 없습니다."));
    if (!isLikeAllowed(post)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "좋아요 가능 기간이 지난 글입니다.");
    }
    Optional<AcademyBlogPostLike> existing = likeRepository.findByPost_PostIdAndUser_UserId(postId, principal.userId());
    if (existing.isPresent()) {
      likeRepository.delete(existing.get());
    } else {
      User user = userRepository.findById(principal.userId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
      likeRepository.save(new AcademyBlogPostLike(post, user));
    }
    return toResponse(post);
  }

  public AcademyBlogPostResponse getPublic(String academySlug, String postSlug) {
    ensurePublicAcademy(academySlug);
    AcademyBlogPost post = postRepository.findByAcademy_SlugAndSlugAndStatusAndVisibleTrue(
            academySlug, postSlug, AcademyBlogPostStatus.PUBLISHED)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 글을 찾을 수 없습니다."));
    return toResponse(post);
  }

  @Transactional
  public AcademyBlogSourceResponse uploadImage(JwtTokenProvider.AuthenticatedPrincipal principal, MultipartFile image) {
    Academy academy = requireMine(principal);
    if (image == null || image.isEmpty()) throw new IllegalArgumentException("이미지를 선택해 주세요.");
    try {
      ImageSanitizer.SanitizedImage sanitized = imageSanitizer.sanitize(image);
      String storageKey = fileStorage.saveImage(
          principal.userId(), image.getOriginalFilename(), sanitized.bytes(), sanitized.extension());
      AcademyBlogSource source = AcademyBlogSource.builder()
          .academy(academy)
          .sourceType(AcademyBlogSourceType.IMAGE)
          .originalFilename(Optional.ofNullable(image.getOriginalFilename()).orElse("image"))
          .storedFilename(storageKey.substring(Math.max(0, storageKey.lastIndexOf('/') + 1)))
          .storageKey(storageKey)
          .contentType(sanitized.contentType())
          .fileSize(sanitized.bytes().length)
          .sortOrder(0)
          .build();
      AcademyBlogSource saved = sourceRepository.save(source);
      saved.attachTo(null, 0, publicImageUrl(academy.getSlug(), saved.getSourceId()));
      return AcademyBlogSourceResponse.from(saved);
    } catch (IOException e) {
      throw new UncheckedIOException("블로그 이미지 업로드 실패", e);
    }
  }

  public ImageBundle readImage(String academySlug, Long sourceId) {
    AcademyBlogSource source = sourceRepository.findBySourceIdAndAcademy_Slug(sourceId, academySlug)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."));
    if (!canReadSource(source)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");
    }
    try {
      return new ImageBundle(source.getOriginalFilename(), source.getContentType(), fileStorage.readPage(source.getStorageKey(), "IMAGE", 1));
    } catch (IOException e) {
      throw new UncheckedIOException("블로그 이미지 조회 실패", e);
    }
  }

  public List<AcademyBlogPostResponse> listPublishedForSitemap() {
    // MVP 규모에서는 공개 Academy 목록을 순회하지 않고, public API와 동일한 공개 글 목록만 노출한다.
    return postRepository.findAll().stream()
        .filter(AcademyBlogPost::isPublicVisible)
        .filter(p -> p.getAcademy().getStatus() == AcademyStatus.PUBLISHED && p.getAcademy().isVisible())
        .map(this::toResponse)
        .toList();
  }

  private boolean canReadSource(AcademyBlogSource source) {
    AcademyBlogPost post = source.getPost();
    if (post != null && post.isPublicVisible() && post.getAcademy().isVisible()
        && post.getAcademy().getStatus() == AcademyStatus.PUBLISHED) return true;
    return authResolver.currentPrincipal()
        .filter(p -> "TUTOR".equals(p.role()))
        .map(p -> source.getAcademy().getTutor().getUserId().equals(p.userId()))
        .orElse(false);
  }

  private Academy requireMine(JwtTokenProvider.AuthenticatedPrincipal principal) {
    authResolver.requireTutor(principal);
    Academy academy = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("Academy를 먼저 생성해 주세요."));
    authResolver.verifyOwnsAcademy(academy);
    return academy;
  }

  private void ensurePublicAcademy(String academySlug) {
    academyRepository.findBySlugAndStatusAndVisibleTrue(academySlug, AcademyStatus.PUBLISHED)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 Academy를 찾을 수 없습니다."));
  }

  private List<AcademyBlogPostResponse> withLikeMetadata(List<AcademyBlogPost> posts) {
    if (posts == null || posts.isEmpty()) return List.of();
    List<Long> postIds = posts.stream().map(AcademyBlogPost::getPostId).toList();
    Map<Long, Long> totalCounts = toCountMap(likeRepository.countTotalByPostIds(postIds));
    Map<Long, Long> recentCounts = toCountMap(likeRepository.countRecentByPostIds(postIds, recommendationWindowStart()));
    Optional<Long> currentUserId = authResolver.currentPrincipal().map(JwtTokenProvider.AuthenticatedPrincipal::userId);
    return posts.stream().map(post -> {
      boolean likedByMe = currentUserId
          .map(userId -> likeRepository.existsByPost_PostIdAndUser_UserId(post.getPostId(), userId))
          .orElse(false);
      return toResponse(post,
          totalCounts.getOrDefault(post.getPostId(), 0L),
          recentCounts.getOrDefault(post.getPostId(), 0L),
          likedByMe);
    }).toList();
  }

  private Map<Long, Long> toCountMap(List<Object[]> rows) {
    Map<Long, Long> counts = new HashMap<>();
    for (Object[] row : rows) {
      counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }
    return counts;
  }

  private AcademyBlogPostResponse toResponse(AcademyBlogPost post) {
    long likeCount = likeRepository.countByPost_PostId(post.getPostId());
    boolean likedByMe = authResolver.currentPrincipal()
        .map(p -> likeRepository.existsByPost_PostIdAndUser_UserId(post.getPostId(), p.userId()))
        .orElse(false);
    return toResponse(post, likeCount, 0, likedByMe);
  }

  private AcademyBlogPostResponse toResponse(AcademyBlogPost post, long likeCount, long recentLikeCount, boolean likedByMe) {
    List<AcademyBlogSourceResponse> sources = sourceRepository.findByPost_PostIdOrderBySortOrderAsc(post.getPostId())
        .stream().map(AcademyBlogSourceResponse::from).toList();
    return AcademyBlogPostResponse.of(post, sources, likeCount, recentLikeCount, likedByMe, isLikeAllowed(post), likeAllowedUntil(post));
  }

  private boolean isLikeAllowed(AcademyBlogPost post) {
    LocalDateTime base = post.getPublishedAt() != null ? post.getPublishedAt() : post.getCreatedAt();
    return base != null && !LocalDateTime.now().isAfter(base.plusDays(properties.likeAllowedDays()));
  }

  private LocalDateTime likeAllowedUntil(AcademyBlogPost post) {
    LocalDateTime base = post.getPublishedAt() != null ? post.getPublishedAt() : post.getCreatedAt();
    return base == null ? null : base.plusDays(properties.likeAllowedDays());
  }

  private LocalDateTime recommendationWindowStart() {
    return LocalDateTime.now().minusDays(properties.recommendationWindowDays());
  }

  private void attachSources(Academy academy, AcademyBlogPost post, List<Long> sourceIds) {
    if (sourceIds == null) sourceIds = List.of();
    List<Long> distinct = sourceIds.stream().filter(Objects::nonNull).distinct().toList();
    if (distinct.isEmpty()) return;
    List<AcademyBlogSource> sources = sourceRepository.findBySourceIdIn(distinct);
    Map<Long, AcademyBlogSource> byId = new HashMap<>();
    for (AcademyBlogSource source : sources) byId.put(source.getSourceId(), source);
    for (int i = 0; i < distinct.size(); i++) {
      AcademyBlogSource source = byId.get(distinct.get(i));
      if (source == null) continue;
      if (!source.getAcademy().getAcademyId().equals(academy.getAcademyId())) {
        throw new AccessDeniedException("자기 Academy 이미지가 아닙니다.");
      }
      source.attachTo(post, i, publicImageUrl(academy.getSlug(), source.getSourceId()));
    }
  }

  private NormalizedRequest normalizeAndValidate(AcademyBlogPostRequest request, Long academyId, Long postId) {
    if (request == null) throw new IllegalArgumentException("글 정보가 필요합니다.");
    String title = trim(request.title());
    if (isBlank(title)) throw new IllegalArgumentException("제목은 필수입니다.");
    if (title.length() > MAX_TITLE_LENGTH) throw new IllegalArgumentException("제목은 200자 이하로 입력해 주세요.");
    String slug = slugify(request.slug());
    if (isBlank(slug)) slug = slugify(title);
    if (isBlank(slug) || slug.length() > MAX_SLUG_LENGTH || !SLUG_PATTERN.matcher(slug).matches()) {
      throw new IllegalArgumentException("URL 슬러그는 한글/영문/숫자/하이픈으로 입력해 주세요.");
    }
    boolean exists = postId == null
        ? postRepository.existsByAcademy_AcademyIdAndSlug(academyId, slug)
        : postRepository.existsByAcademy_AcademyIdAndSlugAndPostIdNot(academyId, slug, postId);
    if (exists) throw new IllegalArgumentException("이미 사용 중인 URL 슬러그입니다.");
    String contentJson = blankToDefaultJson(request.contentJson());
    String contentHtml = sanitizer.sanitize(request.contentHtml());
    if (isBlank(contentHtml)) throw new IllegalArgumentException("본문은 필수입니다.");
    AcademyBlogPostStatus status = request.status() == null ? AcademyBlogPostStatus.DRAFT : request.status();
    boolean visible = request.visible() == null || request.visible();
    return new NormalizedRequest(title, slug, trimToNull(request.summary()), contentJson, contentHtml,
        trimToNull(request.metaTitle()), trimToNull(request.metaDescription()), status, visible,
        request.sourceIds() == null ? List.of() : request.sourceIds());
  }

  private String publicImageUrl(String academySlug, Long sourceId) {
    return "/api/academies/" + academySlug + "/blog-sources/" + sourceId + "/file";
  }

  private String blankToDefaultJson(String value) { return isBlank(value) ? "{\"type\":\"doc\",\"content\":[]}" : value.trim(); }
  private String slugify(String value) {
    if (value == null) return "";
    return value.trim().toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9가-힣]+", "-")
        .replaceAll("^-+|-+$", "");
  }
  private String trim(String value) { return value == null ? null : value.trim(); }
  private String trimToNull(String value) { String v = trim(value); return isBlank(v) ? null : v; }
  private boolean isBlank(String value) { return value == null || value.isBlank(); }

  private record NormalizedRequest(String title, String slug, String summary, String contentJson, String contentHtml,
      String metaTitle, String metaDescription, AcademyBlogPostStatus status, boolean visible, List<Long> sourceIds) {}
  public record ImageBundle(String filename, String contentType, byte[] bytes) {}
}
