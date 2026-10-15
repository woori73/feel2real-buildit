package f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;

public record LandingVideoAdminResponse(
    LandingVideoSlotKey slot,
    String label,
    String description,
    boolean visible,
    LandingVideoSourceType sourceType,
    boolean hasManagedAsset,
    String originalFilename,
    Integer durationSec,
    Long sizeBytes,
    Integer width,
    Integer height,
    String previewUrl,
    String thumbnailUrl) {

  public static LandingVideoAdminResponse bundled(LandingVideoSlotKey slot, boolean visible) {
    return new LandingVideoAdminResponse(
        slot,
        slot.getLabel(),
        slot.getDescription(),
        visible,
        LandingVideoSourceType.BUNDLED,
        false,
        null,
        null,
        null,
        null,
        null,
        slot.getBundledPath(),
        null);
  }
}
