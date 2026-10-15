package f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.service.SourceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.dto.WrongNoteEntryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.entity.WrongNoteEntry;
import f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.repository.WrongNoteEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WrongNoteService {

  private static final int TITLE_MAX = 150;

  private final WrongNoteEntryRepository wrongNoteEntryRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final UserRepository userRepository;
  private final SourceService sourceService;
  private final AuthenticatedUserResolver authResolver;

  public List<WrongNoteEntryResponse> list(Long conversationId) {
    Conversation conversation = loadAccessibleConversation(conversationId);
    return wrongNoteEntryRepository.findByConversation_ConversationIdOrderByCreatedAtDesc(conversation.getConversationId())
        .stream()
        .map(entry -> WrongNoteEntryResponse.of(entry, sourceService::pageCountOf))
        .toList();
  }

  @Transactional
  public List<WrongNoteEntryResponse> upload(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long conversationId,
      List<MultipartFile> files,
      String title,
      String description) {
    Conversation conversation = loadAccessibleConversation(conversationId);
    validateFiles(files);
    String normalizedTitle = requireLength(title, TITLE_MAX, "제목");
    String normalizedDescription = trimToNull(description);

    User uploader = userRepository.findById(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "업로더를 찾을 수 없습니다."));

    List<Source> sources = files.stream()
        .map(file -> sourceService.saveManualFile(file, uploader, SourceType.WRONG_NOTE, ContentKind.GENERAL))
        .toList();
    sources.forEach(source -> conversationSourceRepository.save(ConversationSource.builder()
        .conversationId(conversation.getConversationId())
        .sourceId(source.getSourceId())
        .build()));

    WrongNoteEntry entry = WrongNoteEntry.builder()
        .conversation(conversation)
        .source(sources.getFirst())
        .uploadedBy(uploader)
        .title(normalizedTitle)
        .memo(normalizedDescription)
        .build();
    for (int i = 0; i < sources.size(); i++) {
      entry.addFile(sources.get(i), i + 1);
    }
    WrongNoteEntry saved = wrongNoteEntryRepository.save(entry);
    return List.of(WrongNoteEntryResponse.of(saved, sourceService::pageCountOf));
  }

  @Transactional
  public void delete(Long conversationId, Long entryId) {
    Conversation conversation = loadAccessibleConversation(conversationId);
    WrongNoteEntry entry = wrongNoteEntryRepository.findById(entryId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "오답노트 항목을 찾을 수 없습니다."));
    if (!entry.getConversation().getConversationId().equals(conversation.getConversationId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "오답노트 항목을 찾을 수 없습니다.");
    }
    List<Long> sourceIds = entry.getFiles().stream()
        .map(file -> file.getSource().getSourceId())
        .distinct()
        .toList();
    List<Long> idsToDelete = sourceIds.isEmpty()
        ? List.of(entry.getSource().getSourceId())
        : sourceIds;
    wrongNoteEntryRepository.delete(entry);
    wrongNoteEntryRepository.flush();
    idsToDelete.forEach(sourceService::deleteTrusted);
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
      throw new IllegalArgumentException("오답 파일이 필요합니다.");
    }
    files.forEach(this::validateFile);
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("오답 파일이 필요합니다.");
    }
    String contentType = file.getContentType();
    boolean supported = contentType != null
        && (contentType.startsWith("image/") || "application/pdf".equals(contentType));
    if (!supported) {
      throw new IllegalArgumentException("이미지 또는 PDF만 업로드할 수 있습니다.");
    }
  }

  private String requireLength(String value, int max, String label) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(label + "은 필수입니다.");
    if (normalized.length() > max) throw new IllegalArgumentException(label + "은 " + max + "자 이하로 입력해야 합니다.");
    return normalized;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
