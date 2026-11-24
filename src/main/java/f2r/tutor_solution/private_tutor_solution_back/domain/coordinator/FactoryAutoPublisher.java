package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 자동 게시(원장 지시 2026-11-24): 1분마다 아직 올리지 않은 시험 묶음을 훑어, 완료 + 검수 3곳 통과한 작업을 학생 풀이 페이지에 올린다
 * ({@link CoordFactoryService#autoPublish}). 한 건이 실패해도(시험 코드 겹침 등) 다른 건은 계속 올리고, 실패한 건은 다시 시작할 때까지
 * 건너뛴다 — 원장이 현황판에서 이유를 보고 직접 처리한다.
 */
@Component
public class FactoryAutoPublisher {

  private static final Logger log = LoggerFactory.getLogger(FactoryAutoPublisher.class);

  private final FactoryExamPackageRepository packages;
  private final CoordFactoryService factory;
  private final Set<String> failed = new HashSet<>();

  public FactoryAutoPublisher(FactoryExamPackageRepository packages, CoordFactoryService factory) {
    this.packages = packages;
    this.factory = factory;
  }

  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  public void sweep() {
    for (FactoryExamPackage pkg : packages.findAllByExamIdIsNull()) {
      String jobId = pkg.getJobId();
      if (failed.contains(jobId)) continue;
      try {
        String url = factory.autoPublish(jobId);
        if (!url.isEmpty()) log.info("자동 게시: job={} exam={} → {}", jobId, pkg.getExamCode(), url);
      } catch (RuntimeException e) {
        failed.add(jobId);
        log.warn("자동 게시 실패(원장 확인 필요): job={} exam={} — {}", jobId, pkg.getExamCode(), e.getMessage());
      }
    }
  }
}
