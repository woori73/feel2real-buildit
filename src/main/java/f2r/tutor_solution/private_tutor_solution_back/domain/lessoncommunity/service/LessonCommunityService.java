package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonCommunityService {
  private static final int MAX_BODY_LENGTH = 5000;
  private static final int MAX_ATTACHMENTS = 10;
  private static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;
  private static final Set<String> DEFAULT_TAB_NAMES = Set.of("전체글", "일반글", "Q&A", "공지");

  private final LessonProductRepository lessonProductRepository;
  private final LessonApplicationRepository lessonApplicationRepository;
  private final StudentRepository studentRepository;
  private final UserRepository userRepository;
  private final LessonCommunityCategoryRepository categoryRepository;
  private final LessonCommunityPostRepository postRepository;
  private final LessonCommunityCommentRepository commentRepository;
  private final LessonCommunityAttachmentRepository attachmentRepository;
  private final FileStorage fileStorage;

  public LessonCommunityContextResponse context(JwtTokenProvider.AuthenticatedPrincipal principal, Long lessonId) {
    LessonProduct lesson = loadLesson(lessonId);
    Access access = requireParticipant(principal, lesson);
    return new LessonCommunityContextResponse(
        lesson.getLessonId(),
        lesson.getName(),
        lesson.getAcademy().getAcademyId(),
        lesson.getAcademy().getName(),
        principal.role(),
        access.tutorOwner(),
        access.admin());
  }

  public List<LessonCommunityCategoryResponse> listCategories(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long lessonId) {
    LessonProduct lesson = loadLesson(lessonId);
    requireParticipant(principal, lesson);
    return categoryRepository.findByLesson_LessonIdAndVisibleTrueOrderBySortOrderAscNameAsc(lessonId)
        .stream().map(LessonCommunityCategoryResponse::from).toList();
  }

  @Transactional
  public LessonCommunityCategoryResponse createCategory(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long lessonId, LessonCommunityCategoryRequest request) {
    LessonProduct lesson = loadLesson(lessonId);
    requireTutorOwnerOrAdmin(principal, lesson);
    String name = requireCustomTabName(lesson.getLessonId(), request == null ? null : request.name(), null);
    LessonCommunityCategory category = LessonCommunityCategory.builder()
        .lesson(lesson)
        .name(name)
        .sortOrder(request == null ? null : request.sortOrder())
        .build();
    try {
      AuditorContextHolder.set(principal.loginId());
      return LessonCommunityCategoryResponse.from(categoryRepository.save(category));
    } finally {
      AuditorContextHolder.clear();
    }
  }


  @Transactional
  public LessonCommunityCategoryResponse updateCategory(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId,
      Long categoryId,
      LessonCommunityCategoryRequest request) {
    LessonProduct lesson = loadLesson(lessonId);
    requireTutorOwnerOrAdmin(principal, lesson);
    LessonCommunityCategory category = categoryRepository.findByCategoryIdAndLesson_LessonId(categoryId, lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "탭을 찾을 수 없습니다."));
    String name = requireCustomTabName(lessonId, request == null ? null : request.name(), categoryId);
    try {
      AuditorContextHolder.set(principal.loginId());
      category.update(name, request == null ? null : request.sortOrder());
      return LessonCommunityCategoryResponse.from(category);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void deleteCategory(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId,
      Long categoryId) {
    LessonProduct lesson = loadLesson(lessonId);
    requireTutorOwnerOrAdmin(principal, lesson);
    LessonCommunityCategory category = categoryRepository.findByCategoryIdAndLesson_LessonId(categoryId, lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "탭을 찾을 수 없습니다."));
    try {
      AuditorContextHolder.set(principal.loginId());
      postRepository.moveCategoryPostsToGeneral(lessonId, categoryId);
      categoryRepository.delete(category);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  public LessonCommunityPostListResponse listPosts(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId,
      String tab,
      Long categoryId) {
    LessonProduct lesson = loadLesson(lessonId);
    requireParticipant(principal, lesson);
    LessonCommunityPostType type = tabToType(tab);
    LessonCommunityPostSummaryResponse pinnedNotice = latestNotice(principal, lessonId, tab);
    Long pinnedNoticeId = pinnedNotice == null ? null : pinnedNotice.postId();
    List<LessonCommunityPostSummaryResponse> posts = postRepository.findVisibleList(lessonId, type, categoryId)
        .stream()
        .filter(p -> !p.getPostId().equals(pinnedNoticeId))
        .filter(p -> canReadPost(principal, p))
        .map(p -> summary(principal, p))
        .toList();
    return new LessonCommunityPostListResponse(pinnedNotice, posts);
  }

  @Transactional
  public LessonCommunityPostDetailResponse createPost(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId,
      LessonCommunityPostRequest request,
      List<MultipartFile> files) {
    LessonProduct lesson = loadLesson(lessonId);
    requireParticipant(principal, lesson);
    LessonCommunityPostType type = request == null || request.postType() == null
        ? LessonCommunityPostType.GENERAL : request.postType();
    if (type == LessonCommunityPostType.NOTICE) {
      requireTutorOwnerOrAdmin(principal, lesson);
    }
    User author = loadUser(principal.userId());
    LessonCommunityCategory category = resolveCategory(lessonId, request == null ? null : request.categoryId());
    LessonCommunityPost post = LessonCommunityPost.builder()
        .lesson(lesson)
        .category(category)
        .author(author)
        .postType(type)
        .title(requireText(request == null ? null : request.title(), "제목", 150))
        .content(requireText(request == null ? null : request.content(), "본문", MAX_BODY_LENGTH))
        .qnaPublic(request != null && request.qnaPublic())
        .build();
    try {
      AuditorContextHolder.set(principal.loginId());
      LessonCommunityPost saved = postRepository.save(post);
      saveAttachmentsForPost(author, saved, files);
      return getPost(principal, saved.getPostId());
    } finally {
      AuditorContextHolder.clear();
    }
  }

  public LessonCommunityPostDetailResponse getPost(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    LessonCommunityPost post = loadPost(postId);
    if (!post.isVisible() || !canReadPost(principal, post)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
    }
    var postAttachments = attachmentRepository.findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(postId)
        .stream().map(LessonCommunityAttachmentResponse::from).toList();
    var comments = commentRepository.findVisibleByPostId(postId).stream()
        .map(c -> LessonCommunityCommentResponse.from(
            c,
            principal.userId(),
            attachmentRepository.findByComment_CommentIdAndVisibleTrueOrderByAttachmentIdAsc(c.getCommentId())
                .stream().map(LessonCommunityAttachmentResponse::from).toList()))
        .toList();
    return LessonCommunityPostDetailResponse.from(
        post,
        principal.userId(),
        canHidePost(principal, post),
        postAttachments,
        comments);
  }

  @Transactional
  public LessonCommunityPostDetailResponse updatePost(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long postId,
      LessonCommunityPostRequest request) {
    LessonCommunityPost post = loadPost(postId);
    ensureVisibleAndReadable(principal, post);
    if (!post.getAuthor().getUserId().equals(principal.userId())) {
      throw new AccessDeniedException("작성자만 글을 수정할 수 있습니다.");
    }
    LessonCommunityCategory category = resolveCategory(post.getLesson().getLessonId(), request == null ? null : request.categoryId());
    try {
      AuditorContextHolder.set(principal.loginId());
      post.update(
          requireText(request == null ? null : request.title(), "제목", 150),
          requireText(request == null ? null : request.content(), "본문", MAX_BODY_LENGTH),
          category,
          request != null && request.qnaPublic());
      return getPost(principal, postId);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void hidePost(JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    LessonCommunityPost post = loadPost(postId);
    ensureVisibleAndReadable(principal, post);
    if (!canHidePost(principal, post)) {
      throw new AccessDeniedException("게시글을 숨김 처리할 권한이 없습니다.");
    }
    try {
      AuditorContextHolder.set(principal.loginId());
      post.hide(loadUser(principal.userId()));
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public LessonCommunityPostDetailResponse createComment(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long postId,
      LessonCommunityCommentRequest request) {
    LessonCommunityPost post = loadPost(postId);
    ensureVisibleAndReadable(principal, post);
    User author = loadUser(principal.userId());
    LessonCommunityComment comment = LessonCommunityComment.builder()
        .post(post)
        .author(author)
        .content(requireText(request == null ? null : request.content(), "댓글", MAX_BODY_LENGTH))
        .build();
    try {
      AuditorContextHolder.set(principal.loginId());
      commentRepository.save(comment);
      return getPost(principal, postId);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public LessonCommunityPostDetailResponse updateComment(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long commentId,
      LessonCommunityCommentRequest request) {
    LessonCommunityComment comment = loadComment(commentId);
    ensureVisibleAndReadable(principal, comment.getPost());
    if (!comment.getAuthor().getUserId().equals(principal.userId())) {
      throw new AccessDeniedException("작성자만 댓글을 수정할 수 있습니다.");
    }
    try {
      AuditorContextHolder.set(principal.loginId());
      comment.update(requireText(request == null ? null : request.content(), "댓글", MAX_BODY_LENGTH));
      return getPost(principal, comment.getPost().getPostId());
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void hideComment(JwtTokenProvider.AuthenticatedPrincipal principal, Long commentId) {
    LessonCommunityComment comment = loadComment(commentId);
    ensureVisibleAndReadable(principal, comment.getPost());
    if (!canHideComment(principal, comment)) {
      throw new AccessDeniedException("댓글을 숨김 처리할 권한이 없습니다.");
    }
    try {
      AuditorContextHolder.set(principal.loginId());
      comment.hide(loadUser(principal.userId()));
    } finally {
      AuditorContextHolder.clear();
    }
  }


  public List<AdminLessonCommunitySummaryResponse> searchAdminCommunities(
      JwtTokenProvider.AuthenticatedPrincipal principal, String keyword) {
    requireAdmin(principal);
    String normalizedKeyword = normalizeKeyword(keyword);
    return lessonProductRepository.searchAdminCommunities(normalizedKeyword)
        .stream()
        .limit(100)
        .map(lesson -> AdminLessonCommunitySummaryResponse.of(
            lesson,
            postRepository.countByLesson_LessonIdAndVisibleTrue(lesson.getLessonId()),
            commentRepository.countVisibleByLessonId(lesson.getLessonId()),
            postRepository.findFirstByLesson_LessonIdAndVisibleTrueOrderByCreatedAtDescPostIdDesc(lesson.getLessonId())
                .orElse(null)))
        .toList();
  }

  public AttachmentFileBundle getAttachmentFile(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long attachmentId) {
    LessonCommunityAttachment attachment = attachmentRepository.findReadableById(attachmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."));
    if (attachment.getComment() != null && !attachment.getComment().isVisible()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
    }
    LessonCommunityPost post = attachment.getPost() != null ? attachment.getPost() : attachment.getComment().getPost();
    ensureVisibleAndReadable(principal, post);
    try {
      byte[] bytes = fileStorage.readPage(attachment.getStorageKey(), "UNKNOWN", 1);
      return new AttachmentFileBundle(bytes, attachment.getContentType(), attachment.getOriginalFileName());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "첨부파일을 읽을 수 없습니다.", e);
    }
  }

  private LessonCommunityPostSummaryResponse latestNotice(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long lessonId, String tab) {
    if ("notice".equalsIgnoreCase(tab)) return null;
    return postRepository
        .findFirstByLesson_LessonIdAndPostTypeAndVisibleTrueOrderByCreatedAtDescPostIdDesc(
            lessonId, LessonCommunityPostType.NOTICE)
        .filter(p -> canReadPost(principal, p))
        .map(p -> summary(principal, p))
        .orElse(null);
  }

  private LessonCommunityPostSummaryResponse summary(
      JwtTokenProvider.AuthenticatedPrincipal principal, LessonCommunityPost post) {
    return LessonCommunityPostSummaryResponse.from(
        post,
        principal.userId(),
        commentRepository.countByPost_PostIdAndVisibleTrue(post.getPostId()),
        attachmentRepository.countByPost_PostIdAndVisibleTrue(post.getPostId()));
  }

  private void ensureVisibleAndReadable(JwtTokenProvider.AuthenticatedPrincipal principal, LessonCommunityPost post) {
    if (!post.isVisible() || !canReadPost(principal, post)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
    }
  }

  private boolean canReadPost(JwtTokenProvider.AuthenticatedPrincipal principal, LessonCommunityPost post) {
    requireParticipant(principal, post.getLesson());
    if (post.getPostType() != LessonCommunityPostType.QNA) return true;
    if (post.isQnaPublic()) return true;
    if (isAdmin(principal) || isTutorOwner(principal, post.getLesson())) return true;
    return post.getAuthor().getUserId().equals(principal.userId());
  }

  private boolean canHidePost(JwtTokenProvider.AuthenticatedPrincipal principal, LessonCommunityPost post) {
    return post.getAuthor().getUserId().equals(principal.userId())
        || isAdmin(principal)
        || isTutorOwner(principal, post.getLesson());
  }

  private boolean canHideComment(JwtTokenProvider.AuthenticatedPrincipal principal, LessonCommunityComment comment) {
    return comment.getAuthor().getUserId().equals(principal.userId())
        || isAdmin(principal)
        || isTutorOwner(principal, comment.getPost().getLesson());
  }

  private LessonProduct loadLesson(Long lessonId) {
    if (lessonId == null) throw new IllegalArgumentException("수업 ID가 필요합니다.");
    return lessonProductRepository.findById(lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업을 찾을 수 없습니다."));
  }

  private LessonCommunityPost loadPost(Long postId) {
    return postRepository.findDetailById(postId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."));
  }

  private LessonCommunityComment loadComment(Long commentId) {
    LessonCommunityComment comment = commentRepository.findDetailById(commentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
    if (!comment.isVisible()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
    }
    return comment;
  }

  private User loadUser(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new AccessDeniedException("사용자 정보가 필요합니다."));
  }

  private LessonCommunityCategory resolveCategory(Long lessonId, Long categoryId) {
    if (categoryId == null) return null;
    return categoryRepository.findByCategoryIdAndLesson_LessonIdAndVisibleTrue(categoryId, lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "탭을 찾을 수 없습니다."));
  }

  private Access requireParticipant(JwtTokenProvider.AuthenticatedPrincipal principal, LessonProduct lesson) {
    if (principal == null) throw new AccessDeniedException("인증이 필요합니다.");
    if (isAdmin(principal)) return new Access(true, false);
    if (isTutorOwner(principal, lesson)) return new Access(false, true);
    if (Role.STUDENT.name().equals(principal.role())) {
      Student student = studentRepository.findByUser_UserId(principal.userId())
          .orElseThrow(() -> new AccessDeniedException("학생 프로필이 필요합니다."));
      boolean approved = lessonApplicationRepository
          .findByStudent_StudentIdAndLesson_LessonId(student.getStudentId(), lesson.getLessonId())
          .filter(a -> a.getStatus() == LessonApplicationStatus.APPROVED)
          .isPresent();
      if (approved) return new Access(false, false);
    }
    throw new AccessDeniedException("수업 커뮤니티 접근 권한이 없습니다.");
  }

  private void requireTutorOwnerOrAdmin(JwtTokenProvider.AuthenticatedPrincipal principal, LessonProduct lesson) {
    if (isAdmin(principal) || isTutorOwner(principal, lesson)) return;
    throw new AccessDeniedException("강사 또는 관리자 권한이 필요합니다.");
  }


  private void requireAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!isAdmin(principal)) {
      throw new AccessDeniedException("관리자 권한이 필요합니다.");
    }
  }

  private String normalizeKeyword(String keyword) {
    if (keyword == null) return null;
    String trimmed = keyword.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  private boolean isAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.ADMIN.name().equals(principal.role());
  }

  private boolean isTutorOwner(JwtTokenProvider.AuthenticatedPrincipal principal, LessonProduct lesson) {
    return principal != null
        && Role.TUTOR.name().equals(principal.role())
        && lesson.getAcademy().getTutor().getUserId().equals(principal.userId());
  }

  private LessonCommunityPostType tabToType(String tab) {
    if (tab == null || tab.isBlank() || "all".equalsIgnoreCase(tab)) return null;
    return switch (tab.trim().toLowerCase(Locale.ROOT)) {
      case "qna" -> LessonCommunityPostType.QNA;
      case "notice" -> LessonCommunityPostType.NOTICE;
      case "general" -> LessonCommunityPostType.GENERAL;
      default -> null;
    };
  }


  private String requireCustomTabName(Long lessonId, String value, Long currentCategoryId) {
    String name = requireText(value, "탭 이름", 50);
    if (DEFAULT_TAB_NAMES.contains(name)) {
      throw new IllegalArgumentException("기본 탭 이름은 커스텀 탭으로 사용할 수 없습니다.");
    }
    boolean duplicate = categoryRepository.existsByLesson_LessonIdAndName(lessonId, name);
    if (!duplicate) return name;
    if (currentCategoryId != null) {
      boolean sameCategory = categoryRepository.findByCategoryIdAndLesson_LessonId(currentCategoryId, lessonId)
          .map(category -> name.equals(category.getName()))
          .orElse(false);
      if (sameCategory) return name;
    }
    throw new IllegalArgumentException("이미 사용 중인 탭 이름입니다.");
  }

  private String requireText(String value, String label, int maxLength) {
    if (value == null || value.trim().isBlank()) {
      throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
    }
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(label + "은(는) 최대 " + maxLength + "자까지 입력할 수 있습니다.");
    }
    return trimmed;
  }

  private void saveAttachmentsForPost(User uploader, LessonCommunityPost post, List<MultipartFile> files) {
    for (MultipartFile file : normalizeFiles(files)) {
      attachmentRepository.save(toAttachment(uploader, post, null, file));
    }
  }

  private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
    if (files == null) return List.of();
    List<MultipartFile> nonEmpty = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    if (nonEmpty.size() > MAX_ATTACHMENTS) {
      throw new IllegalArgumentException("첨부파일은 최대 " + MAX_ATTACHMENTS + "개까지 가능합니다.");
    }
    nonEmpty.stream()
        .filter(f -> f.getSize() > MAX_ATTACHMENT_BYTES)
        .findFirst()
        .ifPresent(f -> {
          throw new IllegalArgumentException("첨부파일은 파일당 10MB 이하만 가능합니다.");
        });
    return nonEmpty;
  }

  private LessonCommunityAttachment toAttachment(
      User uploader, LessonCommunityPost post, LessonCommunityComment comment, MultipartFile file) {
    try {
      String original = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
          ? "attachment" : file.getOriginalFilename();
      String contentType = file.getContentType() == null || file.getContentType().isBlank()
          ? "application/octet-stream" : file.getContentType();
      String storageKey = fileStorage.saveRaw(uploader.getUserId(), original, file.getInputStream(), file.getSize());
      registerRollbackCleanup(storageKey);
      return LessonCommunityAttachment.builder()
          .post(post)
          .comment(comment)
          .uploader(uploader)
          .originalFileName(original)
          .contentType(contentType)
          .fileSize(file.getSize())
          .storageKey(storageKey)
          .build();
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "첨부파일 저장에 실패했습니다.", e);
    }
  }

  private void registerRollbackCleanup(String storageKey) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
          fileStorage.delete(storageKey, "UNKNOWN");
        }
      }
    });
  }

  private record Access(boolean admin, boolean tutorOwner) {}

  public record AttachmentFileBundle(byte[] bytes, String contentType, String filename) {}
}
