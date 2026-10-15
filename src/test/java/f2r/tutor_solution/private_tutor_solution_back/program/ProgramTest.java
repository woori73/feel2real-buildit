package f2r.tutor_solution.private_tutor_solution_back.program;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Program — 프로그램 영역과 복귀 URL 판정")
class ProgramTest {

  @Test
  @DisplayName("given DesignCraft when 소개·시작 경로와 쿼리가 붙은 경로를 보면 then 모두 자기 영역으로 판정한다")
  void owns_intro_start_and_query_paths() {
    // given
    Program p = Program.DESIGNCRAFT;

    // when & then
    assertThat(p.ownsReturnUrl("/designcraft")).isTrue();
    assertThat(p.ownsReturnUrl("/designcraft/editor")).isTrue();
    assertThat(p.ownsReturnUrl("/designcraft/editor?job=7")).isTrue();
  }

  @Test
  @DisplayName("given DesignCraft when 접두사만 같은 다른 경로나 null을 보면 then 자기 영역이 아니다")
  void does_not_own_lookalike_paths() {
    // given
    Program p = Program.DESIGNCRAFT;

    // when & then
    assertThat(p.ownsReturnUrl("/designcraftx")).isFalse();
    assertThat(p.ownsReturnUrl("/designcrafts/editor")).isFalse();
    assertThat(p.ownsReturnUrl("/newstart")).isFalse();
    assertThat(p.ownsReturnUrl(null)).isFalse();
  }

  @Test
  @DisplayName("given 등록된 프로그램 when 임의 경로를 확인하면 then 등록 영역만 true다")
  void registered_return_url_only_for_known_programs() {
    // when & then
    assertThat(Program.isRegisteredReturnUrl("/designcraft/editor")).isTrue();
    assertThat(Program.isRegisteredReturnUrl("/admin/dashboard")).isFalse();
    assertThat(Program.isRegisteredReturnUrl("/")).isFalse();
  }

  @Test
  @DisplayName("given BuildIt 제작기 when 소개·샘플 경로를 보면 then 자기 영역이고 TUTOR·ADMIN만 허용한다")
  void builder_owns_its_paths_and_roles() {
    // given
    Program p = Program.BUILDER;

    // when & then
    assertThat(p.ownsReturnUrl("/builder")).isTrue();
    assertThat(p.ownsReturnUrl("/builder/entry")).isTrue();
    assertThat(p.ownsReturnUrl("/builders")).isFalse();
    assertThat(p.getRoles()).containsExactlyInAnyOrder("TUTOR", "ADMIN");
    assertThat(Program.isRegisteredReturnUrl("/builder")).isTrue();
  }

  @Test
  @DisplayName("given 문제지 제작기 when 소개·하위 경로를 보면 then 자기 영역이고 STUDENT·TUTOR·ADMIN을 허용한다")
  void problem_generator_owns_its_paths_and_roles() {
    // given
    Program p = Program.PROBLEM_GENERATOR;

    // when & then
    assertThat(p.ownsReturnUrl("/problem-generator")).isTrue();
    assertThat(p.ownsReturnUrl("/problem-generator/exam")).isTrue();
    assertThat(p.ownsReturnUrl("/problem-generators")).isFalse();
    assertThat(p.getRoles()).containsExactlyInAnyOrder("STUDENT", "TUTOR", "ADMIN");
  }

  @Test
  @DisplayName("given 이지장부·Easy 장부 when 정확한 소개 경로와 하위 경로를 조회하면 then 소개 경로만 forIntroPath, 하위는 forPath로 찾는다")
  void ledger_and_easy_lookup_by_intro_and_sub_path() {
    // when & then
    assertThat(Program.forIntroPath("/ledger")).contains(Program.LEDGER);
    assertThat(Program.forIntroPath("/easy")).contains(Program.EASY);
    assertThat(Program.forIntroPath("/easy/students")).isEmpty();
    assertThat(Program.forPath("/easy/students")).contains(Program.EASY);
    assertThat(Program.forPath("/ledger/accounts")).contains(Program.LEDGER);
    assertThat(Program.LEDGER.getRoles()).containsExactlyInAnyOrder("TUTOR", "ADMIN");
    assertThat(Program.EASY.getRoles()).containsExactly("ADMIN");
  }
}
