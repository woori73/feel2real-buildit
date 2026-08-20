package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageLayoutService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Academy Builder 레이아웃 생성/수정/삭제 API(Phase 4 백엔드).
 *
 * <p>모두 본인 소유 Academy에만 적용된다({@link AcademyService#requireMyAcademy}). 빌더 화면이
 * 전체 semantic schema를 보내면 allowlist 검증 후 upsert 하고, 삭제 시 기본 레이아웃으로 fallback 된다.
 * 화면단(빌더 UI)은 이 API를 호출하는 별도 작업으로 이어진다.
 */
@RestController
@RequiredArgsConstructor
public class AcademyLayoutApiController {

  private final AcademyService academyService;
  private final AcademyPageLayoutService academyPageLayoutService;

  /** 내 Academy의 레이아웃을 반환한다(빌더 초기 로딩). 저장본이 없으면 기본 레이아웃. */
  @GetMapping("/api/tutor/academy/layout")
  public ResponseEntity<AcademyPageLayoutSchema> getMyLayout(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademy(principal);
    return ResponseEntity.ok(academyPageLayoutService.resolveLayoutSchema(academy));
  }

  /** 빌더가 보낸 전체 schema로 레이아웃을 upsert 하고, 해석된 결과를 돌려준다. */
  @PutMapping("/api/tutor/academy/layout")
  public ResponseEntity<AcademyPageLayoutSchema> upsertMyLayout(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AcademyPageLayoutSchema schema) {
    Academy academy = academyService.requireMyAcademy(principal);
    academyPageLayoutService.saveLayout(academy, schema);
    return ResponseEntity.ok(academyPageLayoutService.resolveLayoutSchema(academy));
  }

  /** 내 Academy의 저장 레이아웃을 삭제한다(이후 기본 레이아웃 fallback). 멱등. */
  @DeleteMapping("/api/tutor/academy/layout")
  public ResponseEntity<Void> deleteMyLayout(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademy(principal);
    academyPageLayoutService.deleteLayout(academy);
    return ResponseEntity.noContent().build();
  }
}
