package f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;

public record LandingVideoRenderItem(
    LandingVideoSlotKey slot,
    boolean visible,
    String sourceUrl) {

  public static LandingVideoRenderItem hidden(LandingVideoSlotKey slot) {
    return new LandingVideoRenderItem(slot, false, null);
  }

  public static LandingVideoRenderItem bundled(LandingVideoSlotKey slot) {
    return new LandingVideoRenderItem(slot, true, slot.getBundledPath());
  }

  public static LandingVideoRenderItem managed(LandingVideoSlotKey slot) {
    return new LandingVideoRenderItem(
        slot,
        true,
        "/api/landing/videos/" + slot.name() + "/stream");
  }
}
