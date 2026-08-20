package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlock;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageLayoutService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("AcademyLayoutApiController — 레이아웃 생성/수정/삭제 API")
class AcademyLayoutApiControllerTest {

  private final AcademyService academyService = mock(AcademyService.class);
  private final AcademyPageLayoutService academyPageLayoutService = mock(AcademyPageLayoutService.class);

  private final AcademyLayoutApiController controller =
      new AcademyLayoutApiController(academyService, academyPageLayoutService);

  private final JwtTokenProvider.AuthenticatedPrincipal principal =
      mock(JwtTokenProvider.AuthenticatedPrincipal.class);

  @Test
  @DisplayName("getMyLayout — given 내 Academy when 조회 then resolveLayoutSchema 결과 200")
  void getMyLayout_returns_resolved_schema() {
    // given
    Academy academy = mock(Academy.class);
    AcademyPageLayoutSchema schema = new AcademyPageLayoutSchema(1, List.of());
    when(academyService.requireMyAcademy(principal)).thenReturn(academy);
    when(academyPageLayoutService.resolveLayoutSchema(academy)).thenReturn(schema);

    // when
    ResponseEntity<AcademyPageLayoutSchema> response = controller.getMyLayout(principal);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(schema);
  }

  @Test
  @DisplayName("upsertMyLayout — given schema when PUT then saveLayout 호출 후 해석 결과 200")
  void upsertMyLayout_saves_then_returns_resolved() {
    // given
    Academy academy = mock(Academy.class);
    AcademyPageLayoutSchema body = new AcademyPageLayoutSchema(
        1, List.of(new AcademyLayoutBlock("hero", 0, Map.of("headline", "학원"))));
    AcademyPageLayoutSchema resolved = new AcademyPageLayoutSchema(1, List.of());
    when(academyService.requireMyAcademy(principal)).thenReturn(academy);
    when(academyPageLayoutService.resolveLayoutSchema(academy)).thenReturn(resolved);

    // when
    ResponseEntity<AcademyPageLayoutSchema> response = controller.upsertMyLayout(principal, body);

    // then
    verify(academyPageLayoutService).saveLayout(academy, body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(resolved);
  }

  @Test
  @DisplayName("deleteMyLayout — given 내 Academy when DELETE then deleteLayout 호출 후 204")
  void deleteMyLayout_deletes_and_returns_no_content() {
    // given
    Academy academy = mock(Academy.class);
    when(academyService.requireMyAcademy(principal)).thenReturn(academy);

    // when
    ResponseEntity<Void> response = controller.deleteMyLayout(principal);

    // then
    verify(academyPageLayoutService).deleteLayout(academy);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
