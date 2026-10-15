package f2r.tutor_solution.private_tutor_solution_back.program;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@DisplayName("ProgramEntry — 비회원·권한부족·허용 상태 판정")
class ProgramEntryTest {

  private static UsernamePasswordAuthenticationToken user(String role) {
    return new UsernamePasswordAuthenticationToken(
        "u", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  @Test
  @DisplayName("given 인증 정보가 없거나 익명 토큰 when 판정하면 then ANON")
  void anonymous_is_anon() {
    assertThat(ProgramEntry.stateOf(Program.DESIGNCRAFT, null)).isEqualTo(ProgramEntry.State.ANON);
    var anon = new AnonymousAuthenticationToken(
        "k", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    assertThat(ProgramEntry.stateOf(Program.DESIGNCRAFT, anon)).isEqualTo(ProgramEntry.State.ANON);
  }

  @Test
  @DisplayName("given TUTOR·ADMIN when DesignCraft를 판정하면 then ALLOWED(관리자는 모든 화면 열람)")
  void tutor_and_admin_are_allowed() {
    assertThat(ProgramEntry.stateOf(Program.DESIGNCRAFT, user("TUTOR")))
        .isEqualTo(ProgramEntry.State.ALLOWED);
    assertThat(ProgramEntry.stateOf(Program.DESIGNCRAFT, user("ADMIN")))
        .isEqualTo(ProgramEntry.State.ALLOWED);
  }

  @Test
  @DisplayName("given STUDENT when DesignCraft를 판정하면 then FORBIDDEN")
  void student_is_forbidden() {
    assertThat(ProgramEntry.stateOf(Program.DESIGNCRAFT, user("STUDENT")))
        .isEqualTo(ProgramEntry.State.FORBIDDEN);
  }
}
