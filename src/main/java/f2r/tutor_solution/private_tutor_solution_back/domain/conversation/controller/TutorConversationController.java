package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.SourceUploadRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/tutor/conversations")
@RequiredArgsConstructor
public class TutorConversationController {

  private final ConversationService conversationService;

  @PostMapping
  public ResponseEntity<ConversationSummaryResponse> create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(conversationService.createForTutor(principal.userId()));
  }

  @GetMapping
  public ResponseEntity<List<ConversationSummaryResponse>> list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(conversationService.listForTutor(principal.userId()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ConversationDetailResponse> detail(@PathVariable Long id) {
    return ResponseEntity.ok(conversationService.getTutorDetail(id));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ConversationTitleResponse> updateTitle(
      @PathVariable Long id,
      @RequestBody UpdateConversationTitleRequest request) {
    return ResponseEntity.ok(conversationService.updateTutorTitle(id, request.title()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    conversationService.deleteTutorConversation(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/sources")
  public ResponseEntity<List<SourceAttachResponse>> attachSources(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long id,
      @RequestParam("files") List<MultipartFile> files,
      @RequestParam(value = "contentKind", required = false) ContentKind contentKind,
      @RequestParam(value = "schoolName", required = false) String schoolName,
      @RequestParam(value = "grade", required = false) Byte grade,
      @RequestParam(value = "examType", required = false) ExamType examType,
      @RequestParam(value = "examYear", required = false) Short examYear,
      @RequestParam(value = "examSemester", required = false) Byte examSemester) {
    SourceUploadRequest request = new SourceUploadRequest(
        contentKind != null ? contentKind : ContentKind.GENERAL,
        schoolName, grade, examType, examYear, examSemester);
    return ResponseEntity.ok(conversationService.attachTutorConversationSources(
        id, files, principal.userId(), request));
  }

  @PostMapping("/{id}/messages")
  public ResponseEntity<MessageResponse> sendMessage(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long id,
      @RequestBody MessageRequest request) {
    return ResponseEntity.ok(conversationService.sendTutorMessage(id, request.content(), principal.userId()));
  }
}
