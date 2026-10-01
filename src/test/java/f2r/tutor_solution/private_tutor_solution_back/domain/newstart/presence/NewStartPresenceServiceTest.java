package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.PresenceMemberView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartMemberPresence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository.NewStartMemberPresenceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service.NewStartPresenceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewStart 접속상태 — heartbeat·온라인 window·숨김")
class NewStartPresenceServiceTest {

  @Mock
  private NewStartMemberPresenceRepository presenceRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private NewStartMemberProfileService memberProfileService;

  private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private NewStartPresenceService service() {
    return new NewStartPresenceService(presenceRepository, userRepository, memberProfileService, CLOCK);
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal(long userId) {
    return new JwtTokenProvider.AuthenticatedPrincipal(userId, "login" + userId, "USER");
  }

  private User user(long userId, String nickname) {
    User user = User.builder().loginId("login" + userId).nickname(nickname).build();
    setUserId(user, userId);
    return user;
  }

  private static void setUserId(User user, Long id) {
    try {
      var field = User.class.getDeclaredField("userId");
      field.setAccessible(true);
      field.set(user, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("given 첫 ping when 행 없음 then 행 생성 + 기본 설정 반환")
  void givenFirstPing_whenNoRow_thenCreatesRow() {
    // given
    when(presenceRepository.findById(7L)).thenReturn(Optional.empty());

    // when
    var settings = service().ping(principal(7L));

    // then
    assertThat(settings.hideOnline()).isFalse();
    assertThat(settings.dmBlocked()).isFalse();
    verify(presenceRepository).save(any(NewStartMemberPresence.class));
  }

  @Test
  @DisplayName("given 60초 이내 ping when 최근 기록 있음 then 쓰기 생략")
  void givenRecentPing_whenPingAgain_thenSkipsWrite() {
    // given
    NewStartMemberPresence row = NewStartMemberPresence.builder()
        .userId(7L).lastSeenAt(LocalDateTime.now(CLOCK).minusSeconds(10)).build();
    when(presenceRepository.findById(7L)).thenReturn(Optional.of(row));

    // when
    service().ping(principal(7L));

    // then
    verify(presenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("given 혼합 상태 when 온라인 조회 then 본인·숨김·오래된 행 제외")
  void givenMixedRows_whenOnline_thenFiltersCorrectly() {
    // given
    LocalDateTime now = LocalDateTime.now(CLOCK);
    NewStartMemberPresence fresh = NewStartMemberPresence.builder()
        .userId(2L).lastSeenAt(now.minusMinutes(1)).hideOnline(false).build();
    NewStartMemberPresence self = NewStartMemberPresence.builder()
        .userId(1L).lastSeenAt(now.minusMinutes(1)).hideOnline(false).build();
    // 숨김·오래된 행은 DB 쿼리(findByLastSeenAtAfterAndHideOnlineFalse...)가 제외하므로
    // 스텁도 쿼리 결과를 그대로 둔다. 서비스는 본인 제외 + 별명 매핑만 담당한다.
    when(presenceRepository.findByLastSeenAtAfterAndHideOnlineFalseOrderByLastSeenAtDesc(any()))
        .thenReturn(List.of(fresh, self));
    User user2 = user(2L, "둘째");
    when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(user2));
    when(memberProfileService.resolveDisplayNicknames(List.of(user2)))
        .thenReturn(Map.of(2L, "둘째"));

    // when
    List<PresenceMemberView> online = service().onlineMembers(principal(1L));

    // then
    assertThat(online).hasSize(1);
    assertThat(online.get(0).userId()).isEqualTo(2L);
    assertThat(online.get(0).displayNickname()).isEqualTo("둘째");
  }

  @Test
  @DisplayName("given 설정 변경 when 숨김+거부 then 저장 후 반영된 값 반환")
  void givenSettingsChange_whenUpdate_thenPersists() {
    // given
    when(presenceRepository.findById(7L)).thenReturn(Optional.empty());

    // when
    var settings = service().updateSettings(principal(7L), true, true);

    // then
    assertThat(settings.hideOnline()).isTrue();
    assertThat(settings.dmBlocked()).isTrue();
    verify(presenceRepository).save(any(NewStartMemberPresence.class));
  }
}
