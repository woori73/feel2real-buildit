package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberProfile;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberProfileRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * NewStartMemberProfileService given/when/then 단위테스트 — 표시 우선순위(NewStart 별명 →
 * NAVER 프로필 nickname seed → User.getDisplayName() 최종 폴백), 별명 검증(공백/길이/
 * 위험문자), unique 제약 없음(§3)을 검증한다. 실명(User.name)은 어떤 경로로도 읽지 않는다.
 */
@DisplayName("NewStartMemberProfileService — 별명 resolve/seed/update 계약")
class NewStartMemberProfileServiceTest {

  private NewStartMemberProfileRepository profiles;
  private UserRepository users;
  private NewStartMemberProfileService service;

  @BeforeEach
  void setUp() {
    profiles = mock(NewStartMemberProfileRepository.class);
    users = mock(UserRepository.class);
    service = new NewStartMemberProfileService(profiles, users);
  }

  private static User user(long userId, String loginId, String name, String nickname, Role role) {
    User u = User.builder().loginId(loginId).password("x").name(name).nickname(nickname).role(role).build();
    org.springframework.test.util.ReflectionTestUtils.setField(u, "userId", userId);
    return u;
  }

  @Test
  @DisplayName("given NewStart 별명 row가 있음 when resolve then users.nickname/실명이 아니라 그 별명을 반환한다")
  void resolvesFromNewStartProfileWhenPresent() {
    User user = user(1L, "member1", "홍길동", "공용닉네임", Role.STUDENT);
    when(profiles.findById(1L)).thenReturn(
        Optional.of(NewStartMemberProfile.builder().userId(1L).displayNickname("새출발러73").build()));

    String result = service.resolveDisplayNickname(user);

    assertThat(result).isEqualTo("새출발러73");
  }

  @Test
  @DisplayName("given NewStart 별명 row가 없음 when resolve then User.getDisplayName()으로 폴백한다(실명 아님)")
  void fallsBackToUserDisplayNameWhenProfileMissing() {
    User user = user(2L, "member2", "김철수", "공용닉네임", Role.STUDENT);
    when(profiles.findById(2L)).thenReturn(Optional.empty());

    String result = service.resolveDisplayNickname(user);

    assertThat(result).isEqualTo("공용닉네임");
    assertThat(result).isNotEqualTo("김철수");
  }

  @Test
  @DisplayName("given 여러 회원 중 일부만 NewStart 별명이 있음 when 배치 resolve then 각자 올바른 값을 반환한다(N+1 없이)")
  void batchResolveMixesProfileAndFallback() {
    User withProfile = user(1L, "a", "실명A", "닉A", Role.STUDENT);
    User withoutProfile = user(2L, "b", "실명B", "닉B", Role.STUDENT);
    when(profiles.findAllById(any())).thenReturn(
        List.of(NewStartMemberProfile.builder().userId(1L).displayNickname("전용닉1").build()));

    var result = service.resolveDisplayNicknames(List.of(withProfile, withoutProfile));

    assertThat(result.get(1L)).isEqualTo("전용닉1");
    assertThat(result.get(2L)).isEqualTo("닉B"); // 폴백은 users.nickname, 실명 아님
  }

  @Test
  @DisplayName("given 이미 seed된(또는 사용자가 설정한) row가 있음 when ensureSeeded then 아무것도 하지 않는다(덮어쓰기 없음)")
  void ensureSeededIsNoOpWhenAlreadyPresent() {
    when(profiles.existsById(1L)).thenReturn(true);

    service.ensureSeeded(1L, "새로운NAVER닉네임");

    verify(profiles, never()).save(any());
  }

