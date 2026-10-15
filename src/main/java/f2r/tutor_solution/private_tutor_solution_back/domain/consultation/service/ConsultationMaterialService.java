package f2r.tutor_solution.private_tutor_solution_back.domain.consultation.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.consultation.dto.ConsultationMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.service.SourceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 상담 피드백 자료 — conversation에 직접 매핑되는(숙제 무관) 파일 자료의 업로드/삭제.
 *
 * <p>WrongNoteService를 선례로 하되, 별도 엔티티 없이 Source + ConversationSource만 사용한다.
 * 조회/썸네일/다운로드는 기존 conversation detail + {@code GET /api/sources/{id}/file|download}를
 * 재활용하므로 여기서는 다루지 않는다.
 *
 * <p>권한: 강사=본인 담당 conversation 업로드+삭제, 학생=조회/다운로드만(여기 진입 차단),
 * Admin=전부. Update 없음(재업로드). 삭제는 물리삭제.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationMaterialService {

  private final ConversationRepository conversationRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final SourceRepository sourceRepository;
  private final UserRepository userRepository;
  private final SourceService sourceService;
  private final AuthenticatedUserResolver authResolver;

  /**
   * 강사/Admin이 conversation에 상담자료 파일을 업로드한다.
   * Source(type=CONSULTATION, status=COMPLETED) + ConversationSource 매핑을 생성한다.
   */
  @Transactional
  public List<ConsultationMaterialResponse> upload(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long conversationId,
      List<MultipartFile> files) {
    // 학생 차단(R 전용) — 강사/Admin만 업로드.
    authResolver.verifyAdminOrTutor();
    // conversation 소유 검증(3축): 강사는 본인 담당 conversation만.
    Conversation conversation = loadAccessibleConversation(conversationId);
    validateFiles(files);

    User uploader = userRepository.findById(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "업로더를 찾을 수 없습니다."));

    List<Source> sources = files.stream()
        .map(file -> sourceService.saveManualFile(file, uploader, SourceType.CONSULTATION, ContentKind.GENERAL))
        .toList();
    sources.forEach(source -> conversationSourceRepository.save(ConversationSource.builder()
        .conversationId(conversation.getConversationId())
        .sourceId(source.getSourceId())
        .build()));

    return sources.stream()
        .map(source -> ConsultationMaterialResponse.of(source, sourceService.pageCountOf(source)))
        .toList();
  }

  /**
   * 상담자료 물리삭제. C1 IDOR 가드 순서 고정:
   * <ol>
   *   <li>verifyOwnsConversation(3축) — loadAccessibleConversation</li>
   *   <li>verifyAdminOrTutor — 학생 삭제 차단</li>
   *   <li>existsByConversationIdAndSourceId == false → 404 (타 conversation source 삭제 차단, 미존재도 자연 차단)</li>
   *   <li>sourceType != CONSULTATION → 404 (시험지/TUTOR_MATERIAL 등을 상담 삭제 경로로 지우는 것 차단)</li>
   *   <li>위 가드 통과 시에만 deleteTrusted (owner 검증 skip 물리삭제)</li>
   * </ol>
   * {@link SourceService#deleteTrusted(Long)}는 owner 검증을 skip하므로 위 가드가 유일 방어선이다.
   */
  @Transactional
  public void delete(Long conversationId, Long sourceId) {
    loadAccessibleConversation(conversationId);
    authResolver.verifyAdminOrTutor();

    if (!conversationSourceRepository.existsByConversationIdAndSourceId(conversationId, sourceId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상담자료를 찾을 수 없습니다.");
    }
    Source source = sourceRepository.findById(sourceId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상담자료를 찾을 수 없습니다."));
    if (source.getSourceType() != SourceType.CONSULTATION) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "상담자료를 찾을 수 없습니다.");
    }
    sourceService.deleteTrusted(sourceId);
  }

  private Conversation loadAccessibleConversation(Long conversationId) {
    if (conversationId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId가 필요합니다.");
    }
    Conversation conversation = conversationRepository.findById(conversationId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."));
    authResolver.verifyOwnsConversation(conversation);
    return conversation;
  }

  private void validateFiles(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("상담 자료 파일이 필요합니다.");
    }
    files.forEach(this::validateFile);
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("상담 자료 파일이 필요합니다.");
    }
    String contentType = file.getContentType();
    boolean supported = contentType != null
        && (contentType.startsWith("image/") || "application/pdf".equals(contentType));
    if (!supported) {
      throw new IllegalArgumentException("이미지 또는 PDF만 업로드할 수 있습니다.");
    }
  }
}
