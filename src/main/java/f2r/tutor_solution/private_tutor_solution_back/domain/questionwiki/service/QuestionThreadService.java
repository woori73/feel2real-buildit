package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionThreadPostRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiContextResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
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

/**
 * 문항 THREAD 쓰기 — 글/답글 작성·수정·숨김, 변형문제·기출 증거, 첨부.
 *
 * <p>권한은 전부 authenticated principal 기준이다. 작성자 확인은 {@code author.userId == principal.userId} 로만 한다 —
 * request body 의 user id 는 신뢰하지 않는다. 학생 참여가 가능하다고 해서 보안 기준을 낮추지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionThreadService {

  private static final int MAX_TITLE = 200;
  private static final int MAX_CONTENT = 5000;
  private static final int MAX_VARIANT_FIELD = 4000;
  private static final int MAX_ATTACHMENTS = 10;
  private static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;
  private static final String RAW_FILE_TYPE = "UNKNOWN";

  private final QuestionWikiService questionWikiService;
  private final QuestionThreadPostRepository postRepository;
  private final QuestionThreadVariantDetailRepository variantRepository;
  private final QuestionThreadExamEvidenceRepository examEvidenceRepository;
  private final QuestionThreadAttachmentRepository attachmentRepository;
  private final UserRepository userRepository;
  private final FileStorage fileStorage;
  private final DocumentUploadValidator documentUploadValidator;

  @Transactional
  public QuestionWikiContextResponse createPost(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long questionWikiId,
      QuestionThreadPostRequest request) {
    User author = requireUser(principal);
    QuestionWiki questionWiki = questionWikiService.loadVisible(questionWikiId);

    QuestionThreadPost parent = resolveParent(request == null ? null : request.parentPostId(), questionWikiId);
    QuestionThreadDataKind dataKind = parent != null
        ? QuestionThreadDataKind.DISCUSSION
        : (request == null || request.dataKind() == null
            ? QuestionThreadDataKind.DISCUSSION : request.dataKind());
    QuestionThreadCategory category = request == null || request.category() == null
        ? QuestionThreadCategory.QUESTION : request.category();

    // 구조화 상세는 글 저장 전에 먼저 검증한다(부분 저장 방지).
    QuestionThreadPostRequest.Variant variant =
        parent == null && dataKind == QuestionThreadDataKind.GENERATED_PRACTICE
            ? requireVariant(request) : null;
    QuestionThreadPostRequest.ExamEvidence evidence =
        parent == null && dataKind == QuestionThreadDataKind.EXAM_EVIDENCE
            ? requireEvidence(request) : null;

    try {
      AuditorContextHolder.set(principal.loginId());
      QuestionThreadPost post = postRepository.save(QuestionThreadPost.builder()
          .questionWiki(questionWiki)
          .parentPost(parent)
          .author(author)
          .category(category)
          .dataKind(dataKind)
          .title(trimToNull(request == null ? null : request.title(), "제목", MAX_TITLE))
          .content(requireText(request == null ? null : request.content(), "내용", MAX_CONTENT))
          .build());

      if (variant != null) {
        variantRepository.save(buildVariant(post, variant));
      }
      if (evidence != null) {
        examEvidenceRepository.save(buildEvidence(post, evidence));
      }
      return questionWikiService.getContext(questionWikiId, principal);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public QuestionWikiContextResponse updatePost(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long postId,
      QuestionThreadPostRequest request) {
    User actor = requireUser(principal);
    QuestionThreadPost post = loadVisiblePost(postId);
    if (!post.getAuthor().getUserId().equals(actor.getUserId())) {
      throw new AccessDeniedException("작성자만 글을 수정할 수 있습니다.");
    }
    try {
      AuditorContextHolder.set(principal.loginId());
      QuestionThreadCategory category = request == null ? null : request.category();
      QuestionThreadDataKind dataKind = post.isReply() ? QuestionThreadDataKind.DISCUSSION
          : (request == null ? null : request.dataKind());
      post.update(
          trimToNull(request == null ? null : request.title(), "제목", MAX_TITLE),
          requireText(request == null ? null : request.content(), "내용", MAX_CONTENT),
          category,
          dataKind);

      if (!post.isReply() && post.getDataKind() == QuestionThreadDataKind.GENERATED_PRACTICE) {
        QuestionThreadPostRequest.Variant v = requireVariant(request);
        variantRepository.findByPost_PostId(postId).ifPresentOrElse(
            existing -> existing.update(
                requireText(v.questionText(), "변형문제 지문", MAX_VARIANT_FIELD),
                trimToNull(v.questionType(), "문제 유형", 40),
                cut(v.answer(), MAX_VARIANT_FIELD),
                cut(v.explanation(), MAX_VARIANT_FIELD),
                cut(v.sourceSentence(), MAX_VARIANT_FIELD),
                trimToNull(v.sourceQuestion(), "원 문항", 200),
                trimToNull(v.difficulty(), "난이도", 20)),
            () -> variantRepository.save(buildVariant(post, v)));
      }
      if (!post.isReply() && post.getDataKind() == QuestionThreadDataKind.EXAM_EVIDENCE) {
        QuestionThreadPostRequest.ExamEvidence e = requireEvidence(request);
        examEvidenceRepository.findByPost_PostId(postId).ifPresentOrElse(
            existing -> existing.update(
                requireText(e.schoolName(), "학교", 100),
                trimToNull(e.gradeLabel(), "학년", 20),
                trimToNull(e.examPeriod(), "시험 시기", 60),
                cut(e.relatedSentence(), MAX_VARIANT_FIELD),
                trimToNull(e.examQuestionType(), "출제 유형", 40),
                cut(e.examPoint(), MAX_VARIANT_FIELD),
                cut(e.description(), MAX_VARIANT_FIELD)),
            () -> examEvidenceRepository.save(buildEvidence(post, e)));
      }
      return questionWikiService.getContext(post.getQuestionWiki().getQuestionWikiId(), principal);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public QuestionWikiContextResponse hidePost(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long postId) {
    User actor = requireUser(principal);
    QuestionThreadPost post = loadVisiblePost(postId);
    if (!post.getAuthor().getUserId().equals(actor.getUserId()) && !isAdmin(principal)) {
      throw new AccessDeniedException("작성자 또는 관리자만 삭제할 수 있습니다.");
    }
    try {
      AuditorContextHolder.set(principal.loginId());
      post.hide(actor);
      return questionWikiService.getContext(post.getQuestionWiki().getQuestionWikiId(), principal);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public QuestionWikiContextResponse addAttachments(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long postId, List<MultipartFile> files) {
    User actor = requireUser(principal);
    QuestionThreadPost post = loadVisiblePost(postId);
    if (!post.getAuthor().getUserId().equals(actor.getUserId())) {
      throw new AccessDeniedException("작성자만 첨부를 추가할 수 있습니다.");
    }
    List<MultipartFile> nonEmpty = normalizeFiles(files);
    long existing = attachmentRepository.countByPost_PostIdAndVisibleTrue(postId);
    if (existing + nonEmpty.size() > MAX_ATTACHMENTS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "첨부파일은 글당 최대 " + MAX_ATTACHMENTS + "개까지 가능합니다.");
    }
    try {
      AuditorContextHolder.set(principal.loginId());
      for (MultipartFile file : nonEmpty) {
        documentUploadValidator.validateAttachment(file);
        attachmentRepository.save(toAttachment(actor, post, file));
      }
      return questionWikiService.getContext(post.getQuestionWiki().getQuestionWikiId(), principal);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void deleteAttachment(JwtTokenProvider.AuthenticatedPrincipal principal, Long attachmentId) {
    requireUser(principal);
    QuestionThreadAttachment attachment = attachmentRepository.findVisibleDetailById(attachmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."));
    boolean allowed = attachment.getUploader().getUserId().equals(principal.userId())
        || attachment.getPost().getAuthor().getUserId().equals(principal.userId())
        || isAdmin(principal);
    if (!allowed) {
      throw new AccessDeniedException("첨부파일을 삭제할 권한이 없습니다.");
    }
    try {
      AuditorContextHolder.set(principal.loginId());
      attachment.hide();
    } finally {
      AuditorContextHolder.clear();
    }
  }

  public AttachmentFileBundle getAttachmentFile(Long attachmentId) {
    QuestionThreadAttachment attachment = attachmentRepository.findVisibleDetailById(attachmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."));
    if (!attachment.getPost().isVisible()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
    }
    try {
      byte[] bytes = fileStorage.readPage(attachment.getStorageKey(), RAW_FILE_TYPE, 1);
      return new AttachmentFileBundle(bytes, attachment.getContentType(), attachment.getOriginalFileName());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "첨부파일을 읽을 수 없습니다.", e);
    }
  }

  /* ── helpers ──────────────────────────────────────────────────── */

  private QuestionThreadPost resolveParent(Long parentPostId, Long questionWikiId) {
    if (parentPostId == null) {
      return null;
    }
    QuestionThreadPost parent = loadVisiblePost(parentPostId);
    if (!parent.getQuestionWiki().getQuestionWikiId().equals(questionWikiId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "다른 문항의 글에는 답글을 달 수 없습니다.");
    }
    if (parent.isReply()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글에는 다시 답글을 달 수 없습니다.");
    }
    return parent;
  }

  private QuestionThreadPost loadVisiblePost(Long postId) {
    QuestionThreadPost post = postRepository.findDetailById(postId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다."));
    if (!post.isVisible()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다.");
    }
    return post;
  }

  private QuestionThreadVariantDetail buildVariant(
      QuestionThreadPost post, QuestionThreadPostRequest.Variant v) {
    return QuestionThreadVariantDetail.builder()
        .post(post)
        .questionText(requireText(v.questionText(), "변형문제 지문", MAX_VARIANT_FIELD))
        .questionType(trimToNull(v.questionType(), "문제 유형", 40))
        .answer(cut(v.answer(), MAX_VARIANT_FIELD))
        .explanation(cut(v.explanation(), MAX_VARIANT_FIELD))
        .sourceSentence(cut(v.sourceSentence(), MAX_VARIANT_FIELD))
        .sourceQuestion(trimToNull(v.sourceQuestion(), "원 문항", 200))
        .difficulty(trimToNull(v.difficulty(), "난이도", 20))
        .build();
  }

  private QuestionThreadExamEvidence buildEvidence(
      QuestionThreadPost post, QuestionThreadPostRequest.ExamEvidence e) {
    return QuestionThreadExamEvidence.builder()
        .post(post)
        .schoolName(requireText(e.schoolName(), "학교", 100))
        .gradeLabel(trimToNull(e.gradeLabel(), "학년", 20))
        .examPeriod(trimToNull(e.examPeriod(), "시험 시기", 60))
        .relatedSentence(cut(e.relatedSentence(), MAX_VARIANT_FIELD))
        .examQuestionType(trimToNull(e.examQuestionType(), "출제 유형", 40))
        .examPoint(cut(e.examPoint(), MAX_VARIANT_FIELD))
        .description(cut(e.description(), MAX_VARIANT_FIELD))
        .build();
  }

  private QuestionThreadPostRequest.Variant requireVariant(QuestionThreadPostRequest request) {
    QuestionThreadPostRequest.Variant v = request == null ? null : request.variant();
    if (v == null || v.questionText() == null || v.questionText().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변형문제는 문제 지문이 필요합니다.");
    }
    return v;
  }

  private QuestionThreadPostRequest.ExamEvidence requireEvidence(QuestionThreadPostRequest request) {
    QuestionThreadPostRequest.ExamEvidence e = request == null ? null : request.examEvidence();
    if (e == null || e.schoolName() == null || e.schoolName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기출 증거는 최소한 학교명이 필요합니다.");
    }
    return e;
  }

  private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
    List<MultipartFile> nonEmpty = files == null ? List.of()
        : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    if (nonEmpty.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "첨부할 파일을 선택해 주세요.");
    }
    nonEmpty.stream().filter(f -> f.getSize() > MAX_ATTACHMENT_BYTES).findFirst().ifPresent(f -> {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "첨부파일은 파일당 10MB 이하만 가능합니다.");
    });
    return nonEmpty;
  }

  private QuestionThreadAttachment toAttachment(User uploader, QuestionThreadPost post, MultipartFile file) {
    try {
      String original = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
          ? "attachment" : file.getOriginalFilename();
      String contentType = file.getContentType() == null || file.getContentType().isBlank()
          ? "application/octet-stream" : file.getContentType();
      String storageKey = fileStorage.saveRaw(
          uploader.getUserId(), original, file.getInputStream(), file.getSize());
      registerRollbackCleanup(storageKey);
      return QuestionThreadAttachment.builder()
          .post(post)
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
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
          fileStorage.delete(storageKey, RAW_FILE_TYPE);
        }
      }
    });
  }

  private User requireUser(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return userRepository.findById(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
  }

  private boolean isAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.ADMIN.name().equals(principal.role());
  }

  private String requireText(String value, String label, int max) {
    if (value == null || value.trim().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "을(를) 입력해 주세요.");
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          label + "은(는) 최대 " + max + "자까지 입력할 수 있습니다.");
    }
    return trimmed;
  }

  private String trimToNull(String value, String label, int max) {
    if (value == null || value.trim().isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          label + "은(는) 최대 " + max + "자까지 입력할 수 있습니다.");
    }
    return trimmed;
  }

  private String cut(String value, int max) {
    if (value == null || value.trim().isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
  }

  public record AttachmentFileBundle(byte[] bytes, String contentType, String filename) {}
}
