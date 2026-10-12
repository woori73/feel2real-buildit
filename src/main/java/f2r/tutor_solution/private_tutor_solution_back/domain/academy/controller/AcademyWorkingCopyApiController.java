package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.State;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BuildIt 통합 화면 작업본 API. 인증·권한은 {@code /api/tutor/**}(TUTOR) 규칙과 서비스의 소유권 검증이 맡는다.
 *
 * <p>공개 홈페이지는 {@code POST /publish}에서만 바뀐다. 나머지는 모두 작업본만 다룬다.
 */
@RestController
@RequestMapping("/api/tutor/academy/working-copy")
@RequiredArgsConstructor
public class AcademyWorkingCopyApiController {

  private final AcademyWorkingCopyService service;

  /** 작업본 상태. 작업본이 없으면 현재 공개 내용으로 복제해 만든다(공개본 불변). */
  @GetMapping
  public ResponseEntity<Map<String, Object>> get(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return respond(() -> service.open(principal));
  }

  /** 작업본 수정. body: {revision, intro?, pageSettings?}. */
  @PutMapping
  public ResponseEntity<Map<String, Object>> save(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody SaveRequest request) {
    if (request == null || request.revision() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision이 필요합니다.", null);
    }
    return respond(() -> service.save(principal, request.revision(), request.intro(), request.pageSettings()));
  }

  /** 작업본을 현재 공개 내용으로 되돌린다(공개본 불변). body: {revision}. */
  @PostMapping("/discard")
  public ResponseEntity<Map<String, Object>> discard(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody RevisionRequest request) {
    if (request == null || request.revision() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision이 필요합니다.", null);
    }
    return respond(() -> service.discard(principal, request.revision()));
  }

  /** 기존 버전 기록을 작업본에 불러온다(공개본 불변). body: {revision, versionId}. */
  @PostMapping("/restore")
  public ResponseEntity<Map<String, Object>> restore(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody RestoreRequest request) {
    if (request == null || request.revision() == null || request.versionId() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision과 versionId가 필요합니다.", null);
    }
    return respond(() -> service.restoreFromVersion(principal, request.revision(), request.versionId()));
  }

  /** 작업본을 공개 홈페이지에 반영한다. body: {revision, force?}. */
  @PostMapping("/publish")
  public ResponseEntity<Map<String, Object>> publish(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody PublishRequest request) {
    if (request == null || request.revision() == null) {
      return error(HttpStatus.BAD_REQUEST, "revision이 필요합니다.", null);
    }
    return respond(() -> service.publish(principal, request.revision(), Boolean.TRUE.equals(request.force())));
  }

  /** 작업본 기준 비공개 미리보기 데이터(공개 API와 같은 모양). 캐시하지 않는다. */
  @GetMapping("/preview")
  public ResponseEntity<AcademyResponse> preview(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Robots-Tag", "noindex, nofollow")
        .body(service.preview(principal));
  }

  // ───────────────────────── 내부 ─────────────────────────

  private ResponseEntity<Map<String, Object>> respond(java.util.function.Supplier<State> action) {
    try {
      State s = action.get();
      Map<String, Object> body = new HashMap<>();
      body.put("revision", s.revision());
      body.put("intro", s.intro());
      body.put("pageSettings", s.pageSettings());
      body.put("dirty", s.dirty());
      body.put("liveChanged", s.liveChanged());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    } catch (WorkingCopyConflictException e) {
      return error(HttpStatus.CONFLICT, e.getMessage(), e.getCode());
    } catch (IllegalArgumentException e) {
      return error(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String code) {
    Map<String, Object> body = new HashMap<>();
    body.put("status", status.value());
    body.put("message", message);
    if (code != null) {
      body.put("code", code);
    }
    return ResponseEntity.status(status).body(body);
  }

  public record SaveRequest(Long revision, String intro, AcademyPageSettingsUpdateRequest pageSettings) {}

  public record RevisionRequest(Long revision) {}

  public record RestoreRequest(Long revision, Long versionId) {}

  public record PublishRequest(Long revision, Boolean force) {}
}
