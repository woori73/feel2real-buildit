package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EasyCollectionManagementService {

  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionCheckpointRepository checkpointRepository;
  private final EasyCollectionProviderReadinessPolicy readinessPolicy;
  private final EasyCollectionSchedulerService schedulerService;

  /** 화면 표시용: 직접 로그인 수집 연결의 기관 코드를 원본에서 읽는다. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRawRecordRepository
      rawRepository;

  /** 은행 연결 등록용 수집원 카탈로그. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private EasyCollectionSourceRepository sourceRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private EasyBusinessRepository businessRepository;

  @Transactional(readOnly = true)
  public List<EasyCollectionManagementResponse> listConnections(Long businessId) {
    requireBusinessId(businessId);
    return connectionRepository.findAllByBusiness_BusinessIdOrderByConnectionIdAsc(businessId)
        .stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<EasyCollectionCheckpointResponse> listCheckpoints(
      Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    return checkpointRepository.findAllByConnection_ConnectionIdOrderByDatasetTypeAsc(
        connection.getConnectionId()).stream()
        .map(EasyCollectionCheckpointResponse::from)
        .toList();
  }

  @Transactional
  public EasyCollectionManagementResponse disable(Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    connection.disable();
    return toResponse(connection);
  }

  @Transactional
  public EasyCollectionManagementResponse enable(Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    EasyCollectionProviderReadiness readiness = readinessForActivation(connection);
    if (readiness != EasyCollectionProviderReadiness.READY) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Collection provider is not ready for activation: " + readiness.name());
    }
    connection.enable();
    return toResponse(connection);
  }

  /**
   * All safety checks complete before delegating. The delegate preserves Phase 3A's external-fetch
   * transaction boundary and records MANUAL provenance.
   */
  public EasyCollectionRunResponse triggerManual(Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    EasyCollectionProviderReadiness readiness = readiness(connection);
    if (readiness != EasyCollectionProviderReadiness.READY) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "Collection provider is not ready: " + readiness.name());
    }
    if (runRepository.existsByConnection_ConnectionIdAndStatus(
        connectionId, EasyCollectionRunStatus.RUNNING)) {
      throw new IllegalStateException("A collection run is already in progress.");
    }
    return schedulerService.triggerManualCollection(businessId, connectionId);
  }

  /**
   * 은행 계좌 연결 등록 또는 설정 수정. 값은 기관코드·고객구분·계좌번호(비민감 조회 조건)와, 사용자가
   * CODEF 계정 등록으로 받은 connectedId(인증 참조)뿐이다. 계좌 비밀번호·인증서·OTP는 받지 않는다.
   * 새 연결은 자동수집이 꺼진 상태로 만든다 — 기관 검증이 끝나 READY가 된 뒤에만 켤 수 있다.
   */
  @Transactional
  public EasyCollectionManagementResponse saveBankConnection(Long businessId, Long connectionId,
      String label, String organization, String clientType, String account,
      String connectedId) {
    requireBusinessId(businessId);
    CodefBankTransactionAdapter.BankConfig config = CodefBankTransactionAdapter.parseConfig(
        "{\"organization\":\"" + safe(organization) + "\",\"clientType\":\""
            + safe(clientType) + "\",\"account\":\"" + safe(account) + "\"}");
    EasyCollectionConnection connection;
    if (connectionId != null) {
      connection = requireOwnedConnection(businessId, connectionId);
      if (!CodefBankTransactionAdapter.ADAPTER_CODE.equals(connection.getSource().getCode())) {
        throw new IllegalArgumentException("은행 연결만 이 화면에서 수정할 수 있습니다.");
      }
    } else {
      EasyCollectionSource source = sourceRepository
          .findByCode(CodefBankTransactionAdapter.ADAPTER_CODE)
          .orElseThrow(() -> new IllegalArgumentException("은행 수집원이 없습니다."));
      EasyBusiness business = businessRepository.findById(businessId)
          .filter(EasyBusiness::isActive)
          .orElseThrow(() -> new IllegalArgumentException("사업체를 찾을 수 없습니다."));
      String normalizedLabel = label == null || label.isBlank()
          ? "계좌 " + config.maskedAccount() : label.trim();
      if (connectionRepository.findByBusiness_BusinessIdAndSource_SourceIdAndLabel(
          businessId, source.getSourceId(), normalizedLabel).isPresent()) {
        throw new IllegalArgumentException("같은 이름의 연결이 이미 있습니다.");
      }
      connection = EasyCollectionConnection.builder()
          .business(business).source(source).label(normalizedLabel).build();
      connection.disable();
    }
    connection.updateProviderConfig(config.toJson());
    if (connectedId != null && !connectedId.isBlank()) {
      connection.updateConnectionRef(connectedId.trim());
    }
    return toResponse(connectionRepository.save(connection));
  }

  /** 재인증 — 새 connectedId로 인증 참조만 바꾼다. NEEDS_REAUTH면 ACTIVE로 돌아간다. */
  @Transactional
  public EasyCollectionManagementResponse updateAuthReference(Long businessId, Long connectionId,
      String connectedId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    if (connectedId == null || connectedId.isBlank()) {
      throw new IllegalArgumentException("인증 참조(connectedId)를 입력하세요.");
    }
    connection.updateConnectionRef(connectedId.trim());
    return toResponse(connection);
  }

  /**
   * 연결 해제 — 조회 중단 + 인증 참조 비활성화. 장부·증빙·원본·수집 이력은 삭제하지 않는다.
   */
  @Transactional
  public EasyCollectionManagementResponse disconnect(Long businessId, Long connectionId) {
    EasyCollectionConnection connection = requireOwnedConnection(businessId, connectionId);
    if (runRepository.existsByConnection_ConnectionIdAndStatus(
        connectionId, EasyCollectionRunStatus.RUNNING)) {
      throw new IllegalStateException("수집이 진행 중입니다. 끝난 뒤 해제하세요.");
    }
    connection.disconnect(java.time.LocalDateTime.now());
    return toResponse(connection);
  }

  /** 연결별 수집 이력 + 장부 저장 결과. */
  @Transactional(readOnly = true)
  public List<EasyCollectionRunResponse> listRuns(Long businessId, Long connectionId) {
    requireOwnedConnection(businessId, connectionId);
    return runRepository.findAllByBusiness_BusinessIdOrderByRunIdDesc(businessId).stream()
        .filter(run -> connectionId.equals(run.getConnection().getConnectionId()))
        .limit(50)
        .map(EasyCollectionRunResponse::from)
        .toList();
  }

  private static String safe(String value) {
    return value == null ? "" : value.replace("\\", "").replace("\"", "").trim();
  }

  public EasyCollectionProviderReadiness readiness(EasyCollectionConnection connection) {
    if (connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      return EasyCollectionProviderReadiness.NEEDS_REAUTH;
    }
    if (connection.getStatus() != EasyCollectionConnectionStatus.ACTIVE
        || !connection.getSource().isActive()) {
      return EasyCollectionProviderReadiness.DISABLED;
    }
    return configuredAndVerified(connection);
  }

  private EasyCollectionProviderReadiness readinessForActivation(
      EasyCollectionConnection connection) {
    if (connection.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      return EasyCollectionProviderReadiness.NEEDS_REAUTH;
    }
    if (connection.getStatus() == EasyCollectionConnectionStatus.DISCONNECTED
        || !connection.getSource().isActive()) {
      return EasyCollectionProviderReadiness.DISABLED;
    }
    return configuredAndVerified(connection);
  }

  private EasyCollectionProviderReadiness configuredAndVerified(
      EasyCollectionConnection connection) {
    if (connection.getConnectionRef() == null || connection.getConnectionRef().isBlank()) {
      return EasyCollectionProviderReadiness.NOT_CONFIGURED;
    }
    if (!readinessPolicy.isVerified(connection.getSource())) {
      return EasyCollectionProviderReadiness.UNVERIFIED;
    }
    return EasyCollectionProviderReadiness.READY;
  }

  private EasyCollectionManagementResponse toResponse(EasyCollectionConnection connection) {
    EasyCollectionRun latest = runRepository
        .findFirstByConnection_ConnectionIdOrderByRunIdDesc(connection.getConnectionId())
        .orElse(null);
    boolean hasCheckpoint = checkpointRepository
        .existsByConnection_ConnectionId(connection.getConnectionId());
    String siteRaw = null;
    if (rawRepository != null && latest != null
        && EasyCollectionIngestService.SITE_ADAPTER_CODE.equals(latest.getAdapterCode())) {
      siteRaw = rawRepository.findFirstByRun_RunIdOrderByRawIdAsc(latest.getRunId())
          .map(f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord::getRawPayload)
          .orElse(null);
    }
    long savedTotal = runRepository.sumLedgerSavedByConnection(connection.getConnectionId());
    return EasyCollectionManagementResponse.from(
        connection, latest, readiness(connection), hasCheckpoint, siteRaw, savedTotal);
  }

  private EasyCollectionConnection requireOwnedConnection(Long businessId, Long connectionId) {
    requireBusinessId(businessId);
    EasyCollectionConnection connection = connectionRepository.findById(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("Collection connection not found."));
    if (!businessId.equals(connection.getBusiness().getBusinessId())) {
      throw new IllegalArgumentException("Business does not own this collection connection.");
    }
    return connection;
  }

  private static void requireBusinessId(Long businessId) {
    if (businessId == null) {
      throw new IllegalArgumentException("Business is required.");
    }
  }
}
