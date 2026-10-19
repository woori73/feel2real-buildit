package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarAiFindService;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankAccessGuard;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service.GrammarBankService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 문법 문제은행 API의 서버 측 접근 강제 검증 — 프론트 메뉴 숨김이 아니라 컨트롤러가 실제로 차단한다.
 */
@ExtendWith(MockitoExtension.class)
class GrammarBankControllerAccessTest {

  @Mock GrammarBankService bankService;
  @Mock GrammarBankImportService importService;
  @Mock GrammarAiFindService aiFindService;
  @Mock UserRepository userRepository;

  private GrammarBankAccessGuard guard;
  private GrammarBankController controller;

  @BeforeEach
  void setUp() {
    guard = new GrammarBankAccessGuard(userRepository);
    ReflectionTestUtils.setField(guard, "operatorLoginId", "modu");
    controller = new GrammarBankController(bankService, importService, aiFindService, guard);
  }

  private static AuthenticatedPrincipal admin() {
    return new AuthenticatedPrincipal(1L, "admin", Role.ADMIN.name());
  }

  private static AuthenticatedPrincipal tutor() {
    return new AuthenticatedPrincipal(7L, "tutor01", Role.TUTOR.name());
  }

  @Test
  @DisplayName("비로그인 검색·AI·tags·stats 요청은 401이고 서비스 계층까지 가지 않는다")
  void anonymousBlocked() {
    assertThatThrownBy(() -> controller.search(null, null, null, null, null, null, null, null, null, null, null))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(() -> controller.stats(null))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    assertThatThrownBy(() -> controller.tags(null))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    verify(bankService, never()).stats();
  }

  @Test
  @DisplayName("TUTOR·STUDENT는 403 차단되고 데이터 조회가 일어나지 않는다")
  void nonAdminForbidden() {
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> controller.search(tutor(), null, null, null, null, null, null, null, null, null, null))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(bankService, never()).search(any(), any());
  }

  @Test
  @DisplayName("ADMIN은 통과해 실제 검색·AI·문제집 서비스를 호출한다")
  void adminAllowed() {
    controller.stats(admin());
    verify(bankService).stats();
  }

  @Test
  @DisplayName("운영 계정 modu(고유 식별자 일치)는 통과한다")
  void operatorModuAllowed() {
    User modu = User.builder().loginId("modu").password("x").name("m").nickname("m").role(Role.STUDENT).build();
    ReflectionTestUtils.setField(modu, "userId", 42L);
    when(userRepository.findByLoginId("modu")).thenReturn(Optional.of(modu));
    controller.stats(new AuthenticatedPrincipal(42L, "modu", Role.STUDENT.name()));
    verify(bankService).stats();
  }
}
