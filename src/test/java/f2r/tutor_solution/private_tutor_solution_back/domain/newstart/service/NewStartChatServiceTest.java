package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat.NewStartChatBroadcaster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat.NewStartChatMessageEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat.NewStartChatRateLimiter;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartChatMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartMemberProfile;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartChatMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartMemberProfileRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * NewStartChatService given/when/then 단위테스트. repository는 Mockito fake, broadcaster는
 * 메모리 fake — DB 없이 전송 계약·작성자 결정 원칙(principal)·삭제 권한을 검증한다.
 */
@DisplayName("NewStartChatService — 전송·히스토리·삭제 계약")
class NewStartChatServiceTest {

  private static final Instant START = Instant.parse("2026-09-19T09:00:00Z");

  private NewStartChatMessageRepository messages;
  private UserRepository users;
  private NewStartMemberProfileRepository profiles;
  private RecordingBroadcaster broadcaster;
  private final List<NewStartChatMessage> saved = new ArrayList<>();
  private final Map<Long, User> usersById = new HashMap<>();
  private final Map<Long, NewStartMemberProfile> profilesById = new HashMap<>();
  private final AtomicLong idSeq = new AtomicLong(0);

  private NewStartChatService service;

  @BeforeEach
  void setUp() {
    messages = mock(NewStartChatMessageRepository.class);
    users = mock(UserRepository.class);
    profiles = mock(NewStartMemberProfileRepository.class);
    broadcaster = new RecordingBroadcaster();

    MutableClock clock = new MutableClock(START);
    NewStartChatRateLimiter limiter = new NewStartChatRateLimiter(clock, 3, Duration.ofSeconds(15));
    NewStartMemberProfileService memberProfileService = new NewStartMemberProfileService(profiles, users);
    service = new NewStartChatService(messages, users, limiter, broadcaster, memberProfileService);

    // 기본은 아무도 NewStart 별명을 아직 설정하지 않은 상태 — 이 경우 User.getDisplayName()으로
    // 폴백한다(기존 테스트가 검증해 온 "nickname 우선 → loginId, ADMIN=관리자"와 동일한 결과).
    lenient().when(profiles.findById(anyLong())).thenAnswer(
        (Answer<Optional<NewStartMemberProfile>>) invocation ->
            Optional.ofNullable(profilesById.get(invocation.getArgument(0))));
    lenient().when(profiles.existsById(anyLong())).thenAnswer(
        (Answer<Boolean>) invocation -> profilesById.containsKey(invocation.getArgument(0)));
    lenient().when(profiles.findAllById(anyIterable())).thenAnswer((Answer<List<NewStartMemberProfile>>) invocation -> {
      List<NewStartMemberProfile> result = new ArrayList<>();
      for (Object id : (Iterable<?>) invocation.getArgument(0)) {
        NewStartMemberProfile p = profilesById.get(((Number) id).longValue());
        if (p != null) result.add(p);
      }
      return result;
    });

    // save: id를 붙여 저장한 copy를 돌려준다(테스트 상태는 saved에만).
    when(messages.save(any(NewStartChatMessage.class))).thenAnswer((Answer<NewStartChatMessage>) invocation -> {
      NewStartChatMessage input = invocation.getArgument(0);
      NewStartChatMessage copy = new NewStartChatMessage(input.getUserId(), input.getMessage());
      setMessageId(copy, idSeq.incrementAndGet());
      saved.add(copy);
      return copy;
    });
    when(messages.findById(anyLong())).thenAnswer(
        (Answer<Optional<NewStartChatMessage>>) invocation -> saved.stream()
            .filter(m -> m.getMessageId().equals(invocation.getArgument(0)))
            .findFirst());
    when(messages.findByDeletedFalseOrderByMessageIdDesc(any(Pageable.class))).thenAnswer(
        (Answer<PageImpl<NewStartChatMessage>>) invocation -> new PageImpl<>(recent(true, null)));
    when(messages.findByMessageIdLessThanAndDeletedFalseOrderByMessageIdDesc(anyLong(), any(Pageable.class)))
        .thenAnswer((Answer<PageImpl<NewStartChatMessage>>) invocation ->
            new PageImpl<>(recent(true, invocation.getArgument(0))));

    lenient().when(users.findById(anyLong())).thenAnswer(
        (Answer<Optional<User>>) invocation -> Optional.ofNullable(usersById.get(invocation.getArgument(0))));
    lenient().when(users.findAllById(anyIterable())).thenAnswer((Answer<List<User>>) invocation -> {
      List<User> result = new ArrayList<>();
      for (Object id : (Iterable<?>) invocation.getArgument(0)) {
        User u = usersById.get(((Number) id).longValue());
        if (u != null) result.add(u);
      }
      return result;
    });
  }

