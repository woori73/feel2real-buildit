package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTask;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectorTaskStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyLocalCollector;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyCollectorTaskRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyLocalCollectorRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task lifecycle for the Visible Local Browser Collector.
 * Admin-facing methods (start/status/list/cancel) are scoped by businessId (ROLE_ADMIN).
 * Agent-facing methods (claimNext/advance/complete/fail) are scoped by collectorId — the
 * collector agent authenticates as itself (its own pairing token), never as a business/admin,
 * so it can only ever touch tasks it owns (see {@code ownedTask}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EasyCollectorTaskService {

  private static final List<EasyCollectorTaskStatus> TERMINAL =
      List.of(EasyCollectorTaskStatus.COMPLETED, EasyCollectorTaskStatus.FAILED,
          EasyCollectorTaskStatus.CANCELLED);

  private final EasyLocalCollectorRepository collectorRepository;
  private final EasyCollectorTaskRepository taskRepository;

  @Transactional
  public EasyCollectorTask start(Long businessId, Long collectorId, String sourceCode,
      String dataType, LocalDate requestedFrom, LocalDate requestedTo) {
    EasyLocalCollector collector = ownedCollector(collectorId, businessId);
    if (taskRepository
        .existsByCollector_CollectorIdAndSourceCodeAndDataTypeAndRequestedFromAndRequestedToAndStatusNotIn(
            collectorId, sourceCode, dataType, requestedFrom, requestedTo, TERMINAL)) {
      throw new IllegalArgumentException("이미 진행 중인 동일 조건의 작업이 있습니다.");
    }
    return taskRepository.save(EasyCollectorTask.builder()
        .collector(collector).business(collector.getBusiness()).sourceCode(sourceCode)
        .dataType(dataType).requestedFrom(requestedFrom).requestedTo(requestedTo).build());
  }

  public EasyCollectorTask status(Long taskId, Long businessId) {
    EasyCollectorTask task = requireTask(taskId);
    requireBusiness(task.getBusiness(), businessId);
    return task;
  }

  public List<EasyCollectorTask> list(Long businessId) {
    if (businessId == null) throw new IllegalArgumentException("businessId is required.");
    return taskRepository.findAllByBusiness_BusinessIdOrderByTaskIdDesc(businessId);
  }

  @Transactional
  public EasyCollectorTask cancel(Long taskId, Long businessId) {
    EasyCollectorTask task = requireTask(taskId);
    requireBusiness(task.getBusiness(), businessId);
    task.cancel();
    return task;
  }

  /** Agent poll: claims (STARTING -&gt; BROWSER_OPEN) the oldest queued task for this collector. */
  @Transactional
  public Optional<EasyCollectorTask> claimNext(Long collectorId) {
    return taskRepository
        .findFirstByCollector_CollectorIdAndStatusOrderByTaskIdAsc(
            collectorId, EasyCollectorTaskStatus.STARTING)
        .map(task -> {
          task.advanceTo(EasyCollectorTaskStatus.BROWSER_OPEN);
          return task;
        });
  }

  @Transactional
  public EasyCollectorTask advance(Long taskId, Long collectorId, EasyCollectorTaskStatus next) {
    EasyCollectorTask task = ownedTask(taskId, collectorId);
    task.advanceTo(next);
    return task;
  }

  @Transactional
  public EasyCollectorTask complete(Long taskId, Long collectorId, String filename, long size,
      String sha256) {
    EasyCollectorTask task = ownedTask(taskId, collectorId);
    task.complete(filename, size, sha256);
    return task;
  }

  @Transactional
  public EasyCollectorTask fail(Long taskId, Long collectorId, String reason) {
    EasyCollectorTask task = ownedTask(taskId, collectorId);
    task.fail(reason);
    return task;
  }

  private EasyLocalCollector ownedCollector(Long collectorId, Long businessId) {
    if (collectorId == null) throw new IllegalArgumentException("collectorId is required.");
    EasyLocalCollector collector = collectorRepository.findById(collectorId)
        .orElseThrow(() -> new IllegalArgumentException("Collector not found: " + collectorId));
    requireBusiness(collector.getBusiness(), businessId);
    if (!collector.isActive()) throw new IllegalArgumentException("Collector is revoked.");
    return collector;
  }

  /** Agent-side ownership check — a collector may only act on its own tasks. */
  private EasyCollectorTask ownedTask(Long taskId, Long collectorId) {
    EasyCollectorTask task = requireTask(taskId);
    if (!task.getCollector().getCollectorId().equals(collectorId)) {
      throw new IllegalArgumentException("Cross-collector task access is forbidden.");
    }
    return task;
  }

  private EasyCollectorTask requireTask(Long taskId) {
    return taskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
  }

  private static void requireBusiness(EasyBusiness business, Long businessId) {
    if (businessId == null) throw new IllegalArgumentException("businessId is required.");
    if (business == null || business.getBusinessId() == null
        || !business.getBusinessId().equals(businessId)) {
      throw new IllegalArgumentException("Cross-business reference is forbidden.");
    }
  }
}
