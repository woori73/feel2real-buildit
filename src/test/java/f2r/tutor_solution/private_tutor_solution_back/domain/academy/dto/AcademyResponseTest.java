package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AcademyResponse.publicUrl — 환경별 공개 URL 계산")
class AcademyResponseTest {

  private Academy academy(String slug, String handle) {
    Academy academy = mock(Academy.class);
    lenient().when(academy.getAcademyId()).thenReturn(1L);
    lenient().when(academy.getSlug()).thenReturn(slug);
    lenient().when(academy.getHandle()).thenReturn(handle);
    lenient().when(academy.getName()).thenReturn("학원");
    lenient().when(academy.getIntro()).thenReturn("소개");
    lenient().when(academy.getStatus()).thenReturn(AcademyStatus.PUBLISHED);
    lenient().when(academy.isVisible()).thenReturn(true);
    lenient().when(academy.getCoverStorageKey()).thenReturn(null);
    lenient().when(academy.getTargetGroups()).thenReturn(Set.of());
    lenient().when(academy.getPageSettings()).thenReturn(null);
    return academy;
  }

  @Test
  @DisplayName("given base-domain+handle when of then publicUrl은 서브도메인 형식")
  void publicUrl_subdomain_when_baseDomain_and_handle() {
    // given
    Academy academy = academy("my-academy", "academy-abc123");

    // when
    AcademyResponse res = AcademyResponse.of(academy, List.of(), List.of(), List.of(), "feel2real.co.kr");

    // then
    assertThat(res.publicUrl()).isEqualTo("https://academy-abc123.feel2real.co.kr/");
  }

  @Test
  @DisplayName("given base-domain 없음(로컬) when of then publicUrl은 경로형")
  void publicUrl_path_when_no_baseDomain() {
    // given
    Academy academy = academy("my-academy", "academy-abc123");

    // when
    AcademyResponse res = AcademyResponse.of(academy, List.of(), List.of(), List.of(), null);

    // then
    assertThat(res.publicUrl()).isEqualTo("/academies/my-academy");
  }

  @Test
  @DisplayName("given base-domain 있지만 handle 없음(전환기) when of then publicUrl은 경로형")
  void publicUrl_path_when_baseDomain_but_no_handle() {
    // given
    Academy academy = academy("my-academy", null);

    // when
    AcademyResponse res = AcademyResponse.of(academy, List.of(), List.of(), List.of(), "feel2real.co.kr");

    // then
    assertThat(res.publicUrl()).isEqualTo("/academies/my-academy");
  }
}
