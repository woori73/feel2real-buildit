package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AcademyAdminManagementService.confirmKey — 게시 대상 확인 키")
class AdminConfirmKeyTest {

  private static Academy academy(String handle) {
    return Academy.builder().slug("old-slug").handle(handle).name("학원").intro("소개").build();
  }

  @Test
  @DisplayName("given 핸들이 있는 학원, when 확인 키를 구하면, then 핸들")
  void usesHandle() {
    assertThat(AcademyAdminManagementService.confirmKey(academy("saebom"))).isEqualTo("saebom");
  }

  @Test
  @DisplayName("given 핸들이 없는 기존 학원, when 확인 키를 구하면, then slug (문자열 'null'이 통과하지 않는다)")
  void fallsBackToSlug() {
    assertThat(AcademyAdminManagementService.confirmKey(academy(null))).isEqualTo("old-slug");
    assertThat(AcademyAdminManagementService.confirmKey(academy(" "))).isEqualTo("old-slug");
  }
}
