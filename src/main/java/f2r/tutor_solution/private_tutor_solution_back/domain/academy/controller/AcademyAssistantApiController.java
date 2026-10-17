package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantEditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantEditService.EditOutcome;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.PublishResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.StateView;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantService.TurnResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantFeatureFlag;
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
  private final AssistantFeatureFlag featureFlag;

  /** 시작 화면의 샘플 6종. */
  @GetMapping("/samples")
  public ResponseEntity<Object> samples() {
    return ResponseEntity.ok(service.samples());
  }

  /**
   * 화면 모드 조회: {mode: NONE|ASSISTANT|CLASSIC, canAdopt}. 기존 빌더가 'AI로 이어서 만들기' 버튼을 보일지 정한다.
   * 스위치가 꺼져 있거나 허용 목록에 없는 계정은 403(버튼을 보이지 않는다).
   */
  @GetMapping("/mode")
  public ResponseEntity<Map<String, Object>> mode(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    String mode = service.modeOf(principal);
    return noStore(HttpStatus.OK, Map.of("mode", mode, "canAdopt", "CLASSIC".equals(mode)));
  }

  /**
   * 'AI로 이어서 만들기': 기존 방식 학원을 AI 제작 방식으로 명시 전환한다. body: {confirm: true}가 있어야 한다.
   * 전환 전 공개본을 버전 기록에 보관하고 공개 홈페이지는 바꾸지 않는다.
   */
  @PostMapping("/adopt")
  public ResponseEntity<Map<String, Object>> adopt(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody(required = false) AdoptRequest request) {
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (request == null || !Boolean.TRUE.equals(request.confirm())) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of(
          "code", "CONFIRM_REQUIRED", "message", "AI로 이어서 만들기는 확인이 필요해요."));
    }
    try {
      AcademyAssistantService.AdoptResult r = service.adopt(principal);
      Map<String, Object> body = new HashMap<>();
      body.put("adopted", r.adopted());
      body.put("versionId", r.versionId());
      body.put("revision", r.revision());
      return noStore(HttpStatus.OK, body);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  /** 화면 복원용: 학원 유무·상태·대화 기록. */
  @GetMapping("/state")
  public ResponseEntity<Map<String, Object>> state(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
    StateView v = service.state(principal);
    Map<String, Object> body = new HashMap<>();
    body.put("hasAcademy", v.hasAcademy());
    body.put("status", v.status());
    body.put("messages", v.messages());
    body.put("revision", v.revision());
    body.put("dirty", v.dirty());
    body.put("publicUrl", v.publicUrl());
    body.put("undoDepth", v.undoDepth());
    putStack(body, principal);
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
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
    try {
      TurnResult r = service.turn(
          principal, request.message(), request.academyName(), request.sampleKey(), request.region(),
          request.subject(), request.target(), request.revision(), Boolean.TRUE.equals(request.instant()));
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
      putStack(body, principal);
      return noStore(HttpStatus.OK, body);
    } catch (AcademyAssistantEditService.AiTimeoutException e) {
      // 서버 AI 한도 초과: 수정이 아니다. 입력·작업본·공개본·대화 기록은 그대로(쓰기 전에 끊었다).
      return noStore(HttpStatus.GATEWAY_TIMEOUT, Map.of(
          "code", "AI_TIMEOUT", "message", e.getMessage(), "limitSeconds", e.getLimitSeconds()));
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
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
    try {
      HeroMediaSlot parsed = HeroMediaSlot.from(slot);
      String key = mediaService.storeHeroImage(principal, parsed, image);
      EditOutcome out = editService.stageUploadedMedia(principal, parsed.key(), key, revision);
      Map<String, Object> body = new HashMap<>();
      body.put("reply", out.reply());
      body.put("created", false);
      putEdit(body, out);
      putStack(body, principal);
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
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
    try {
      EditOutcome out = editService.undo(principal, request == null ? null : request.revision());
      Map<String, Object> body = new HashMap<>();
      body.put("reply", out.reply());
      body.put("created", false);
      putEdit(body, out);
      putStack(body, principal);
      return noStore(HttpStatus.OK, body);
    } catch (WorkingCopyConflictException e) {
      return conflict(e);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  /** 다시 실행: 방금 되돌린 수정을 다시 적용한다(되돌린 뒤 새 수정을 하면 불가). body: {revision}. */
  @PostMapping("/redo")
  public ResponseEntity<Map<String, Object>> redo(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody UndoRequest request) {
    return editAction(principal, p -> editService.redo(p, request == null ? null : request.revision()));
  }

  /** 저장: 현재 작업본을 저장점으로 기록한다(초기화 기준). 공개본·게시는 바꾸지 않는다. body: {revision}. */
  @PostMapping("/save")
  public ResponseEntity<Map<String, Object>> save(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody UndoRequest request) {
    return editAction(principal, p -> editService.savepoint(p, request == null ? null : request.revision()));
  }

  /**
   * 새 홈페이지 만들기: 학원 정보는 두고 요청에서 새 초안을 만들어 작업본에 적용한다(되돌리기 가능, 공개본 불변).
   * body: {message, revision}. 수정 턴(/turn)과 구분된다.
   */
  @PostMapping("/restart")
  public ResponseEntity<Map<String, Object>> restart(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody RestartRequest request) {
    return editAction(principal, p -> service.restart(p, request == null ? null : request.message(), request == null ? null : request.revision()));
  }

  /**
   * 초기화: 작업본을 마지막 저장점(없으면 현재 공개본)으로 되돌린다. 작업본만 바뀌고 공개본·버전 기록은 그대로이며,
   * 수정 한 건으로 기록돼 '되돌리기'로 취소할 수 있다. body: {revision}.
   */
  @PostMapping("/reset")
  public ResponseEntity<Map<String, Object>> reset(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody UndoRequest request) {
    return editAction(principal, p -> editService.reset(p, request == null ? null : request.revision()));
  }

  private ResponseEntity<Map<String, Object>> editAction(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      java.util.function.Function<JwtTokenProvider.AuthenticatedPrincipal, EditOutcome> action) {
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
    try {
      EditOutcome out = action.apply(principal);
      Map<String, Object> body = new HashMap<>();
      body.put("reply", out.reply());
      body.put("created", false);
      putEdit(body, out);
      putStack(body, principal);
      return noStore(HttpStatus.OK, body);
    } catch (WorkingCopyConflictException e) {
      return conflict(e);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  /** 버튼 상태용: 다시 실행 가능 단계와 마지막 저장점(revision·시각). */
  private void putStack(Map<String, Object> body, JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyAssistantEditService.StackInfo info = editService.stackInfo(principal);
    body.put("undoDepth", info.undoDepth());
    body.put("redoDepth", info.redoDepth());
    body.put("savedRevision", info.savedRevision());
    body.put("savedAt", info.savedAt());
  }

  /**
   * 기능 스위치가 꺼져 있거나 허용 목록에 없는 계정이면 서버에서 막는다(화면 숨김만으로는 API가 열려 있었다).
   * AI 호출·작업본 쓰기·게시가 모두 이 가드 뒤에 있다.
   */
  private static ResponseEntity<Map<String, Object>> featureOff() {
    return noStore(HttpStatus.FORBIDDEN, Map.of("code", "ASSISTANT_DISABLED", "message", "대화형 홈페이지 제작은 아직 사용할 수 없어요."));
  }

  private boolean isClassic(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return "CLASSIC".equals(service.modeOf(principal));
  }

  /** 기존 방식 학원은 대화 API 대상이 아니다. 화면·공개 데이터·작업본은 그대로이며, 전환은 /adopt로만 한다. */
  private static ResponseEntity<Map<String, Object>> notAssistantAcademy() {
    return noStore(HttpStatus.CONFLICT, Map.of(
        "code", "NOT_ASSISTANT_ACADEMY",
        "message", "이 홈페이지는 기존 방식으로 만들어졌어요. 'AI로 이어서 만들기'를 확인하면 대화로 수정할 수 있어요."));
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
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
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

  /** 버전 기록 목록(복원 대상). */
  @GetMapping("/versions")
  public ResponseEntity<Object> versions(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!featureFlag.isOn(principal)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ASSISTANT_DISABLED", "message", "대화형 홈페이지 제작은 아직 사용할 수 없어요."));
    }
    if (isClassic(principal)) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "NOT_ASSISTANT_ACADEMY", "message", "기존 방식 홈페이지예요."));
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.versions(principal));
  }

  /** 버전 복원: 선택한 버전을 비공개 작업본에 불러온다(공개본 불변). body: {revision, versionId}. */
  @PostMapping("/restore")
  public ResponseEntity<Map<String, Object>> restore(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody RestoreRequest request) {
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
    if (request == null || request.revision() == null || request.versionId() == null) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", "revision과 versionId가 필요합니다."));
    }
    try {
      var state = service.restoreVersion(principal, request.revision(), request.versionId());
      return noStore(HttpStatus.OK, Map.of("revision", state.revision(), "undoDepth", 0));
    } catch (WorkingCopyConflictException e) {
      return conflict(e);
    } catch (IllegalArgumentException e) {
      return noStore(HttpStatus.BAD_REQUEST, Map.of("message", e.getMessage()));
    }
  }

  /** 초안 학원의 이름·지역·대상 수정(한 문장 생성의 임시 값을 실제 값으로). body: {academyName?, region?, targets?[]}. */
  @PostMapping("/profile")
  public ResponseEntity<Map<String, Object>> profile(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ProfileRequest request) {
    if (!featureFlag.isOn(principal)) {
      return featureOff();
    }
    if (isClassic(principal)) {
      return notAssistantAcademy();
    }
    try {
      java.util.Set<f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup> targets = null;
      if (request != null && request.targets() != null) {
        targets = request.targets().stream()
            .map(String::trim).filter(v -> !v.isEmpty())
            .map(v -> f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.valueOf(v))
            .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(
                f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.class)));
      }
      service.updateProfile(principal,
          request == null ? null : request.academyName(), request == null ? null : request.region(), targets);
      return noStore(HttpStatus.OK, Map.of("updated", true));
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
      Long revision, Boolean instant) {}

  record RestoreRequest(Long revision, Long versionId) {}

  record ProfileRequest(String academyName, String region, java.util.List<String> targets) {}

  record UndoRequest(Long revision) {}

  record RestartRequest(String message, Long revision) {}

  record AdoptRequest(Boolean confirm) {}

  record PublishRequest(Long revision, Boolean force) {}
}
