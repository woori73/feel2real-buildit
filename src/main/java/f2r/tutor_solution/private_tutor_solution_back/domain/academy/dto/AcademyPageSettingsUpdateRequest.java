package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyBusinessInfo;
import java.util.List;
import java.util.Map;

/**
 * Builder/page-settings partial update request. Null URL fields mean keep existing URL values.
 *
 * <p>builderDraft stores tenant feature settings as JSON strings:
 * <ul>
 *   <li>{@code tenant.campaignPopup} - Campaign popup config</li>
 *   <li>{@code tenant.featuredCta} - Featured CTA config</li>
 *   <li>{@code tenant.liveMode} - Live mode config</li>
 *   <li>{@code tenant.videoFilter} - Video filter config</li>
 *   <li>{@code tenant.contentTools} - ArtCraft AI editor content tools config (Phase 1)</li>
 * </ul>
 */
public record AcademyPageSettingsUpdateRequest(
    String blogUrl,
    String storeUrl,
    String naverMapUrl,
    String heroYoutubeUrl,
    Map<String, Boolean> sectionVisibility,
    List<String> sectionOrder,
    AcademyBusinessInfo businessInfo,
    Map<String, String> builderDraft) {

  public AcademyPageSettingsUpdateRequest(
      String blogUrl,
      String storeUrl,
      String naverMapUrl,
      String heroYoutubeUrl,
      Map<String, Boolean> sectionVisibility,
      List<String> sectionOrder) {
    this(blogUrl, storeUrl, naverMapUrl, heroYoutubeUrl, sectionVisibility, sectionOrder, null, null);
  }

  public AcademyPageSettingsUpdateRequest(
      String blogUrl,
      String storeUrl,
      String naverMapUrl,
      String heroYoutubeUrl,
      Map<String, Boolean> sectionVisibility,
      List<String> sectionOrder,
      AcademyBusinessInfo businessInfo) {
    this(blogUrl, storeUrl, naverMapUrl, heroYoutubeUrl, sectionVisibility, sectionOrder, businessInfo, null);
  }
}
