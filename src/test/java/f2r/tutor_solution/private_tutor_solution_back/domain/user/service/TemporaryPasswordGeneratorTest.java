package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TemporaryPasswordGenerator — 무작위 10자, 헷갈리는 글자 없음")
class TemporaryPasswordGeneratorTest {

  @Test
  @DisplayName("given 무작위 생성기 when 500번 만들면 then 모두 10자·허용 글자만·대소문자·숫자 포함, 서로 다르다")
  void generate_rules() {
    // given
    SecureRandom random = new SecureRandom();
    Set<String> seen = new HashSet<>();

    // when
    for (int i = 0; i < 500; i++) seen.add(TemporaryPasswordGenerator.generate(random));

    // then
    assertThat(seen).hasSize(500);
    assertThat(seen).allSatisfy(p -> {
      assertThat(p).hasSize(TemporaryPasswordGenerator.LENGTH);
      assertThat(p).matches("[" + TemporaryPasswordGenerator.ALPHABET + "]+");
      assertThat(p).doesNotContainPattern("[0O1lIo]");
      assertThat(p).containsPattern("[A-Z]").containsPattern("[a-z]").containsPattern("[2-9]");
    });
  }

  @Test
  @DisplayName("given 글자 목록 when 확인하면 then 0·O·o·1·l·I가 없다")
  void alphabet_excludesAmbiguous() {
    // given
    String alphabet = TemporaryPasswordGenerator.ALPHABET;

    // when & then
    for (char c : "0Oo1lI".toCharArray()) assertThat(alphabet).doesNotContain(String.valueOf(c));
  }
}
