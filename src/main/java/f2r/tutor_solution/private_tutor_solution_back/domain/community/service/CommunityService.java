package f2r.tutor_solution.private_tutor_solution_back.domain.community.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academyblog.service.AcademyBlogSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.config.CommunityProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.Answer;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.BoardType;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostLike;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.AnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostLikeRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 공개 커뮤니티 Q&A. 질문(CommunityPost) → 답변(Answer) 2계층 (댓글 없음).
 * 읽기=anyopen, 작성/답변/like=로그인, 수정=작성자, 삭제=작성자/Admin soft-hide.
 *
 * <p>404/403: 숨김/미존재 대상 write/update/delete/like → 404; visible+wrong-author → 403.
 * <p>transitive 가시성: 답변=answer∧answer.question.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

  private static final int MAX_TITLE = 150;
  private static final String EMPTY_DOC_JSON = "{\"type\":\"doc\",\"content\":[]}";

  /** F2R 커뮤니티(/community) 공간 게시판. */
  private static final List<BoardType> COMMUNITY_BOARDS =
      List.of(BoardType.FREE, BoardType.QNA, BoardType.SUBJECT);
  /** 공개 BuildIt Wiki(/wiki) 공간 게시판. */
  private static final List<BoardType> WIKI_BOARDS =
      List.of(BoardType.EXAM_REVIEW, BoardType.VARIANT, BoardType.MATERIAL);

  private final CommunityPostRepository postRepository;
  private final AnswerRepository answerRepository;
  private final CommunityPostLikeRepository likeRepository;
  private final UserRepository userRepository;
  private final SubjectRepository subjectRepository;
  private final AuthenticatedUserResolver authResolver;
  private final CommunityProperties properties;
  private final AcademyBlogSanitizer sanitizer;
  private final CommunityPostAttachmentService attachmentService;

  /* ── read (anyopen) ─────────────────────────────────────── */

  public List<CommunityPostListResponse> list(String scope, String boardType, Integer subjectId, String keyword, String sort) {
    List<BoardType> scopeBoards = resolveScope(scope);
    BoardType board = parseBoardFilter(boardType);
    if (board != null && !scopeBoards.contains(board)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 게시판은 요청한 공간에 속하지 않습니다: " + boardType);
    }
    Integer subj = board == BoardType.SUBJECT ? subjectId : null;  // 과목 탭에서만 subjectId 의미
    String kw = trimToNull(keyword);
    List<CommunityPost> posts = "recommended".equalsIgnoreCase(sort)
        ? postRepository.searchRecommended(scopeBoards, board, subj, kw, recommendationWindowStart())
        : postRepository.searchLatest(scopeBoards, board, subj, kw);
    if (posts.isEmpty()) return List.of();

    List<Long> ids = posts.stream().map(CommunityPost::getPostId).toList();
    Map<Long, Long> totals = toCountMap(likeRepository.countTotalByPostIds(ids));
    Map<Long, Long> recents = toCountMap(likeRepository.countRecentByPostIds(ids, recommendationWindowStart()));
    Map<Long, Long> answerCounts = toCountMap(answerRepository.countVisibleByQuestionIds(ids));
    Optional<Long> me = currentUserId();

    return posts.stream().map(p -> {
      long pid = p.getPostId();
      return CommunityPostListResponse.of(p,
          answerCounts.getOrDefault(pid, 0L),
          totals.getOrDefault(pid, 0L),
          recents.getOrDefault(pid, 0L),
          me.map(uid -> likeRepository.existsByPost_PostIdAndUser_UserId(pid, uid)).orElse(false),
          isLikeAllowed(p),
          likeAllowedUntil(p));
    }).toList();
  }

  public CommunityPostDetailResponse detail(Long postId) {
    CommunityPost post = postRepository.findVisibleDetailById(postId).orElseThrow(this::postNotFound);

    List<AnswerResponse> answerResponses = answerRepository.findVisibleByQuestionId(postId)
        .stream().map(AnswerResponse::of).toList();

    return CommunityPostDetailResponse.of(post,
        likeRepository.countByPost_PostId(postId),
        likedByMe(postId),
        isLikeAllowed(post),
        likeAllowedUntil(post),
        answerResponses,
        attachmentService.listForPost(postId));
  }

  /* ── question write ─────────────────────────────────────── */

  @Transactional
  public CommunityPostDetailResponse createPost(JwtTokenProvider.AuthenticatedPrincipal principal, CommunityPostRequest req) {
    User author = requireUser(principal);
    ResolvedBoard rb = resolveBoard(req == null ? null : req.boardType(), req == null ? null : req.subjectId());
    String title = requireLength(req == null ? null : req.title(), MAX_TITLE, "제목");
    Body body = normalizeBody(req == null ? null : req.contentJson(), req == null ? null : req.contentHtml());
    try {
      AuditorContextHolder.set(principal.loginId());
      CommunityPost saved = postRepository.save(CommunityPost.builder()
          .author(author).boardType(rb.boardType()).subject(rb.subject())
          .title(title).contentJson(body.json()).contentHtml(body.html()).build());
      return detail(saved.getPostId());
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public CommunityPostDetailResponse updatePost(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId, CommunityPostRequest req) {
    User actor = requireUser(principal);
    CommunityPost post = loadVisiblePost(postId);
    requireAuthor(post.getAuthor(), actor);
    ResolvedBoard rb = resolveBoard(req == null ? null : req.boardType(), req == null ? null : req.subjectId());
    String title = requireLength(req == null ? null : req.title(), MAX_TITLE, "제목");
    Body body = normalizeBody(req == null ? null : req.contentJson(), req == null ? null : req.contentHtml());
    try {
      AuditorContextHolder.set(principal.loginId());
      post.update(rb.boardType(), rb.subject(), title, body.json(), body.html());
      return detail(postId);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void deletePost(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    User actor = requireUser(principal);
    CommunityPost post = loadVisiblePost(postId);
    requireAuthorOrAdmin(post.getAuthor(), actor, principal);
    try {
      AuditorContextHolder.set(principal.loginId());
      post.hide(actor);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /* ── answer write ───────────────────────────────────────── */

  @Transactional
  public CommunityPostDetailResponse createAnswer(JwtTokenProvider.AuthenticatedPrincipal principal, Long questionId, AnswerRequest req) {
    User author = requireUser(principal);
    CommunityPost question = loadVisiblePost(questionId);  // 숨김/미존재 질문 → 404
    Body body = normalizeBody(req == null ? null : req.contentJson(), req == null ? null : req.contentHtml());
    try {
      AuditorContextHolder.set(principal.loginId());
      answerRepository.save(Answer.builder()
          .question(question).author(author).contentJson(body.json()).contentHtml(body.html()).build());
      return detail(questionId);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public CommunityPostDetailResponse updateAnswer(JwtTokenProvider.AuthenticatedPrincipal principal, Long answerId, AnswerRequest req) {
    User actor = requireUser(principal);
    Answer answer = loadVisibleAnswer(answerId);
    requireAuthor(answer.getAuthor(), actor);
    Body body = normalizeBody(req == null ? null : req.contentJson(), req == null ? null : req.contentHtml());
    try {
      AuditorContextHolder.set(principal.loginId());
      answer.update(body.json(), body.html());
      return detail(answer.getQuestion().getPostId());
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void deleteAnswer(JwtTokenProvider.AuthenticatedPrincipal principal, Long answerId) {
    User actor = requireUser(principal);
    Answer answer = loadVisibleAnswer(answerId);
    requireAuthorOrAdmin(answer.getAuthor(), actor, principal);
    try {
      AuditorContextHolder.set(principal.loginId());
      answer.hide(actor);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /* ── like (toggle, 기간 유지) ───────────────────────────── */

  @Transactional
  public CommunityPostDetailResponse toggleLike(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    User user = requireUser(principal);
    CommunityPost post = loadVisiblePost(postId);
    if (!isLikeAllowed(post)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "추천 가능 기간이 지난 질문입니다.");
    }
    Optional<CommunityPostLike> existing = likeRepository.findByPost_PostIdAndUser_UserId(postId, user.getUserId());
    if (existing.isPresent()) likeRepository.delete(existing.get());
    else likeRepository.save(new CommunityPostLike(post, user));
    return detail(postId);
  }

  /* ── helpers ────────────────────────────────────────────── */

  private CommunityPost loadVisiblePost(Long postId) {
    CommunityPost post = postRepository.findById(postId).orElseThrow(this::postNotFound);
    if (!post.isVisible()) throw postNotFound();
    return post;
  }

  /** 답변 또는 그 질문이 숨김이면 404. */
  private Answer loadVisibleAnswer(Long answerId) {
    Answer answer = answerRepository.findById(answerId).orElseThrow(this::answerNotFound);
    if (!answer.isVisible() || !answer.getQuestion().isVisible()) throw answerNotFound();
    return answer;
  }

  private void requireAuthor(User owner, User actor) {
    if (!owner.getUserId().equals(actor.getUserId())) {
      throw new AccessDeniedException("작성자만 수정할 수 있습니다.");
    }
  }

  private void requireAuthorOrAdmin(User owner, User actor, JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!owner.getUserId().equals(actor.getUserId()) && !isAdmin(principal)) {
      throw new AccessDeniedException("작성자 또는 관리자만 삭제할 수 있습니다.");
    }
  }

  private User requireUser(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    return userRepository.findById(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
  }

  private boolean isAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.ADMIN.name().equals(principal.role());
  }

  private boolean likedByMe(Long postId) {
    return currentUserId().map(uid -> likeRepository.existsByPost_PostIdAndUser_UserId(postId, uid)).orElse(false);
  }

  private Optional<Long> currentUserId() {
    return authResolver.currentPrincipal().map(JwtTokenProvider.AuthenticatedPrincipal::userId);
  }

  private boolean isLikeAllowed(CommunityPost post) {
    LocalDateTime base = post.getCreatedAt();
    return base != null && !LocalDateTime.now().isAfter(base.plusDays(properties.likeAllowedDays()));
  }

  private LocalDateTime likeAllowedUntil(CommunityPost post) {
    LocalDateTime base = post.getCreatedAt();
    return base == null ? null : base.plusDays(properties.likeAllowedDays());
  }

  private LocalDateTime recommendationWindowStart() {
    return LocalDateTime.now().minusDays(properties.recommendationWindowDays());
  }

  private Map<Long, Long> toCountMap(List<Object[]> rows) {
    Map<Long, Long> counts = new HashMap<>();
    for (Object[] row : rows) counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    return counts;
  }

  /** 목록 공간 — null/blank/"community"면 F2R 커뮤니티 게시판, "wiki"면 공개 Wiki 게시판. 그 외 400. */
  private List<BoardType> resolveScope(String scope) {
    String s = trimToNull(scope);
    if (s == null || "community".equalsIgnoreCase(s)) return COMMUNITY_BOARDS;
    if ("wiki".equalsIgnoreCase(s)) return WIKI_BOARDS;
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 scope: " + scope);
  }

  /** 목록 필터용 — null/blank면 전체(null 반환). 잘못된 값이면 400. */
  private BoardType parseBoardFilter(String v) {
    String t = trimToNull(v);
    if (t == null) return null;
    try {
      return BoardType.valueOf(t);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 게시판: " + v);
    }
  }

  private record ResolvedBoard(BoardType boardType, Subject subject) {}

  /**
   * 작성/수정용 board 해석. boardType 필수. SUBJECT면 subjectId 필수 + 존재하는 과목,
   * FREE/QNA면 subject=null(subjectId 무시).
   */
  private ResolvedBoard resolveBoard(String boardTypeRaw, Integer subjectId) {
    String t = trimToNull(boardTypeRaw);
    if (t == null) throw new IllegalArgumentException("게시판은 필수입니다.");
    BoardType board;
    try {
      board = BoardType.valueOf(t);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("유효하지 않은 게시판: " + boardTypeRaw);
    }
    if (board != BoardType.SUBJECT) return new ResolvedBoard(board, null);
    if (subjectId == null) throw new IllegalArgumentException("과목 게시판은 과목 선택이 필수입니다.");
    Subject subject = subjectRepository.findById(subjectId)
        .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 과목: " + subjectId));
    return new ResolvedBoard(board, subject);
  }

  private record Body(String json, String html) {}

  /** Tiptap 본문 정규화: html sanitize + 필수 검증, json 기본값 보정. */
  private Body normalizeBody(String json, String html) {
    String sanitized = sanitizer.sanitize(html);
    if (sanitized == null || sanitized.isBlank()) throw new IllegalArgumentException("내용은 필수입니다.");
    String j = (json == null || json.isBlank()) ? EMPTY_DOC_JSON : json.trim();
    return new Body(j, sanitized);
  }

  private ResponseStatusException postNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "질문을 찾을 수 없습니다.");
  }

  private ResponseStatusException answerNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "답변을 찾을 수 없습니다.");
  }

  private String requireLength(String value, int max, String label) {
    String v = trimToNull(value);
    if (v == null) throw new IllegalArgumentException(label + "은 필수입니다.");
    if (v.length() > max) throw new IllegalArgumentException(label + "은 " + max + "자 이하로 입력해 주세요.");
    return v;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
