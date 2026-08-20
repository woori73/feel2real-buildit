package f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LandingVideoSlotTest {

  @Test
  void fixedSlots_keepDisplayOrderAndBundledPaths() {
    // given / when
    var slots = LandingVideoSlotKey.displayOrder();

    // then
    assertThat(slots).containsExactly(
        LandingVideoSlotKey.HERO_BACKGROUND,
        LandingVideoSlotKey.ACADEMY_SHORTS_DEMO,
        LandingVideoSlotKey.PARENT_REPORT_DEMO);
    assertThat(slots)
        .extracting(LandingVideoSlotKey::getBundledPath)
        .containsExactly(
            "/video/landing/hero-background.mp4",
            "/video/landing/academy-shorts-demo.mp4",
            "/video/landing/parent-report-demo.mp4");
  }

  @Test
  void replacingAsset_preservesVisibilityAndStoresOutputMetadata() {
    // given
    LandingVideoSlot slot = LandingVideoSlot.bundled(
        LandingVideoSlotKey.HERO_BACKGROUND, false);

    // when
    slot.replaceAsset("hero.mov", "opaque-video", "opaque-thumbnail", 12, 100L, 1280, 720);

    // then
    assertThat(slot.isVisible()).isFalse();
    assertThat(slot.hasManagedAsset()).isTrue();
    assertThat(slot.getOriginalFilename()).isEqualTo("hero.mov");
    assertThat(slot.getStorageKey()).isEqualTo("opaque-video");
    assertThat(slot.getThumbnailKey()).isEqualTo("opaque-thumbnail");
    assertThat(slot.getDurationSec()).isEqualTo(12);
    assertThat(slot.getSizeBytes()).isEqualTo(100L);
    assertThat(slot.getWidth()).isEqualTo(1280);
    assertThat(slot.getHeight()).isEqualTo(720);
  }
}