  private List<NewStartChatMessage> recent(boolean liveOnly, Long before) {
    return saved.stream()
        .filter(m -> !liveOnly || !m.isDeleted())
        .filter(m -> before == null || m.getMessageId() < before)
        .sorted((a, b) -> Long.compare(b.getMessageId(), a.getMessageId()))
        .toList();
  }

  private void addUser(long userId, String loginId, String nickname) {
    User user = User.builder().loginId(loginId).password("x").name("실명" + userId)
        .nickname(nickname).role(Role.STUDENT).build();
    setUserId(user, userId);
    usersById.put(userId, user);
  }

  /** NewStart 전용 별명이 이미 설정된(=seed됐거나 사용자가 직접 바꾼) 상태를 흉내낸다. */
  private void addProfile(long userId, String displayNickname) {
    profilesById.put(userId,
        NewStartMemberProfile.builder().userId(userId).displayNickname(displayNickname).build());
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal(long userId, String role) {
    return new JwtTokenProvider.AuthenticatedPrincipal(userId, "user" + userId, role);
  }

  @Test
  @DisplayName("given 정상 메시지 when send then principal userId로 저장하고 방 전체에 broadcast한다")
  void sendSavesWithPrincipalUserAndBroadcasts() {
    addUser(1L, "member1", "별명1");

    NewStartChatService.NewStartChatSendResult result =
        service.send(principal(1L, "STUDENT"), "  안녕하세요  ");

    assertThat(result.ok()).isTrue();
    assertThat(result.view().senderUserId()).isEqualTo(1L);
    assertThat(result.view().displayName()).isEqualTo("별명1"); // nickname 우선
    assertThat(result.view().message()).isEqualTo("안녕하세요"); // trim
    assertThat(broadcaster.events).hasSize(1);
    NewStartChatMessageEvent event = broadcaster.events.get(0);
    assertThat(event.type()).isEqualTo("new");
    assertThat(event.displayName()).isEqualTo("별명1");
    assertThat(event.message()).doesNotContain("실명1"); // 실명 노출 없음
  }

  @Test
  @DisplayName("given nickname 없는 회원 when send then loginId fallback 표기로 broadcast한다")
  void fallsBackToLoginIdWhenNicknameMissing() {
    addUser(2L, "fall01", "");

    NewStartChatService.NewStartChatSendResult result = service.send(principal(2L, "STUDENT"), "메시지");

    assertThat(result.ok()).isTrue();
    assertThat(result.view().displayName()).isEqualTo("fall01");
  }

  @Test
  @DisplayName("given ADMIN when send then 표기는 관리자로 내려간다")
  void adminDisplayName() {
    User admin = User.builder().loginId("boss").password("x").name("운영자").nickname("별칭").role(Role.ADMIN).build();
    setUserId(admin, 9L);
    usersById.put(9L, admin);

    NewStartChatService.NewStartChatSendResult result = service.send(principal(9L, "ADMIN"), "공지");

    assertThat(result.ok()).isTrue();
    assertThat(result.view().displayName()).isEqualTo("관리자");
  }

  @Test
  @DisplayName("given 빈/공백 메시지 when send then 저장·broadcast 없이 거부한다")
  void rejectsBlankMessage() {
    assertThat(service.send(principal(1L, "STUDENT"), "").ok()).isFalse();
    assertThat(service.send(principal(1L, "STUDENT"), "   ").ok()).isFalse();
    assertThat(service.send(principal(1L, "STUDENT"), null).ok()).isFalse();
    assertThat(saved).isEmpty();
    assertThat(broadcaster.events).isEmpty();
  }

  @Test
  @DisplayName("given 500자 초과 when send then 거부하고 500자는 허용한다")
  void rejectsTooLongMessage() {
    addUser(1L, "member1", "별명1");
    assertThat(service.send(principal(1L, "STUDENT"), "가".repeat(501)).ok()).isFalse();
    assertThat(service.send(principal(1L, "STUDENT"), "가".repeat(500)).ok()).isTrue();
  }

  @Test
  @DisplayName("given 도배 한도 초과 when send then 거부하고 저장하지 않는다")
  void rateLimitBlocksFlood() {
    addUser(1L, "member1", "별명1");
    assertThat(service.send(principal(1L, "STUDENT"), "m1").ok()).isTrue();
    assertThat(service.send(principal(1L, "STUDENT"), "m2").ok()).isTrue();
    assertThat(service.send(principal(1L, "STUDENT"), "m3").ok()).isTrue();

    NewStartChatService.NewStartChatSendResult blocked = service.send(principal(1L, "STUDENT"), "m4");

    assertThat(blocked.ok()).isFalse();
    assertThat(blocked.errorMessage()).contains("잠시");
    assertThat(saved).hasSize(3);
  }

  @Test
  @DisplayName("given 존재하지 않는 회원 when send then 거부한다")
  void rejectsUnknownUser() {
    NewStartChatService.NewStartChatSendResult result = service.send(principal(999L, "STUDENT"), "메시지");
    assertThat(result.ok()).isFalse();
    assertThat(saved).isEmpty();
  }

  @Test
  @DisplayName("given 본인 메시지 when delete then soft delete하고 broadcast한다")
  void ownerCanDeleteOwnMessage() {
    NewStartChatMessage savedMessage =
        messages.save(NewStartChatMessage.builder().userId(1L).message("내 메시지").build());

    NewStartChatService.NewStartChatDeleteResult result =
        service.delete(principal(1L, "STUDENT"), savedMessage.getMessageId());

    assertThat(result.ok()).isTrue();
    assertThat(savedMessage.isDeleted()).isTrue();
    assertThat(savedMessage.getMessage()).isEmpty();
    assertThat(broadcaster.events.get(broadcaster.events.size() - 1).type()).isEqualTo("deleted");
  }

  @Test
  @DisplayName("given 타인 메시지 when delete then 403(forbidden)으로 거부한다")
  void otherUserCannotDelete() {
    NewStartChatMessage savedMessage =
        messages.save(NewStartChatMessage.builder().userId(2L).message("남의 메시지").build());

    NewStartChatService.NewStartChatDeleteResult result =
        service.delete(principal(1L, "STUDENT"), savedMessage.getMessageId());

    assertThat(result.ok()).isFalse();
    assertThat(result.forbidden()).isTrue();
    assertThat(savedMessage.isDeleted()).isFalse();
  }

  @Test
  @DisplayName("given ADMIN when 타인 메시지 delete then 허용한다")
  void adminCanDeleteAnyMessage() {
    NewStartChatMessage savedMessage =
        messages.save(NewStartChatMessage.builder().userId(2L).message("대상 메시지").build());

    NewStartChatService.NewStartChatDeleteResult result =
        service.delete(principal(9L, "ADMIN"), savedMessage.getMessageId());

    assertThat(result.ok()).isTrue();
    assertThat(savedMessage.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("given 이미 삭제된 메시지 when delete then 거부한다")
  void doubleDeleteIsRejected() {
    NewStartChatMessage savedMessage =
        messages.save(NewStartChatMessage.builder().userId(1L).message("메시지").build());
    service.delete(principal(1L, "STUDENT"), savedMessage.getMessageId());

    NewStartChatService.NewStartChatDeleteResult second =
        service.delete(principal(1L, "STUDENT"), savedMessage.getMessageId());

    assertThat(second.ok()).isFalse();
    assertThat(second.forbidden()).isFalse();
  }

  @Test
  @DisplayName("given history 조회 when resolve then 공개 표기(nickname)만 내려준다")
  void historyResolvesDisplayNames() {
    addUser(1L, "hist01", "히토");
    messages.save(NewStartChatMessage.builder().userId(1L).message("히스토리 메시지").build());

    NewStartChatService.NewStartChatHistory history = service.history(null, null);

    assertThat(history.messages()).hasSize(1);
    assertThat(history.messages().get(0).displayName()).isEqualTo("히토");
    assertThat(history.messages().get(0).message()).doesNotContain("실명1");
    assertThat(history.hasMore()).isFalse();
  }

  @Test
  @DisplayName("given NewStart 전용 별명이 설정된 회원 when send then users.nickname이 아니라 그 별명으로 broadcast한다")
  void usesNewStartDisplayNicknameWhenSet() {
    addUser(1L, "member1", "users공용닉네임");
    addProfile(1L, "새출발러73");

    NewStartChatService.NewStartChatSendResult result = service.send(principal(1L, "STUDENT"), "안녕하세요");

    assertThat(result.view().displayName()).isEqualTo("새출발러73");
    assertThat(broadcaster.events.get(0).displayName()).isEqualTo("새출발러73");
  }

  @Test
  @DisplayName("given NewStart 전용 별명이 설정된 회원 when history 조회 then 과거 메시지도 현재 별명으로 보인다(메시지에 이름을 저장하지 않으므로 별도 마이그레이션 불필요)")
  void historyUsesCurrentNewStartNicknameForPastMessages() {
    addUser(1L, "hist01", "예전닉네임");
    messages.save(NewStartChatMessage.builder().userId(1L).message("과거 메시지").build());

    // 메시지를 보낸 "이후"에 회원이 NewStart 별명을 바꿨다고 가정 — 저장된 메시지 row에는
    // 이름이 없으므로 조회 시점에 항상 최신 별명이 반영돼야 한다.
    addProfile(1L, "바뀐새별명");

    NewStartChatService.NewStartChatHistory history = service.history(null, null);

    assertThat(history.messages().get(0).displayName()).isEqualTo("바뀐새별명");
  }

  @Test
  @DisplayName("given NAVER 프로필의 실명(name)이 nickname과 다른 회원 when send then 화면에는 절대 실명이 나오지 않는다")
  void realNameIsNeverExposedInChat() {
    User user = User.builder().loginId("naver_abc123").password("x")
        .name("홍길동") // NAVER response.name — 실제 실명일 수 있는 필드, 절대 표시하면 안 됨
        .nickname("길동이다") // NAVER response.nickname — 이것만 표시 후보
        .role(Role.STUDENT).build();
    setUserId(user, 5L);
    usersById.put(5L, user);

    NewStartChatService.NewStartChatSendResult result = service.send(principal(5L, "STUDENT"), "가입인사");

    assertThat(result.view().displayName()).isNotEqualTo("홍길동");
    assertThat(broadcaster.events.get(0).displayName()).doesNotContain("홍길동");
  }

  /* ── helpers ────────────────────────────────────────────────────────── */

  private static final class MutableClock extends Clock {
    private Instant now;
    MutableClock(Instant start) { this.now = start; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }

  private static void setMessageId(NewStartChatMessage message, Long id) {
    try {
      var field = NewStartChatMessage.class.getDeclaredField("messageId");
      field.setAccessible(true);
      field.set(message, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
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

  private static class RecordingBroadcaster extends NewStartChatBroadcaster {
    final List<NewStartChatMessageEvent> events = new ArrayList<>();
    @Override
    public void broadcast(Object payload) {
      if (payload instanceof NewStartChatMessageEvent event) events.add(event);
    }
  }
}
