package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberGrant;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberGrantRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService.GrantResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 감사회원 grant 발급 계약 — row가 없을 때만 INSERT하고, 이미 있으면 재실행해도 row가 늘지
 * 않는다(idempotent). 대상 회원이 존재하지 않는 user_id는 row를 만들지 않고 안내로 거절한다.
 */
@ExtendWith(MockitoExtension.class)
class NewStartGrantServiceTest {

  @Mock NewStartMemberGrantRepository grantRepository;
  @Mock UserRepository userRepository;
  @InjectMocks NewStartGrantService service;

  private static NewStartMemberGrant row(Long userId, String grantedBy) {
    return NewStartMemberGrant.builder()
        .userId(userId).capability(NewStartCapability.THANKS_MEMBER).grantedBy(grantedBy).build();
  }

  @Test
  void grantsNewMemberWithThanksCapabilityAndOperatorMarker() {
    when(userRepository.existsById(41L)).thenReturn(true);
    when(grantRepository.findByUserIdAndCapability(41L, NewStartCapability.THANKS_MEMBER))
        .thenReturn(Optional.empty());
    when(grantRepository.save(any(NewStartMemberGrant.class))).thenReturn(row(41L, "admin"));

    GrantResult result = service.grant(41L, null, "admin");

    assertThat(result.ok()).isTrue();
    assertThat(result.grant().userId()).isEqualTo(41L);
    assertThat(result.grant().capability()).isEqualTo(NewStartCapability.THANKS_MEMBER);
    ArgumentCaptor<NewStartMemberGrant> saved = ArgumentCaptor.forClass(NewStartMemberGrant.class);
    verify(grantRepository).save(saved.capture());
    assertThat(saved.getValue().getGrantedBy()).isEqualTo("admin");
  }

  @Test
  void reGrantingExistingMemberReturnsRowWithoutSecondInsert() {
    when(userRepository.existsById(41L)).thenReturn(true);
    when(grantRepository.findByUserIdAndCapability(41L, NewStartCapability.THANKS_MEMBER))
        .thenReturn(Optional.of(row(41L, "operator")));

    GrantResult result = service.grant(41L, NewStartCapability.THANKS_MEMBER, "operator");

    assertThat(result.ok()).isTrue();
    verify(grantRepository, never()).save(any(NewStartMemberGrant.class));
  }

  @Test
  void unknownUserIdIsRefusedWithoutRow() {
    when(userRepository.existsById(999L)).thenReturn(false);

    GrantResult result = service.grant(999L, null, "operator");

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("회원을 찾을 수 없습니다.");
    verify(grantRepository, never()).save(any(NewStartMemberGrant.class));
  }

  @Test
  void missingUserIdIsRefused() {
    assertThat(service.grant(null, null, "operator").ok()).isFalse();
    assertThat(service.grant(0L, null, "operator").ok()).isFalse();
    verify(grantRepository, never()).save(any(NewStartMemberGrant.class));
  }

  @Test
  void longOperatorMarkerIsTruncatedToColumnLimit() {
    when(userRepository.existsById(42L)).thenReturn(true);
    when(grantRepository.findByUserIdAndCapability(42L, NewStartCapability.THANKS_MEMBER))
        .thenReturn(Optional.empty());
    when(grantRepository.save(any(NewStartMemberGrant.class))).thenReturn(row(42L, "x"));

    service.grant(42L, null, "o".repeat(NewStartGrantService.MAX_GRANTED_BY_CHARS + 30));

    ArgumentCaptor<NewStartMemberGrant> saved = ArgumentCaptor.forClass(NewStartMemberGrant.class);
    verify(grantRepository).save(saved.capture());
    assertThat(saved.getValue().getGrantedBy()).hasSize(NewStartGrantService.MAX_GRANTED_BY_CHARS);
  }

  @Test
  void revokeRemovesRowAndReportsWhenNothingWasGranted() {
    when(grantRepository.deleteByUserIdAndCapability(41L, NewStartCapability.THANKS_MEMBER)).thenReturn(1L);
    assertThat(service.revoke(41L, null).ok()).isTrue();

    when(grantRepository.deleteByUserIdAndCapability(43L, NewStartCapability.THANKS_MEMBER)).thenReturn(0L);
    GrantResult missing = service.revoke(43L, null);
    assertThat(missing.ok()).isFalse();
    assertThat(missing.errorMessage()).isEqualTo("해당 회원에게 부여된 권한이 없습니다.");
  }
}
