package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyBusinessInfo;
import java.util.List;
import java.util.Map;

/** Builder/page-settings partial update request. Null URL fields mean keep existing URL values. */
public record AcademyPageSettingsUpdateRequest(
    String blogUrl,
    String storeUrl,
    String naverMapUrl,
    String heroYoutubeUrl,
    Map<String, Boolean> sectionVisibility,
    List<String> sectionOrder,
    AcademyBusinessInfo businessInfo) {

  public AcademyPageSettingsUpdateRequest(
      String blogUrl,
      String storeUrl,
      String naverMapUrl,
      String heroYoutubeUrl,
      Map<String, Boolean> sectionVisibility,
      List<String> sectionOrder) {
    this(blogUrl, storeUrl, naverMapUrl, heroYoutubeUrl, sectionVisibility, sectionOrder, null);
  }
}
