package f2r.tutor_solution.private_tutor_solution_back.domain.community.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.dto.CommunityAttachmentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.BoardType;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostAttachment;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostAttachmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.community.repository.CommunityPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 커뮤니티 게시글 문서 첨부. 변형문제(VARIANT) / 자료 공유·요청(MATERIAL) 게시판만 허용한다.
 * 업로드/삭제는 작성자(삭제는 ADMIN 포함), 다운로드는 공개(글이 보일 때).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostAttachmentService {

  private static final String RAW_FILE_TYPE = "UNKNOWN";
  private static final int MAX_ATTACHMENTS = 10;
  private static final Set<BoardType> ATTACHMENT_BOARDS = EnumSet.of(BoardType.VARIANT, BoardType.MATERIAL);

  private final CommunityPostAttachmentRepository attachmentRepository;
  private final CommunityPostRepository postRepository;
  private final UserRepository userRepository;
  private final FileStorage fileStorage;
  private final DocumentUploadValidator documentUploadValidator;

  @Value("${app.upload.max-document-bytes:20971520}")
  private long maxDocumentBytes;

  public record AttachmentFileBundle(byte[] bytes, String contentType, String filename) {}

  public List<CommunityAttachmentResponse> listForPost(Long postId) {
    return attachmentRepository.findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(postId)
        .stream().map(CommunityAttachmentResponse::of).toList();
  }

  @Transactional
  public List<CommunityAttachmentResponse> addAttachments(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long postId, List<MultipartFile> files) {
    User uploader = requireUser(principal);
    CommunityPost post = loadVisiblePost(postId);
    requireAuthor(post, uploader);
    if (!ATTACHMENT_BOARDS.contains(post.getBoardType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 게시판은 파일을 첨부할 수 없습니다.");
    }

    List<MultipartFile> nonEmpty = files == null ? List.of()
        : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    if (nonEmpty.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "첨부할 파일을 선택해주세요.");
    }
    long existing = attachmentRepository.countByPost_PostIdAndVisibleTrue(postId);
    if (existing + nonEmpty.size() > MAX_ATTACHMENTS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "첨부파일은 게시글당 최대 " + MAX_ATTACHMENTS + "개까지 가능합니다.");
    }
    nonEmpty.forEach(documentUploadValidator::validate);

    try {
      AuditorContextHolder.set(principal.loginId());
      for (MultipartFile file : nonEmpty) {
        String name = originalFileName(file);
        String storageKey = saveRaw(uploader, name, file);
        registerRollbackCleanup(storageKey);
        attachmentRepository.save(CommunityPostAttachment.builder()
            .post(post)
            .uploader(uploader)
            .originalFileName(name)
            .contentType(DocumentUploadValidator.contentType(name))
            .fileSize(file.getSize())
            .storageKey(storageKey)
            .build());
      }
    } finally {
      AuditorContextHolder.clear();
    }
    return listForPost(postId);
  }

  @Transactional
  public void deleteAttachment(JwtTokenProvider.AuthenticatedPrincipal principal, Long attachmentId) {
    User actor = requireUser(principal);
    CommunityPostAttachment attachment = attachmentRepository.findWithPostById(attachmentId)
        .orElseThrow(this::attachmentNotFound);
    boolean owner = attachment.getUploader().getUserId().equals(actor.getUserId())
        || attachment.getPost().getAuthor().getUserId().equals(actor.getUserId());
    if (!owner && !isAdmin(principal)) {
      throw new AccessDeniedException("작성자 또는 관리자만 삭제할 수 있습니다.");
    }
    String storageKey = attachment.getStorageKey();
    attachmentRepository.delete(attachment);
    fileStorage.delete(storageKey, RAW_FILE_TYPE);
  }

  public AttachmentFileBundle getAttachmentFile(Long attachmentId) {
    CommunityPostAttachment attachment = attachmentRepository.findVisibleById(attachmentId)
        .orElseThrow(this::attachmentNotFound);
    if (!attachment.getPost().isVisible()) {
      throw attachmentNotFound();
    }
    try {
      byte[] bytes = fileStorage.readSingleLimited(attachment.getStorageKey(), RAW_FILE_TYPE, maxDocumentBytes);
      return new AttachmentFileBundle(bytes, attachment.getContentType(), attachment.getOriginalFileName());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "첨부파일을 읽을 수 없습니다.", e);
    }
  }

  private String saveRaw(User uploader, String name, MultipartFile file) {
    try {
      return fileStorage.saveRaw(uploader.getUserId(), name, file.getInputStream(), file.getSize());
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

  private CommunityPost loadVisiblePost(Long postId) {
    CommunityPost post = postRepository.findById(postId).orElseThrow(this::postNotFound);
    if (!post.isVisible()) {
      throw postNotFound();
    }
    return post;
  }

  private void requireAuthor(CommunityPost post, User actor) {
    if (!post.getAuthor().getUserId().equals(actor.getUserId())) {
      throw new AccessDeniedException("작성자만 첨부할 수 있습니다.");
    }
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

  private String originalFileName(MultipartFile file) {
    String filename = file.getOriginalFilename();
    return (filename == null || filename.isBlank()) ? "attachment" : filename;
  }

  private ResponseStatusException postNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "질문을 찾을 수 없습니다.");
  }

  private ResponseStatusException attachmentNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
  }
}
