package f2r.tutor_solution.private_tutor_solution_back.domain.wiki.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PublicDisplayName — 실명 첫 글자 + OO 마스킹")
class PublicDisplayNameTest {

  @Test
  @DisplayName("한글/영문 실명은 첫 글자만 남기고 마스킹된다")
  void mask_keepsOnlyFirstCharacter() {
    assertThat(PublicDisplayName.mask("김민수")).isEqualTo("김OO");
    assertThat(PublicDisplayName.mask("Kim")).isEqualTo("KOO");
    assertThat(PublicDisplayName.mask("이")).isEqualTo("이OO");
    assertThat(PublicDisplayName.mask("  박서준  ")).isEqualTo("박OO");
  }

  @Test
  @DisplayName("빈 값/null 은 '익명'")
  void mask_blankBecomesAnonymous() {
    assertThat(PublicDisplayName.mask(null)).isEqualTo("익명");
    assertThat(PublicDisplayName.mask("   ")).isEqualTo("익명");
  }

  @Test
  @DisplayName("전체 실명 · 두 번째 글자 이후는 절대 노출되지 않는다")
  void mask_neverLeaksRest() {
    assertThat(PublicDisplayName.mask("홍길동")).doesNotContain("길", "동");
  }
}
