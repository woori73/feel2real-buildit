package f2r.tutor_solution.private_tutor_solution_back.domain.learnhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

@DisplayName("LearnHubPageController — /learn 화면")
class LearnHubPageControllerTest {

  @Test
  @DisplayName("given 탭 목록 when /learn?tab=toeic then 풀이 화면에 탭·고른 탭·학년·학교 거르기 값을 넘긴다")
  void page_passesTabsToView() {
    // given
    LearnHubCatalog catalog = mock(LearnHubCatalog.class);
    List<LearnHubCatalog.Tab> tabs = List.of(new LearnHubCatalog.Tab("toeic", "TOEIC", "", "로그인 필요", true, List.of()));
    when(catalog.tabs()).thenReturn(tabs);
    when(catalog.grades()).thenReturn(List.of("중1", "중2"));
    when(catalog.schools()).thenReturn(List.of("압구정중학교"));
    ExtendedModelMap model = new ExtendedModelMap();

    // when
    String view = new LearnHubPageController(catalog).page("toeic", model);

    // then
    assertThat(view).isEqualTo("public/learn");
    assertThat(model.get("tabs")).isSameAs(tabs);
    assertThat(model.get("activeTab")).isEqualTo("toeic");
    assertThat(model.get("grades")).isEqualTo(List.of("중1", "중2"));
    assertThat(model.get("schools")).isEqualTo(List.of("압구정중학교"));
    assertThat(LearnHubPageController.PAGE).isEqualTo("/learn");
  }

  @Test
  @DisplayName("given 모르는 tab 값 when /learn then 첫 탭(내신)을 연다")
  void page_unknownTabOpensNaesin() {
    // given
    LearnHubCatalog catalog = mock(LearnHubCatalog.class);
    ExtendedModelMap model = new ExtendedModelMap();

    // when
    new LearnHubPageController(catalog).page("<script>", model);

    // then
    assertThat(model.get("activeTab")).isEqualTo("naesin");
  }
}
