package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoRenderItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.LandingVideoService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@DisplayName("LandingPageController — Build It 제품 영상 슬롯 SSR")
class LandingPageControllerTest {

  private final LandingVideoService service = mock(LandingVideoService.class);
  private final LandingPageController controller = new LandingPageController(service);

  @Test
  @DisplayName("given 제품 영상 슬롯 when 루트 렌더 then Shorts/Report 슬롯을 모델에 담고 랜딩 뷰를 반환한다")
  void landing_sets_product_video_models() {
    // given
    LandingVideoRenderItem hero =
        LandingVideoRenderItem.managed(LandingVideoSlotKey.HERO_BACKGROUND);
    LandingVideoRenderItem shorts =
        LandingVideoRenderItem.hidden(LandingVideoSlotKey.ACADEMY_SHORTS_DEMO);
    LandingVideoRenderItem report =
        LandingVideoRenderItem.bundled(LandingVideoSlotKey.PARENT_REPORT_DEMO);
    when(service.resolveRenderItems()).thenReturn(List.of(report, hero, shorts));
    Model model = new ConcurrentModel();

    // when
    String view = controller.landing(model);

    // then
    assertThat(view).isEqualTo("public/landing");
    // 개편 랜딩이 쓰는 관리형 제품 영상 슬롯은 Shorts/Report 두 개다.
    assertThat(model.asMap())
        .containsEntry("academyShortsVideo", shorts)
        .containsEntry("parentReportVideo", report)
        .hasSize(2);
  }

  @Test
  @DisplayName("given 서비스 계약 위반으로 슬롯 누락 when 렌더 then 잘못된 화면을 조용히 만들지 않는다")
  void landing_rejects_missing_fixed_slot() {
    // given
    when(service.resolveRenderItems()).thenReturn(List.of(
        LandingVideoRenderItem.bundled(LandingVideoSlotKey.HERO_BACKGROUND),
        LandingVideoRenderItem.bundled(LandingVideoSlotKey.PARENT_REPORT_DEMO)));

    // when & then
    assertThatThrownBy(() -> controller.landing(new ConcurrentModel()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ACADEMY_SHORTS_DEMO");
  }

  @Test
  @DisplayName("given Report 슬롯 누락 when 렌더 then 잘못된 화면을 조용히 만들지 않는다")
  void landing_rejects_missing_report_slot() {
    // given
    when(service.resolveRenderItems()).thenReturn(List.of(
        LandingVideoRenderItem.bundled(LandingVideoSlotKey.HERO_BACKGROUND),
        LandingVideoRenderItem.bundled(LandingVideoSlotKey.ACADEMY_SHORTS_DEMO)));

    // when & then
    assertThatThrownBy(() -> controller.landing(new ConcurrentModel()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PARENT_REPORT_DEMO");
  }
}