  @Test
  @DisplayName("given row가 없고 NAVER nickname이 유효함 when ensureSeeded then 그 nickname으로 최초 1회 생성한다")
  void ensureSeededUsesNaverNicknameWhenValid() {
    when(profiles.existsById(1L)).thenReturn(false);

    service.ensureSeeded(1L, "새출발러73");

    ArgumentCaptor<NewStartMemberProfile> captor = ArgumentCaptor.forClass(NewStartMemberProfile.class);
    verify(profiles).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getDisplayNickname()).isEqualTo("새출발러73");
  }

  @Test
  @DisplayName("given row가 없고 NAVER nickname이 없거나 위험한 문자를 포함함 when ensureSeeded then 실명을 쓰지 않고 안전한 자동 생성 별명으로 생성한다")
  void ensureSeededFallsBackToSafeGeneratedNicknameWhenNaverNicknameUnusable() {
    when(profiles.existsById(1L)).thenReturn(false);
    when(profiles.existsById(2L)).thenReturn(false);

    service.ensureSeeded(1L, null);
    service.ensureSeeded(2L, "<script>alert(1)</script>");

    ArgumentCaptor<NewStartMemberProfile> captor = ArgumentCaptor.forClass(NewStartMemberProfile.class);
    verify(profiles, org.mockito.Mockito.times(2)).save(captor.capture());
    captor.getAllValues().forEach(saved -> {
      assertThat(saved.getDisplayNickname()).doesNotContain("<");
      assertThat(saved.getDisplayNickname()).doesNotContain("script");
      assertThat(saved.getDisplayNickname()).isNotBlank();
    });
  }

  @Test
  @DisplayName("given 유효한 별명 when updateNickname then 앞뒤 공백을 정리해 저장하고 그 값을 반환한다")
  void updateNicknameTrimsAndSaves() {
    when(profiles.findById(1L)).thenReturn(Optional.empty());

    String result = service.updateNickname(1L, "  새출발러73  ");

    assertThat(result).isEqualTo("새출발러73");
    ArgumentCaptor<NewStartMemberProfile> captor = ArgumentCaptor.forClass(NewStartMemberProfile.class);
    verify(profiles).save(captor.capture());
    assertThat(captor.getValue().getDisplayNickname()).isEqualTo("새출발러73");
  }

  @Test
  @DisplayName("given 기존 row가 있음 when updateNickname then 같은 row를 갱신한다(새 row를 또 만들지 않음)")
  void updateNicknameUpdatesExistingRowInPlace() {
    NewStartMemberProfile existing =
        NewStartMemberProfile.builder().userId(1L).displayNickname("예전별명").build();
    when(profiles.findById(1L)).thenReturn(Optional.of(existing));

    service.updateNickname(1L, "새별명");

    assertThat(existing.getDisplayNickname()).isEqualTo("새별명");
    verify(profiles).save(existing);
  }

  @Test
  @DisplayName("given 공백만 있는 별명 when updateNickname then 거부한다")
  void rejectsBlankNickname() {
    assertThatThrownBy(() -> service.updateNickname(1L, "   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateNickname(1L, null))
        .isInstanceOf(IllegalArgumentException.class);
    verify(profiles, never()).save(any());
  }

  @Test
  @DisplayName("given 20자를 초과하는 별명 when updateNickname then 거부한다")
  void rejectsTooLongNickname() {
    assertThatThrownBy(() -> service.updateNickname(1L, "가".repeat(21)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given HTML/script 태그가 섞인 별명 when updateNickname then 거부한다(화이트리스트 문자만 허용)")
  void rejectsDangerousCharacters() {
    assertThatThrownBy(() -> service.updateNickname(1L, "<script>alert(1)</script>"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateNickname(1L, "닉네임<img src=x>"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(profiles, never()).save(any());
  }

  @Test
  @DisplayName("given 한글/영문/숫자/공백/_/- 조합 별명 when updateNickname then 허용한다")
  void acceptsWhitelistedCharacters() {
    when(profiles.findById(anyLong())).thenReturn(Optional.empty());

    assertThat(service.updateNickname(1L, "새출발러_73")).isEqualTo("새출발러_73");
    assertThat(service.updateNickname(2L, "New-Start 73")).isEqualTo("New-Start 73");
  }

  @Test
  @DisplayName("여러 회원이 같은 별명을 저장해도 전부 성공한다(별명은 식별 key가 아니므로 unique 제약 없음, §3)")
  void allowsDuplicateNicknamesAcrossUsers() {
    when(profiles.findById(anyLong())).thenReturn(Optional.empty());

    assertThat(service.updateNickname(1L, "인기별명")).isEqualTo("인기별명");
    assertThat(service.updateNickname(2L, "인기별명")).isEqualTo("인기별명");
    verify(profiles, org.mockito.Mockito.times(2)).save(any());
  }
}
