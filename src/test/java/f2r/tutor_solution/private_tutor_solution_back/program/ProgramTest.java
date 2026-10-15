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
}
