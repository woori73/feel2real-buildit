package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.SourceUploadRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 대화 API.
 *
 * <p>1차 인증 정책: 모든 메서드는 JWT principal의 userId/studentId를 그대로 사용한다.
 * ADMIN role만 query의 {@code targetStudentId}를 받아 임퍼소네이트를 허용 — 학생은 query를 보내도
 * 자기 ID로 강제 고정된다 (AuthenticatedUserResolver가 IDOR 차단).
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationService conversationService;
  private final AuthenticatedUserResolver authResolver;

  @PostMapping
  public ResponseEntity<ConversationSummaryResponse> create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(value = "targetStudentId", required = false) Long targetStudentId) {
    if ("TUTOR".equals(principal.role())) {
      throw new AccessDeniedException("강사는 학생 소유 대화를 생성할 수 없습니다.");
    }
    Long studentId = authResolver.resolveStudentId(principal, targetStudentId);
    return ResponseEntity.ok(conversationService.create(studentId));
  }

  @GetMapping
  public ResponseEntity<List<ConversationSummaryResponse>> list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(value = "targetStudentId", required = false) Long targetStudentId) {
    Long studentId = authResolver.resolveStudentId(principal, targetStudentId);
    return ResponseEntity.ok(conversationService.list(studentId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ConversationDetailResponse> detail(@PathVariable Long id) {
    // ownership 가드는 service(ConversationService.getDetail → verifyOwnsConversation)에 있다.
    return ResponseEntity.ok(conversationService.getDetail(id));
  }

  /**
   * 대화명(title) 수정. 학생이 종합 현황판 제목 inline 편집에서 호출한다.
   * 권한은 service에서 {@code verifyOwnsConversation}으로 검증 — 본인 대화 아니면 403.
   */
  @PatchMapping("/{id}")
  public ResponseEntity<ConversationTitleResponse> updateTitle(
      @PathVariable Long id,
      @RequestBody UpdateConversationTitleRequest request) {
    return ResponseEntity.ok(conversationService.updateTitle(id, request.title()));
  }

  /**
   * 대화 삭제. 권한은 service에서 {@code verifyOwnsConversation}으로 검증한다.
   * DB FK 정책에 따라 메시지/대화-파일 매핑/오답노트/학습자료 연결은 함께 정리된다.
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    conversationService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * 한 번에 한 시험(또는 자료) 단위로 여러 파일을 첨부한다.
   *
   * <p>multipart/form-data로 다음 필드를 받는다:
   * <ul>
   *   <li>files: 업로드 파일 1~N개 (최대 20개). 같은 묶음이면 모두 같은 contentKind/exam으로 처리된다.</li>
   *   <li>contentKind: "GENERAL" or "EXAM_BLANK" (학생 폼의 "일반 자료" / "시험지"에 1:1 매핑). 없으면 GENERAL로 간주.</li>
   *   <li>schoolName, grade, examType, examYear, examSemester: 시험지일 때 학생 폼 입력값</li>
   *   <li>targetUserId: ADMIN 임퍼소네이트 시에만 — 학생은 무시</li>
   * </ul>
   */
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
      @RequestParam(value = "examSemester", required = false) Byte examSemester,
      @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
    Long userId = authResolver.resolveUserId(principal, targetUserId);
    SourceUploadRequest request = new SourceUploadRequest(
        contentKind != null ? contentKind : ContentKind.GENERAL,
        schoolName, grade, examType, examYear, examSemester);
    return ResponseEntity.ok(conversationService.attachSources(id, files, userId, request));
  }

  /**
   * 강사/Admin이 학생 대화에 '시험대비 참고자료'(제출 불필요)를 업로드한다.
   *
   * <p>multipart/form-data: {@code files} 1~20개. SourceType=TUTOR_MATERIAL, ContentKind=GENERAL 고정.
   * 업로더(uploaded_by)는 호출한 강사/Admin 본인이다 — 임퍼소네이트 없음(대화 매핑은 path id + 소유권 가드).
   * 학생이 호출하면 403 (service의 verifyAdminOrTutor).
   */
  @PostMapping("/{id}/tutor-materials")
  public ResponseEntity<List<SourceAttachResponse>> attachTutorMaterials(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long id,
      @RequestParam("files") List<MultipartFile> files) {
    return ResponseEntity.ok(conversationService.attachTutorMaterials(id, files, principal.userId()));
  }

  @PostMapping("/{id}/messages")
  public ResponseEntity<MessageResponse> sendMessage(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long id,
      @RequestBody MessageRequest request,
      @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
    Long userId = authResolver.resolveUserId(principal, targetUserId);
    return ResponseEntity.ok(conversationService.sendMessage(id, request.content(), userId));
  }
}
