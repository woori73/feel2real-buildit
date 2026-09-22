package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.DiscoverySourceException;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun.RunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialDiscoveryRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewStartOfficialUpdateDiscoveryService {

  private final List<OfficialUpdateDiscoveryAdapter> adapters;
  private final NewStartOfficialUpdateRepository updateRepository;
  private final NewStartOfficialDiscoveryRunRepository runRepository;
  private final NewStartOfficialUpdateService updateService;

  // 소스별 락 - 중복 실행 방지
  private final Lock discoveryLock = new ReentrantLock();

  // 프로덕션 스케줄러 활성화 여부 (기본 false)
  private boolean schedulerEnabled = false;

  // @Transactional 을 붙이지 않는 이유: 어댑터 하나가 실패해도 다른 어댑터/소의 결과와 run 기록이
  // rollback 으로 함께 사라지지 않게 하기 위함 (source별 failure isolation). 각 save 는 자체 커밋된다.
  public DiscoveryResult runDiscoveryForSource(SourceType sourceType, LocalDateTime since) {
    List<OfficialUpdateDiscoveryAdapter> sourceAdapters = adapters.stream()
        .filter(a -> a.getSourceType() == sourceType)
        .toList();
    if (sourceAdapters.isEmpty()) {
      log.warn("발견 어댑터를 찾을 수 없음: {}", sourceType);
      return DiscoveryResult.failed(sourceType, "어댑터 없음");
    }

    // 락 획득 시도 (non-blocking)
    if (!discoveryLock.tryLock()) {
      log.warn("다른 discovery 작업이 실행 중 - 스킵: {}", sourceType);
      return DiscoveryResult.failed(sourceType, "이미 실행 중");
    }

    NewStartOfficialDiscoveryRun runRecord = new NewStartOfficialDiscoveryRun(sourceType);
    runRepository.save(runRecord);

    try {
      log.info("Discovery 시작: source={}, since={}, adapters={}", sourceType, since, sourceAdapters.size());

      int discovered = 0;
      int newReviews = 0;
      int duplicates = 0;
      int irrelevant = 0;
      List<String> errors = new ArrayList<>();

      for (OfficialUpdateDiscoveryAdapter adapter : sourceAdapters) {
        try {
          List<OfficialUpdateDiscoveryAdapter.DiscoveredCandidate> candidates = adapter.discover(since);
          log.info("후보 발견: source={}, adapter={}, count={}", sourceType, adapter.getSourceName(), candidates.size());

          for (OfficialUpdateDiscoveryAdapter.DiscoveredCandidate candidate : candidates) {
            discovered++;

            OfficialUpdateDiscoveryAdapter.DiscoveredCandidate enriched;
            try {
              enriched = adapter.enrich(candidate);
            } catch (Exception e) {
              log.warn("상세 보강 실패 - 목록 정보로 계속 진행: {} - {}", candidate.title(), e.getMessage());
              enriched = candidate;
            }

            RelevancePriority priority = adapter.evaluateRelevance(enriched);

            if (priority == RelevancePriority.IRRELEVANT) {
              irrelevant++;
              log.debug("IRRELEVANT로 분류되어 저장 안 함: {}", enriched.title());
              continue;
            }

            String dedupKey = adapter.buildDedupKey(enriched);
            if (updateRepository.findByDedupKey(dedupKey).isPresent()) {
              duplicates++;
              log.debug("중복으로 저장 안 함: {}", enriched.title());
              continue;
            }

            try {
              var request = new NewStartOfficialUpdateRequest(
                  sourceType,
                  adapter.getSourceName(),
                  enriched.title(),
                  enriched.summary(),
                  enriched.originalUrl(),
                  enriched.officialDocumentId(),
                  enriched.publishedAt(),
                  enriched.effectiveFrom(),
                  enriched.effectiveTo(),
                  enriched.category()
              );

              updateService.createReview(request, priority, LocalDateTime.now());
              newReviews++;
              log.info("REVIEW 등록 완료: source={}, title={}, priority={}",
                  sourceType, enriched.title(), priority);
            } catch (IllegalArgumentException e) {
              duplicates++;
              log.debug("저장 실패(중복 등): {}", enriched.title());
            }
          }
        } catch (DiscoverySourceException e) {
          errors.add(e.getMessage());
          log.error("Adapter discovery 실패 (SUCCESS 처리 금지): source={}, adapter={}", sourceType,
              adapter.getSourceName(), e);
        } catch (Exception e) {
          errors.add("[" + adapter.getSourceName() + "] 예상치 못한 오류: " + e.getMessage());
          log.error("Adapter discovery 예상치 못한 오류: source={}", sourceType, e);
        }
      }

      if (!errors.isEmpty()) {
        String message = String.join(" / ", errors);
        runRecord.setCounts(discovered, newReviews, duplicates, irrelevant);
        runRecord.fail(message);
        runRepository.save(runRecord);
        log.warn("Discovery FAILED: source={}, discovered={}, newReviews={}, errors={}",
            sourceType, discovered, newReviews, message);
        return DiscoveryResult.failed(sourceType, message);
      }

      runRecord.complete(discovered, newReviews, duplicates, irrelevant);
      runRepository.save(runRecord);

      log.info("Discovery 완료: source={}, discovered={}, newReviews={}, duplicates={}, irrelevant={}",
          sourceType, discovered, newReviews, duplicates, irrelevant);

      return DiscoveryResult.success(sourceType, discovered, newReviews, duplicates, irrelevant);

    } catch (Exception e) {
      log.error("Discovery 실패: source={}", sourceType, e);
      runRecord.fail(e.getMessage());
      runRepository.save(runRecord);
      return DiscoveryResult.failed(sourceType, e.getMessage());
    } finally {
      discoveryLock.unlock();
    }
  }

  @Transactional(readOnly = true)
  public List<NewStartOfficialDiscoveryRun> getRecentRuns(SourceType sourceType, int limit) {
    return runRepository.findBySourceTypeOrderByRunStartedAtDesc(sourceType).stream()
        .limit(limit)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<NewStartOfficialDiscoveryRun> getRecentRunsAllSources(LocalDateTime since, int limit) {
    return runRepository.findByRunStartedAtBetweenOrderByRunStartedAtDesc(since, LocalDateTime.now()).stream()
        .limit(limit)
        .toList();
  }

  // 수동 실행용
  public DiscoveryResult runDiscoveryAllSources(LocalDateTime since) {
    List<DiscoveryResult> results = new ArrayList<>();
    for (SourceType sourceType : adapters.stream().map(OfficialUpdateDiscoveryAdapter::getSourceType).distinct().toList()) {
      results.add(runDiscoveryForSource(sourceType, since));
    }
    return DiscoveryResult.combined(results);
  }

  // 스케줄러용 메서드 - 프로덕션에서만 활성화
  @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Seoul")
  public void scheduledDiscoveryMorning() {
    if (!schedulerEnabled) {
      log.debug("스케줄러 비활성화됨 - 오전 discovery 스킵");
      return;
    }
    log.info("=== 오전 11:00 KST 자동 Discovery 시작 ===");
    runDiscoveryAllSources(LocalDateTime.now().minusDays(1)); // 최근 1일
  }

  @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
  public void scheduledDiscoveryEvening() {
    if (!schedulerEnabled) {
      log.debug("스케줄러 비활성화됨 - 오후 discovery 스킵");
      return;
    }
    log.info("=== 오후 21:00 KST 자동 Discovery 시작 ===");
    runDiscoveryAllSources(LocalDateTime.now().minusDays(1)); // 최근 1일
  }

  public void setSchedulerEnabled(boolean enabled) {
    this.schedulerEnabled = enabled;
    log.info("Discovery 스케줄러 {}", enabled ? "활성화" : "비활성화");
  }

  public boolean isSchedulerEnabled() {
    return schedulerEnabled;
  }

  // 결과 클래스
  public static class DiscoveryResult {
    private final SourceType sourceType;
    private final boolean success;
    private final int discovered;
    private final int newReviews;
    private final int duplicates;
    private final int irrelevant;
    private final String errorMessage;
    private final List<DiscoveryResult> subResults;

    private DiscoveryResult(SourceType sourceType, boolean success, int discovered, int newReviews,
        int duplicates, int irrelevant, String errorMessage, List<DiscoveryResult> subResults) {
      this.sourceType = sourceType;
      this.success = success;
      this.discovered = discovered;
      this.newReviews = newReviews;
      this.duplicates = duplicates;
      this.irrelevant = irrelevant;
      this.errorMessage = errorMessage;
      this.subResults = subResults;
    }

    public static DiscoveryResult success(SourceType sourceType, int discovered, int newReviews,
        int duplicates, int irrelevant) {
      return new DiscoveryResult(sourceType, true, discovered, newReviews, duplicates, irrelevant, null, null);
    }

    public static DiscoveryResult failed(SourceType sourceType, String errorMessage) {
      return new DiscoveryResult(sourceType, false, 0, 0, 0, 0, errorMessage, null);
    }

    public static DiscoveryResult combined(List<DiscoveryResult> results) {
      return new DiscoveryResult(null, results.stream().allMatch(DiscoveryResult::isSuccess), 0, 0, 0, 0, null, results);
    }

    // getters
    public SourceType getSourceType() { return sourceType; }
    public boolean isSuccess() { return success; }
    public int getDiscovered() { return discovered; }
    public int getNewReviews() { return newReviews; }
    public int getDuplicates() { return duplicates; }
    public int getIrrelevant() { return irrelevant; }
    public String getErrorMessage() { return errorMessage; }
    public List<DiscoveryResult> getSubResults() { return subResults; }
  }
}