package f2r.tutor_solution.private_tutor_solution_back.domain.landing.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;

record LandingVideoSlotState(
    LandingVideoSlotKey slot,
    String originalFilename,
    String storageKey,
    String thumbnailKey,
    Integer durationSec,
    Long sizeBytes,
    Integer width,
    Integer height,
    boolean visible) {

  static LandingVideoSlotState from(LandingVideoSlot entity) {
    return new LandingVideoSlotState(
        entity.getSlot(),
        entity.getOriginalFilename(),
        entity.getStorageKey(),
        entity.getThumbnailKey(),
        entity.getDurationSec(),
        entity.getSizeBytes(),
        entity.getWidth(),
        entity.getHeight(),
        entity.isVisible());
  }

  boolean hasManagedAsset() {
    return storageKey != null && !storageKey.isBlank();
  }
}
