package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantEditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantEditService.EditOutcome;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.PublishResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.StateView;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.TurnResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.WorkingCopyConflictException;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.HeroMediaSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyMediaService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
  private final AcademyAssistantEditService editService;
  private final AcademyMediaService mediaService;

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
    body.put("undoDepth", v.undoDepth());
    return noStore(HttpStatus.OK, body);
  }

  /**
   * 대화 한 턴. 학원이 없으면 최초 생성, 있으면 수정 턴(비공개 작업본에만 적용).
   * body: {message?, academyName?, sampleKey?, region?, subject?, target?, revision?}. 수정 턴의 revision은 동시 수정 감지용이다.
   */
  @PostMapping("/turn")
  public ResponseEntity<Map<String, Object>> turn(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody TurnRequest request) {
    try {
      TurnResult r = service.turn(
          principal, request.message(), request.academyName(), request.sampleKey(), request.region(),
          request.subject(), request.target(), request.revision());
      Map<String, Object> body = new HashMap<>();
      body.put("reply", r.reply());
      body.put("needs", r.needs());
      body.put("created", r.created());
      body.put("academyId", r.academyId());
      body.put("revision", r.revision());
      body.put("designPreset", r.designPreset());
      body.put("notes", r.notes());
      if (r.edit() != null) {
        putEdit(body, r.edit());
      }
      return noStore(HttpStatus.OK, body);
    } catch (WorkingCopyConflictException e) {
      return conflict(e);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  /** 되돌리기: 마지막 수정(게시 이후, 아직 되돌리지 않은 것)을 작업본에서 되돌린다. body: {revision}. */
  /**
   * 사진 올리기(S3). 사용자가 직접 올린 사진을 작업본의 이미지 예약으로만 건다 — 공개 홈페이지는 '게시' 전까지 그대로다.
   * slot: principal | textbook1 | textbook2 | textbook3 | schedule. 되돌리기 대상이다.
   */
  @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> uploadMedia(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam("slot") String slot,
      @RequestParam("revision") Long revision,
      @RequestPart("image") MultipartFile image) {
    try {
      HeroMediaSlot parsed = HeroMediaSlot.from(slot);
      String key = mediaService.storeHeroImage(principal, parsed, image);
      EditOutcome out = editService.stageUploadedMedia(principal, parsed.key(), key, revision);
      Map<String, Object> body = new HashMap<>();
      body.put("reply", out.reply());
      body.put("created", false);
      putEdit(body, out);
      return noStore(HttpStatus.OK, body);
    } catch (WorkingCopyConflictException e) {
      return conflict(e);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  /** 작업본 미리보기용 이미지(예약된 사진이 있으면 그것, 없으면 공개 사진). 소유자만 읽는다. */
  @GetMapping("/media/{slot}")
  public ResponseEntity<byte[]> workingCopyMedia(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String slot) {
    AcademyMediaService.ServedImage image = mediaService.readWorkingCopyMedia(principal, HeroMediaSlot.from(slot));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .cacheControl(CacheControl.noStore())
        .body(image.bytes());
  }

  @PostMapping("/undo")
  public ResponseEntity<Map<String, Object>> undo(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody UndoRequest request) {
    try {
      EditOutcome out = editService.undo(principal, request == null ? null : request.revision());
      Map<String, Object> body = new HashMap<>();
      body.put("reply", out.reply());
      body.put("created", false);
      putEdit(body, out);
      return noStore(HttpStatus.OK, body);
    } catch (WorkingCopyConflictException e) {
      return conflict(e);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  private static void putEdit(Map<String, Object> body, EditOutcome out) {
    body.put("edited", !out.failed() && !out.changes().isEmpty());
    body.put("failed", out.failed());
    body.put("changes", out.changes());
    body.put("skipped", out.skipped());
    body.put("engine", out.engine());
    body.put("aiMs", out.aiMs());
    body.put("undoDepth", out.undoDepth());
    body.put("revision", out.revision());
    body.put("question", out.question());
  }

  private static ResponseEntity<Map<String, Object>> conflict(WorkingCopyConflictException e) {
    Map<String, Object> body = new HashMap<>();
    body.put("message", e.getMessage());
    body.put("code", e.getCode());
    if (e.getDetails() != null) {
      body.put("details", e.getDetails());
    }
    return noStore(HttpStatus.CONFLICT, body);
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
      Map<String, Object> body = new java.util.HashMap<>();
      body.put("message", e.getMessage());
      body.put("code", e.getCode());
      if (e.getDetails() != null) {
        body.put("details", e.getDetails());
      }
      return noStore(HttpStatus.CONFLICT, body);
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
      String message, String academyName, String sampleKey, String region, String subject, String target,
      Long revision) {}

  record UndoRequest(Long revision) {}

  record PublishRequest(Long revision, Boolean force) {}
}
