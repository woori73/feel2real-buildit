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
  @DisplayName("given 카드 묶음 when /learn then 모음 화면에 묶음·학년·학교 거르기 값을 넘긴다")
  void page_passesCatalogToView() {
    // given
    LearnHubCatalog catalog = mock(LearnHubCatalog.class);
    List<LearnHubCatalog.Group> groups = List.of(new LearnHubCatalog.Group("naesin", "내신 대비", "", List.of()));
    when(catalog.groups()).thenReturn(groups);
    when(catalog.schools()).thenReturn(List.of("압구정중학교"));
    ExtendedModelMap model = new ExtendedModelMap();

    // when
    String view = new LearnHubPageController(catalog).page(model);

    // then
    assertThat(view).isEqualTo("public/learn");
    assertThat(model.get("groups")).isSameAs(groups);
    assertThat(model.get("audiences")).isEqualTo(LearnHubCatalog.AUDIENCES);
    assertThat(model.get("schools")).isEqualTo(List.of("압구정중학교"));
    assertThat(LearnHubPageController.PAGE).isEqualTo("/learn");
  }
}
