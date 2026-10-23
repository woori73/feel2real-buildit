package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("제작소 권한 — modu는 초대 없이 EDITOR, ADMIN 권한으로 확대되지 않음")
class StudioServiceOperatorPermissionTest {

  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final StudioAccessGuard guard = mock(StudioAccessGuard.class);
  private StudioService studio;

  private final AuthenticatedPrincipal modu = new AuthenticatedPrincipal(42L, "modu", "TUTOR");
  private final AuthenticatedPrincipal tutor = new AuthenticatedPrincipal(11L, "tutor", "TUTOR");

  @BeforeEach
  void setUp() {
    studio = new StudioService(db, mock(FileStorage.class));
    ReflectionTestUtils.setField(studio, "access", guard);
    when(guard.isOperator(modu)).thenReturn(true);
  }

  @SuppressWarnings("unchecked")
  private void memberRow(long userId, List<String> rows) {
    when(db.query(anyString(), any(RowMapper.class), eq(StudioService.PROJECT), eq(userId))).thenReturn(rows);
  }

  @Test
  @DisplayName("초대 행이 없는 modu는 EDITOR — 조회·수정은 되고 승인은 403")
  void givenModuWithoutMember_whenPermission_thenEditorWithoutApproval() {
    // given
    memberRow(42L, List.of());
    // when
    String permission = studio.permission(modu);
    // then
    assertThat(permission).isEqualTo("EDITOR");
    assertThatThrownBy(() -> studio.require(modu, false, true))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("ADMIN이 접근 관리에서 부여한 modu 권한(APPROVER)은 그대로 존중한다")
  void givenModuWithApproverRow_whenPermission_thenApprover() {
    // given
    memberRow(42L, List.of("APPROVER"));
    // when / then
    assertThat(studio.permission(modu)).isEqualTo("APPROVER");
  }

  @Test
  @DisplayName("modu가 아닌 비회원 강사는 기존대로 403")
  void givenOtherTutorWithoutMember_whenPermission_thenForbidden() {
    // given
    memberRow(11L, List.of());
    // when / then
    assertThatThrownBy(() -> studio.permission(tutor)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("modu는 프로젝트 개설·접근 관리 같은 ADMIN 기능을 쓸 수 없다")
  void givenModu_whenAdminOnlyActions_thenForbidden() {
    // when / then
    assertThatThrownBy(() -> studio.initialize(modu)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> studio.members(modu)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> studio.member(modu, 1L, "APPROVER"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
