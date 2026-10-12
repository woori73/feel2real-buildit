package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.PublishResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.StateView;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.TurnResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.WorkingCopyConflictException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BuildIt 통합 화면(대화형)의 서버 API. 인증은 {@code /api/tutor/**}(TUTOR) 규칙을 따른다.
 * 모든 쓰기는 비공개 작업본에만 닿고, 공개 홈페이지는 {@code /publish}에서만 바뀐다.
 */
@RestController
@RequestMapping("/api/tutor/academy/assistant")
@RequiredArgsConstructor
public class AcademyAssistantApiController {

  private final AcademyAssistantService service;

  /** 시작 화면의 샘플 6종. */
  @GetMapping("/samples")
  public ResponseEntity<Object> samples() {
    return ResponseEntity.ok(service.samples());
  }

  /** 화면 복원용: 학원 유무·상태·대화 기록. */
  @GetMapping("/state")
  public ResponseEntity<Map<String, Object>> state(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    StateView v = service.state(principal);
    Map<String, Object> body = new HashMap<>();
    body.put("hasAcademy", v.hasAcademy());
    body.put("status", v.status());
    body.put("messages", v.messages());
    body.put("revision", v.revision());
    body.put("dirty", v.dirty());
    body.put("publicUrl", v.publicUrl());
    return noStore(HttpStatus.OK, body);
  }

  /** 대화 한 턴. body: {message?, academyName?, sampleKey?, region?, subject?, target?}. */
  @PostMapping("/turn")
  public ResponseEntity<Map<String, Object>> turn(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody TurnRequest request) {
    try {
      TurnResult r = service.turn(
          principal, request.message(), request.academyName(), request.sampleKey(), request.region(),
          request.subject(), request.target());
      Map<String, Object> body = new HashMap<>();
      body.put("reply", r.reply());
      body.put("needs", r.needs());
      body.put("created", r.created());
      body.put("academyId", r.academyId());
      body.put("revision", r.revision());
      body.put("designPreset", r.designPreset());
      body.put("notes", r.notes());
      return noStore(HttpStatus.OK, body);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  /** '게시하기': 작업본을 공개하고, 최초면 DRAFT를 PUBLISHED로 바꾼다. body: {revision, force?}. */
  @PostMapping("/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody PublishRequest request) {
    if (request == null || request.revision() == null) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", "revision이 필요합니다."));
    }
    try {
      PublishResult r = service.publish(principal, request.revision(), Boolean.TRUE.equals(request.force()));
      return noStore(HttpStatus.OK, Map.of("firstPublish", r.firstPublish(), "revision", r.revision()));
    } catch (WorkingCopyConflictException e) {
      return noStore(HttpStatus.CONFLICT, Map.of("message", e.getMessage(), "code", e.getCode()));
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  private static ResponseEntity<Map<String, Object>> noStore(HttpStatus status, Map<String, ?> body) {
    @SuppressWarnings("unchecked")
    Map<String, Object> typed = (Map<String, Object>) body;
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(typed);
  }

  record TurnRequest(
      String message, String academyName, String sampleKey, String region, String subject, String target) {}

  record PublishRequest(Long revision, Boolean force) {}
}
