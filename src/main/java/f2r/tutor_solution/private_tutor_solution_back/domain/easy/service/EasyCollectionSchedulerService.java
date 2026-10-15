package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyCollectionRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionConnectionStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionTriggerType;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionConnectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectionSourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
@RequiredArgsConstructor
public class EasyCollectionSchedulerService {

  private final EasyBusinessRepository businessRepository;
  private final EasyCollectionSourceRepository sourceRepository;
  private final EasyCollectionConnectionRepository connectionRepository;
  private final EasyCollectionRunRepository runRepository;
  private final EasyCollectionIngestService ingestService;

  @Autowired
  private EasyCollectionRunExecutionGate executionGate;

/**
    * 자동 수집 크론 — 환경변수로 오버라이드 가능 (기본 02:30 KST 매일).
    * 활성 연결(ACTIVE, connectionRef 있음)만 순차 실행. 동시 실행 방지는 DB 레이스 컨디션 허용 후
    * run 단위 락으로 처리(향후 lease 패턴 적용 시 교체).
    * <p>
    * 이 메서드는 @Transactional이 아니다. 각 연결의 수집은 {@link EasyCollectionIngestService#ingestByAdapter}
    * 내부에서 독립된 트랜잭션으로 수행된다. 외부 API 호출이 긴 트랜잭션을 점유하지 않도록 분리한다.
    * </p>
    */
  @Scheduled(cron = "${easy.collection.cron:0 30 2 * * *}", zone = "Asia/Seoul")
  public void runScheduledCollections() {
    log.info("자동 수집 크론 시작: {}", LocalDateTime.now());

    List<EasyCollectionSource> activeSources = sourceRepository.findAllByActiveTrue();
    if (activeSources.isEmpty()) {
      log.info("활성 수집원이 없습니다.");
      return;
    }

    for (EasyCollectionSource source : activeSources) {
      if (!source.isActive()) continue;

      // 이미 실행 중인 run이 있는 연결은 건너뛰기 (RUNNING 상태 체크)
      List<EasyCollectionConnection> connections = connectionRepository
          .findBySource_SourceIdAndStatus(source.getSourceId(), EasyCollectionConnectionStatus.ACTIVE);

      for (EasyCollectionConnection conn : connections) {
        if (conn.getConnectionRef() == null || conn.getConnectionRef().isBlank()) {
          log.warn("connectionRef 없음 - 건너뜀: connectionId={}, source={}", conn.getConnectionId(), source.getCode());
          continue;
        }

        // NEEDS_REAUTH 상태면 자동 실행 안 함
        if (conn.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
          log.warn("재인증 필요 - 자동 실행 건너뜀: connectionId={}", conn.getConnectionId());
          continue;
        }

        if (executionGate != null
            && executionGate.readiness(conn) != EasyCollectionProviderReadiness.READY) {
          log.info("수집 readiness 미충족 - 건너뜀: connectionId={}, readiness={}",
              conn.getConnectionId(), executionGate.readiness(conn));
          continue;
        }

        try {
          log.info("자동 수집 실행: connectionId={}, source={}, adapter={}", conn.getConnectionId(), source.getCode(), source.getAdapterType());
          // since는 null(전체) 또는 마지막 성공시각으로 증분 수집
          LocalDateTime since = conn.getLastSuccessAt();
          ingestService.ingestByAdapter(
              conn.getBusiness().getBusinessId(),
              conn.getLabel(),
              source.getCode(),
              EasyCollectionTriggerType.SCHEDULED,
              since,
              5000
          );
        } catch (IllegalStateException e) {
          // ingestByAdapter에서 RUNNING 중복 등으로 던지는 예외
          log.warn("수집 스킵: connectionId={}, reason={}", conn.getConnectionId(), e.getMessage());
        } catch (Exception e) {
          log.error("자동 수집 실패: connectionId={}, error={}", conn.getConnectionId(), e.getMessage(), e);
        }
      }
    }

    log.info("자동 수집 크론 완료: {}", LocalDateTime.now());
  }

  /**
   * 수동 트리거 — 관리자가 "지금 수집" 버튼으로 즉시 실행.
   * 스케줄과 동일한 파이프라인이나 triggerType=MANUAL로 기록.
   * 동시 실행 방지는 {@link EasyCollectionIngestService#ingestByAdapter} 내부에서
   * 비관적 락으로 원자적으로 수행된다.
   */
  @Transactional
  public EasyCollectionRunResponse triggerManualCollection(Long businessId, Long connectionId) {
    EasyCollectionConnection conn = connectionRepository.findById(connectionId)
        .orElseThrow(() -> new IllegalArgumentException("연결을 찾을 수 없습니다: " + connectionId));

    if (!conn.getBusiness().getBusinessId().equals(businessId)) {
      throw new IllegalArgumentException("사업체 불일치");
    }

    if (conn.getConnectionRef() == null || conn.getConnectionRef().isBlank()) {
      throw new IllegalStateException("connectionRef가 설정되지 않았습니다.");
    }

    if (conn.getStatus() == EasyCollectionConnectionStatus.NEEDS_REAUTH) {
      throw new IllegalStateException("재인증 필요 상태입니다. 재인증 후 다시 시도하세요.");
    }

    // RUNNING 체크는 ingestByAdapter 내부에서 비관적 락으로 원자적으로 수행
    EasyCollectionSource source = conn.getSource();
    return ingestService.ingestByAdapter(businessId, conn.getLabel(), source.getCode(),
        EasyCollectionTriggerType.MANUAL, null, 5000).run();
  }

  /**
   * 연결 상태별 통계 — 대시보드용
   */
  public List<ConnectionStatusSummary> getConnectionStatusSummary(Long businessId) {
    List<EasyCollectionConnection> connections = connectionRepository.findByBusiness_BusinessId(businessId);
    return connections.stream().map(conn -> {
      EasyCollectionRun latestRun = runRepository.findFirstByConnection_ConnectionIdOrderByRunIdDesc(conn.getConnectionId())
          .orElse(null);
      return new ConnectionStatusSummary(
          conn.getConnectionId(),
          conn.getLabel(),
          conn.getSource().getCode(),
          conn.getSource().getName(),
          conn.getStatus().name(),
          conn.getConnectionRef() != null && !conn.getConnectionRef().isBlank(),
          conn.getLastSuccessAt(),
          latestRun != null ? latestRun.getStatus().name() : null,
          latestRun != null ? latestRun.getAcceptedCount() : 0,
          latestRun != null ? latestRun.getDuplicateCount() : 0,
          latestRun != null ? latestRun.getErrorCount() : 0
      );
    }).toList();
  }

  public record ConnectionStatusSummary(
      Long connectionId,
      String label,
      String sourceCode,
      String sourceName,
      String status,
      boolean hasConnectionRef,
      LocalDateTime lastSuccessAt,
      String latestRunStatus,
      int latestAcceptedCount,
      int latestDuplicateCount,
      int latestErrorCount
  ) {}

}
