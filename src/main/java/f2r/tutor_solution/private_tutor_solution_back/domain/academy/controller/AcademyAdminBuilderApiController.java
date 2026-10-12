package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAdminAudit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAdminManagementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAdminManagementService.AdminView;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAdminManagementService.PublishResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.State;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.WorkingCopyConflictException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자(ADMIN)의 타인 홈페이지 관리 API. 경로에 <b>대상 academyId를 명시</b>한다. {@code /api/admin/**}는 필터 체인에서
 * ADMIN만 통과하고, 서비스 계층이 한 번 더 확인한다. 수정은 관리자 전용 비공개 작업본에만 쓰며 공개 홈페이지는
 * {@code /publish}에서만 바뀐다. 회원용 {@code /api/tutor/**}와 분리되어 있어 TUTOR/STUDENT 권한은 그대로다.
 */
@RestController
@RequestMapping("/api/admin/academies/{academyId}/builder")
@RequiredArgsConstructor
public class AcademyAdminBuilderApiController {

  private final AcademyAdminManagementService service;

  /** 관리 화면 진입: 학원 요약 + 관리자 작업본 상태. 접근이 감사 기록에 남는다. */
  @GetMapping
  public ResponseEntity<Map<String, Object>> open(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId) {
    return respond(() -> {
      AdminView v = service.open(admin, academyId);
      Map<String, Object> body = stateBody(v.state());
      body.put("academy", v.academy());
      body.put("live", v.live());
      body.put("memberDraftDirty", v.memberDraftDirty());
      return body;
    });
  }

  /** 작업본 수정. body: {revision, intro?, pageSettings?}. */
  @PutMapping
  public ResponseEntity<Map<String, Object>> save(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId,
      @RequestBody SaveRequest request) {
    if (request == null || request.revision() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision이 필요합니다.", null);
    }
    return respond(() -> stateBody(
        service.save(admin, academyId, request.revision(), request.intro(), request.pageSettings())));
  }

  /**
   * 공개 변경 반영(rebase): 공개 홈페이지가 작업본을 만든 뒤 바뀌었을 때 관리자가 바꾼 필드만 새 공개 내용 위에 얹는다.
   * 같은 필드를 양쪽이 바꾼 충돌은 공개 값을 유지하고 {@code conflicts}로 돌려준다. body: {revision}.
   */
  @PostMapping("/rebase")
  public ResponseEntity<Map<String, Object>> rebase(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId,
      @RequestBody RevisionRequest request) {
    if (request == null || request.revision() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision이 필요합니다.", null);
    }
    return respond(() -> {
      var r = service.rebase(admin, academyId, request.revision());
      Map<String, Object> body = stateBody(r.state());
      body.put("conflicts", r.conflicts());
      body.put("keptMine", r.keptMine());
      return body;
    });
  }

  /** 되돌리기: 작업본을 공개 내용으로. body: {revision}. */
  @PostMapping("/discard")
  public ResponseEntity<Map<String, Object>> discard(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId,
      @RequestBody RevisionRequest request) {
    if (request == null || request.revision() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision이 필요합니다.", null);
    }
    return respond(() -> stateBody(service.discard(admin, academyId, request.revision())));
  }

  /** 복원: 이 학원의 버전을 작업본에 불러온다. body: {revision, versionId}. */
  @PostMapping("/restore")
  public ResponseEntity<Map<String, Object>> restore(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId,
      @RequestBody RestoreRequest request) {
    if (request == null || request.revision() == null || request.versionId() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision과 versionId가 필요합니다.", null);
    }
    return respond(() -> stateBody(service.restore(admin, academyId, request.revision(), request.versionId())));
  }

  /**
   * 게시. body: {revision, force?, confirmAcademyId, confirmHandle, confirmFirstPublish?}.
   * 확인 값이 대상과 다르면 409 TARGET_MISMATCH, 미공개 학원의 최초 공개는 confirmFirstPublish 없으면 409.
   */
  @PostMapping("/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId,
      @RequestBody PublishRequest request) {
    if (request == null || request.revision() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision이 필요합니다.", null);
    }
    return respond(() -> {
      PublishResult r = service.publish(
          admin, academyId, request.revision(), Boolean.TRUE.equals(request.force()),
          request.confirmAcademyId(), request.confirmHandle(), Boolean.TRUE.equals(request.confirmFirstPublish()));
      Map<String, Object> body = stateBody(r.state());
      body.put("firstPublish", r.firstPublish());
      return body;
    });
  }

  /** 관리자 작업본 기준 비공개 미리보기(공개 API와 같은 모양). */
  @GetMapping("/preview")
  public ResponseEntity<?> preview(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId) {
    try {
      AcademyResponse body = service.preview(admin, academyId);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore())
          .header("X-Robots-Tag", "noindex, nofollow").body(body);
    } catch (RuntimeException e) {
      return mapError(e);
    }
  }

  /** 이 학원의 버전 기록(복원 후보). */
  @GetMapping("/versions")
  public ResponseEntity<?> versions(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId) {
    try {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.versions(admin, academyId));
    } catch (RuntimeException e) {
      return mapError(e);
    }
  }

  /** 이 학원의 관리자 작업 감사 기록(최근순). ADMIN 전용. */
  @GetMapping("/audit")
  public ResponseEntity<?> audit(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal admin, @PathVariable Long academyId,
      @RequestParam(defaultValue = "50") int limit) {
    try {
      List<AcademyAdminAudit> rows = service.auditLog(admin, academyId, limit);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rows);
    } catch (RuntimeException e) {
      return mapError(e);
    }
  }

  // ───────────────────────── 내부 ─────────────────────────

  private ResponseEntity<Map<String, Object>> respond(Supplier<Map<String, Object>> action) {
    try {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(action.get());
    } catch (RuntimeException e) {
      @SuppressWarnings("unchecked")
      ResponseEntity<Map<String, Object>> r = (ResponseEntity<Map<String, Object>>) mapError(e);
      return r;
    }
  }

  private ResponseEntity<?> mapError(RuntimeException e) {
    if (e instanceof WorkingCopyConflictException c) {
      return error(HttpStatus.CONFLICT, c.getMessage(), c.getCode());
    }
    if (e instanceof ResponseStatusException r) {
      return error(HttpStatus.valueOf(r.getStatusCode().value()), r.getReason(), null);
    }
    if (e instanceof AccessDeniedException) {
      return error(HttpStatus.FORBIDDEN, e.getMessage(), null);
    }
    if (e instanceof IllegalArgumentException) {
      return error(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }
    throw e;
  }

  private static Map<String, Object> stateBody(State s) {
    Map<String, Object> body = new HashMap<>();
    body.put("revision", s.revision());
    body.put("intro", s.intro());
    body.put("pageSettings", s.pageSettings());
    body.put("dirty", s.dirty());
    body.put("liveChanged", s.liveChanged());
    return body;
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String code) {
    Map<String, Object> body = new HashMap<>();
    body.put("status", status.value());
    body.put("message", message);
    if (code != null) {
      body.put("code", code);
    }
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }

  public record SaveRequest(Long revision, String intro, AcademyPageSettingsUpdateRequest pageSettings) {}

  public record RevisionRequest(Long revision) {}

  public record RestoreRequest(Long revision, Long versionId) {}

  public record PublishRequest(
      Long revision, Boolean force, Long confirmAcademyId, String confirmHandle, Boolean confirmFirstPublish) {}
}
