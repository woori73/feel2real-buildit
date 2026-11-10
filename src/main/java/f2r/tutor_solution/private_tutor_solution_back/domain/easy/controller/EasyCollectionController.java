package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionConnectionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionCheckpointResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionIngestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionManagementResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionSourceResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionManagementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionSchedulerService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 자동수집 관리자 API — {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 * 모든 조회·수정은 {@link EasyTutorAccessService#requireAllowedBusiness}로 사업체 접근을 먼저 검증한다.
 * 수집한 실거래는 수집 시점에 중앙 장부에 저장된다(운영 원칙). 연결 해제는 조회 중단 + 인증 참조
 * 비활성화이며 장부·증빙을 삭제하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/easy/collections")
@RequiredArgsConstructor
public class EasyCollectionController {

  private final EasyCollectionIngestService ingestService;
  private final EasyCollectionSchedulerService schedulerService;
  private final EasyCollectionManagementService managementService;
  private final EasyTutorAccessService accessService;
  private final f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionAccountRecommendationService
      recommendationService;
  private final f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasySiteCollectionService
      siteCollectionService;

  private void requireBusiness(JwtTokenProvider.AuthenticatedPrincipal principal,
      Long businessId) {
    accessService.requireAllowedBusiness(principal, businessId);
  }

  @PostMapping("/ingest/csv")
  public ResponseEntity<EasyCollectionIngestResponse> ingestCsv(
      @RequestParam("businessId") Long businessId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "label", required = false) String label,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    if (file.isEmpty()) {
      throw new IllegalArgumentException("파일이 비어 있습니다.");
    }
    try {
      return ResponseEntity.ok(ingestService.ingestCsv(
          businessId, label, file.getOriginalFilename(), file.getBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException("파일 읽기에 실패했습니다.", e);
    }
  }

  @GetMapping("/runs")
  public ResponseEntity<List<EasyCollectionRunResponse>> listRuns(
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(ingestService.listRuns(businessId));
  }

  @GetMapping("/runs/{runId}")
  public ResponseEntity<EasyCollectionRunResponse> getRun(@PathVariable Long runId,
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    EasyCollectionRunResponse run = ingestService.getRun(runId);
    if (!businessId.equals(run.businessId())) {
      throw new org.springframework.security.access.AccessDeniedException("다른 사업체의 수집 실행입니다.");
    }
    return ResponseEntity.ok(run);
  }

  /** 수집 후 미분류로 저장된 거래의 계정 추천(GPT 루나). 저장·확정하지 않는다. */
  @GetMapping("/runs/{runId}/recommendations")
  public ResponseEntity<f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCollectionAccountRecommendationService.Result>
      recommendations(@PathVariable Long runId,
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(recommendationService.recommendForRun(businessId, runId));
  }

  /** 직접 로그인해 거래 파일을 내려받을 기관 공식 주소·순서·안전 규칙(MCP easy_collection_sites와 같은 내용). */
  @GetMapping("/sites")
  public ResponseEntity<java.util.Map<String, Object>> listSites(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(siteCollectionService.catalog(principal));
  }

  @GetMapping("/sources")
  public ResponseEntity<List<EasyCollectionSourceResponse>> listSources() {
    return ResponseEntity.ok(ingestService.listSources());
  }

  @GetMapping("/connections")
  public ResponseEntity<List<EasyCollectionManagementResponse>> listConnections(
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.listConnections(businessId));
  }

  /** 은행 계좌 연결 등록(자동수집은 꺼진 상태로 생성). */
  @PostMapping("/connections/bank")
  public ResponseEntity<EasyCollectionManagementResponse> createBankConnection(
      @RequestParam("businessId") Long businessId,
      @RequestBody BankConnectionRequest request,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.saveBankConnection(businessId, null,
        request.label(), request.organization(), request.clientType(), request.account(),
        request.connectedId()));
  }

  /** 은행 연결 설정 수정(기관코드·고객구분·계좌번호). */
  @PutMapping("/connections/{connectionId}/bank")
  public ResponseEntity<EasyCollectionManagementResponse> updateBankConnection(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @RequestBody BankConnectionRequest request,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.saveBankConnection(businessId, connectionId,
        request.label(), request.organization(), request.clientType(), request.account(),
        request.connectedId()));
  }

  /** 인증 등록·재인증 — 새 connectedId로 인증 참조만 교체한다. */
  @PutMapping("/connections/{connectionId}/auth")
  public ResponseEntity<EasyCollectionManagementResponse> updateAuthReference(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @RequestBody ConnectionRefRequest request,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.updateAuthReference(businessId, connectionId,
        request.connectionRef()));
  }

  /** 연결 해제 — 조회 중단 + 인증 참조 비활성화. 장부·증빙·원본·이력은 남는다. */
  @PostMapping("/connections/{connectionId}/disconnect")
  public ResponseEntity<EasyCollectionManagementResponse> disconnect(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.disconnect(businessId, connectionId));
  }

  /** 연결별 수집 이력 + 장부 저장 결과. */
  @GetMapping("/connections/{connectionId}/runs")
  public ResponseEntity<List<EasyCollectionRunResponse>> listConnectionRuns(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.listRuns(businessId, connectionId));
  }

  @PutMapping("/connections/{connectionId}/enabled")
  public ResponseEntity<EasyCollectionManagementResponse> setConnectionEnabled(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @RequestBody ConnectionEnabledRequest request,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(request.enabled()
        ? managementService.enable(businessId, connectionId)
        : managementService.disable(businessId, connectionId));
  }

  @GetMapping("/connections/{connectionId}/checkpoints")
  public ResponseEntity<List<EasyCollectionCheckpointResponse>> listCheckpoints(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.listCheckpoints(businessId, connectionId));
  }

  /** 지금 수집 — 스케줄과 같은 파이프라인(수집 → 원본 보존 → 중복 방지 → 장부 저장 → 분류). */
  @PostMapping("/connections/{connectionId}/run")
  public ResponseEntity<EasyCollectionRunResponse> triggerManualCollection(
      @PathVariable Long connectionId,
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    return ResponseEntity.ok(managementService.triggerManual(businessId, connectionId));
  }

  /**
   * 연결 생성 또는 갱신 (connection_ref 포함) — 관리자 콘솔에서 초기 등록용.
   */
  @PostMapping("/connections")
  public ResponseEntity<EasyCollectionConnectionResponse> createConnection(
      @RequestParam("businessId") Long businessId,
      @RequestParam("sourceCode") String sourceCode,
      @RequestParam(value = "label", required = false) String label,
      @RequestParam(value = "connectionRef", required = false) String connectionRef,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    EasyCollectionConnection conn = ingestService.createOrUpdateConnection(businessId, sourceCode, label, connectionRef);
    return ResponseEntity.ok(EasyCollectionConnectionResponse.from(conn));
  }

  /**
   * connection_ref 업데이트 — 관리자 콘솔에서 가맹점 동의ID 등 참조자 등록/수정.
   * NEEDS_REAUTH 상태면 ACTIVE로 복구.
   */
  @PutMapping("/connections/{connectionId}/ref")
  public ResponseEntity<EasyCollectionConnectionResponse> updateConnectionRef(
      @PathVariable Long connectionId,
      @RequestBody ConnectionRefRequest request,
      @RequestParam("businessId") Long businessId,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireBusiness(principal, businessId);
    ingestService.updateConnectionRef(businessId, connectionId, request.connectionRef());
    EasyCollectionConnection conn = ingestService.getConnection(connectionId);
    return ResponseEntity.ok(EasyCollectionConnectionResponse.from(conn));
  }

  public record ConnectionRefRequest(String connectionRef) {}

  public record ConnectionEnabledRequest(boolean enabled) {}

  /** 비민감 조회 조건 + (선택) connectedId. 계좌 비밀번호·인증서·OTP 필드는 일부러 없다. */
  public record BankConnectionRequest(String label, String organization, String clientType,
      String account, String connectedId) {}
}
