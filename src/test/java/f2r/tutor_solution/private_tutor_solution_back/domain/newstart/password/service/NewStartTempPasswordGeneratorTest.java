package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("1회용 임시 비밀번호 생성 — 고정값 없음, 받아 적기 쉬운 글자만")
class NewStartTempPasswordGeneratorTest {

  private final NewStartTempPasswordGenerator generator = new NewStartTempPasswordGenerator();

  @Test
  @DisplayName("가입 정책(8자)보다 길고 호출마다 새 값이다")
  void generatedValuesAreLongAndUnpredictable() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      String value = generator.generate();
      assertThat(value).hasSize(NewStartTempPasswordGenerator.LENGTH);
      assertThat(value).isNotEqualTo("reset1234");
      assertThat(value).matches("[A-Za-z0-9]+");
      seen.add(value);
    }
    assertThat(seen).hasSizeGreaterThan(190);
  }

  @Test
  @DisplayName("혼동 글자(0 O o 1 l I)는 쓰지 않고 최소 한 개의 숫자를 포함한다")
  void avoidsAmbiguousCharacters() {
    for (int i = 0; i < 200; i++) {
      String value = generator.generate();
      for (char banned : "0Oo1lI".toCharArray()) {
        assertThat(value).doesNotContain(String.valueOf(banned));
      }
      assertThat(value.chars().anyMatch(c -> c >= '2' && c <= '9')).isTrue();
    }
  }
}
