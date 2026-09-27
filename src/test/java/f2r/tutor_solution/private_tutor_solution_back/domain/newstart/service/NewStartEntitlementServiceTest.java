package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * THANKS_MEMBER 판정 단일 지점 계약. DB row 유무만으로 갈리고(역할 목록 하드코딩 없음),
 * ADMIN만 row 없이 통과하며, 미로그인 principal은 어떤 capability도 갖지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class NewStartEntitlementServiceTest {

  @Mock NewStartMemberGrantRepository grantRepository;
  @InjectMocks NewStartEntitlementService service;

  private static AuthenticatedPrincipal student() {
    return new AuthenticatedPrincipal(1L, "student", "STUDENT");
  }

  private static AuthenticatedPrincipal admin() {
    return new AuthenticatedPrincipal(9L, "admin", "ADMIN");
  }

  @Test
  void grantsComeFromDbRowOnly() {
    when(grantRepository.existsByUserIdAndCapability(1L, NewStartCapability.THANKS_MEMBER)).thenReturn(true);

    assertThat(service.isThanksMember(student())).isTrue();
    service.requireThanksMember(student());
  }

  @Test
  void memberWithoutRowIsDeniedWithUserMessage() {
    when(grantRepository.existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class))).thenReturn(false);

    assertThat(service.isThanksMember(student())).isFalse();
    assertThatThrownBy(() -> service.requireThanksMember(student()))
        .isInstanceOf(NewStartEntitlementDeniedException.class)
        .hasMessage(NewStartEntitlementService.DENIAL_MESSAGE);
  }

  @Test
  void adminPassesWithoutLookingUpGrantRows() {
    assertThat(service.isThanksMember(admin())).isTrue();
    service.requireThanksMember(admin());

    verify(grantRepository, never()).existsByUserIdAndCapability(anyLong(), any(NewStartCapability.class));
  }

  @Test
  void anonymousPrincipalHasNoCapability() {
    assertThat(service.allows(null, NewStartCapability.THANKS_MEMBER)).isFalse();
    assertThatThrownBy(() -> service.require(null, NewStartCapability.THANKS_MEMBER))
        .isInstanceOf(NewStartEntitlementDeniedException.class);
  }
}
