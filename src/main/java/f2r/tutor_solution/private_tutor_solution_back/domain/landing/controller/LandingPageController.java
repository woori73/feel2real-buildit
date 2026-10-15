package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoRenderItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.LandingVideoService;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Build It 랜딩("/") 렌더링.
 *
 * <p>Build It Shorts/Report 섹션은 관리형 영상 슬롯을 쓴다. Wiki 섹션의 기출 분석·변형문제 생성
 * 영상은 번들 정적 파일을 직접 참조하므로 모델 값이 필요 없다.
 */
@Controller
@RequiredArgsConstructor
public class LandingPageController {

  private final LandingVideoService landingVideoService;

  @GetMapping("/")
  public String landing(Model model) {
    Map<LandingVideoSlotKey, LandingVideoRenderItem> videos =
        new EnumMap<>(LandingVideoSlotKey.class);
    landingVideoService.resolveRenderItems().forEach(item -> videos.put(item.slot(), item));

    model.addAttribute(
        "academyShortsVideo", required(videos, LandingVideoSlotKey.ACADEMY_SHORTS_DEMO));
    model.addAttribute(
        "parentReportVideo", required(videos, LandingVideoSlotKey.PARENT_REPORT_DEMO));
    return "public/landing";
  }

  private LandingVideoRenderItem required(
      Map<LandingVideoSlotKey, LandingVideoRenderItem> videos, LandingVideoSlotKey slot) {
    LandingVideoRenderItem item = videos.get(slot);
    if (item == null) {
      throw new IllegalStateException("랜딩 영상 슬롯이 누락되었습니다: " + slot);
    }
    return item;
  }
}
