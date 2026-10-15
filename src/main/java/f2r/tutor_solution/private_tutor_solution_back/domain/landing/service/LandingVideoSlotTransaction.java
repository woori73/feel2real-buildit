package f2r.tutor_solution.private_tutor_solution_back.domain.landing.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.repository.LandingVideoSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LandingVideoSlotTransaction {

  private final LandingVideoSlotRepository repository;

  public LandingVideoSlotTransaction(LandingVideoSlotRepository repository) {
    this.repository = repository;
  }

  /**
   * 새 asset swap은 독립 transaction commit까지 끝난 뒤 호출자에게 반환한다. 따라서 호출자가 반환 후
   * 수행하는 이전 asset 정리는 commit보다 앞설 수 없다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Replacement replace(
      LandingVideoSlotKey slot,
      String originalFilename,
      String storageKey,
      String thumbnailKey,
      Integer durationSec,
      Long sizeBytes,
      Integer width,
      Integer height) {
    LandingVideoSlot entity = repository.findBySlotForUpdate(slot)
        .orElseGet(() -> LandingVideoSlot.bundled(slot, true));
    LandingVideoSlotState previous = LandingVideoSlotState.from(entity);
    entity.replaceAsset(
        originalFilename, storageKey, thumbnailKey, durationSec, sizeBytes, width, height);
    LandingVideoSlot saved = repository.saveAndFlush(entity);
    return new Replacement(previous, LandingVideoSlotState.from(saved));
  }

  /**
   * Defensive retry for an absent seeded row that lost an insert race. A fresh transaction is
   * required because the first insert transaction has already rolled back.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Replacement replaceAfterInsertRace(
      LandingVideoSlotKey slot,
      String originalFilename,
      String storageKey,
      String thumbnailKey,
      Integer durationSec,
      Long sizeBytes,
      Integer width,
      Integer height) {
    LandingVideoSlot entity = repository.findBySlotForUpdate(slot)
        .orElseThrow(() -> new IllegalStateException("랜딩 영상 슬롯 생성 충돌을 복구할 수 없습니다."));
    LandingVideoSlotState previous = LandingVideoSlotState.from(entity);
    entity.replaceAsset(
        originalFilename, storageKey, thumbnailKey, durationSec, sizeBytes, width, height);
    repository.flush();
    return new Replacement(previous, LandingVideoSlotState.from(entity));
  }

  /**
   * 공개 상태 변경도 서비스의 외부 transaction 유무와 무관하게 이 호출 반환 전에 commit한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public LandingVideoSlotState updateVisibility(LandingVideoSlotKey slot, boolean visible) {
    LandingVideoSlot entity = repository.findBySlotForUpdate(slot)
        .orElseGet(() -> LandingVideoSlot.bundled(slot, visible));
    entity.updateVisibility(visible);
    return LandingVideoSlotState.from(repository.saveAndFlush(entity));
  }

  public record Replacement(LandingVideoSlotState previous, LandingVideoSlotState current) {}
}
