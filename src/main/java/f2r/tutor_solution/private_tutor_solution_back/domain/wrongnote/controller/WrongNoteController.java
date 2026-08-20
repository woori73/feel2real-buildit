package f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.dto.WrongNoteEntryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.service.WrongNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations/{conversationId}/wrong-note-entries")
public class WrongNoteController {

  private final WrongNoteService wrongNoteService;

  @GetMapping
  public ResponseEntity<List<WrongNoteEntryResponse>> list(@PathVariable Long conversationId) {
    return ResponseEntity.ok(wrongNoteService.list(conversationId));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<WrongNoteEntryResponse>> upload(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long conversationId,
      @RequestPart("files") List<MultipartFile> files,
      @RequestParam String title,
      @RequestParam(required = false) String description) {
    return ResponseEntity.ok(wrongNoteService.upload(
        principal, conversationId, files, title, description));
  }

  @DeleteMapping("/{entryId}")
  public ResponseEntity<Void> delete(@PathVariable Long conversationId, @PathVariable Long entryId) {
    wrongNoteService.delete(conversationId, entryId);
    return ResponseEntity.noContent().build();
  }
}
