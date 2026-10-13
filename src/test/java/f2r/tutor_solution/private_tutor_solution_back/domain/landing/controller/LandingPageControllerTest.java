package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantSamples;
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
        // 첫 화면 아래 홈페이지 샘플 섹션(대화형 시작 화면과 같은 샘플)
        .containsEntry("homepageSamples", AcademyAssistantSamples.all())
        .hasSize(3);
  }

  @Test
  @DisplayName("given 랜딩 샘플 섹션 when 모델을 만들면 then 공개해도 되는 샘플 6종(제목·설명·프리셋·요청 문장)만 담긴다")
  void landing_samples_are_public_safe_presets() {
    // given
    when(service.resolveRenderItems()).thenReturn(List.of(
        LandingVideoRenderItem.hidden(LandingVideoSlotKey.ACADEMY_SHORTS_DEMO),
        LandingVideoRenderItem.bundled(LandingVideoSlotKey.PARENT_REPORT_DEMO)));
    Model model = new ConcurrentModel();

    // when
    controller.landing(model);

    // then
    @SuppressWarnings("unchecked")
    List<AcademyAssistantSamples.Sample> samples =
        (List<AcademyAssistantSamples.Sample>) model.getAttribute("homepageSamples");
    assertThat(samples).hasSize(6);
    assertThat(samples).allSatisfy(sample -> {
      assertThat(sample.title()).isNotBlank();
      assertThat(sample.preset()).isNotBlank();
      assertThat(sample.seed()).contains("홈페이지");
    });
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
