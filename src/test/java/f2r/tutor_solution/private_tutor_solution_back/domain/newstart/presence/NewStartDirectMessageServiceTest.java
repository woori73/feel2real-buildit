package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.DmMessageView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartDirectMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartMemberPresence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository.NewStartDirectMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository.NewStartMemberPresenceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service.NewStartDirectMessageService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service.NewStartDmRateLimiter;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewStart 내부 쪽지 — 전송·수신거부·읽음")
class NewStartDirectMessageServiceTest {

  @Mock
  private NewStartDirectMessageRepository messageRepository;
  @Mock
  private NewStartMemberPresenceRepository presenceRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private NewStartMemberProfileService memberProfileService;
  @Mock
  private NewStartDmRateLimiter rateLimiter;

  private NewStartDirectMessageService service() {
    return new NewStartDirectMessageService(
        messageRepository, presenceRepository, userRepository, memberProfileService, rateLimiter);
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
  @DisplayName("given 정상 요청 when 전송 then 저장 + 양쪽 별명 resolve 뷰 반환")
  void givenValidRequest_whenSend_thenSavesAndReturnsView() {
    // given
    User sender = user(1L, "보내는이");
    User receiver = user(2L, "받는이");
    when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
    when(presenceRepository.findById(2L)).thenReturn(Optional.empty());
    when(rateLimiter.tryAcquire(1L)).thenReturn(true);
    when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
    when(messageRepository.save(any(NewStartDirectMessage.class)))
        .thenAnswer(inv -> {
          NewStartDirectMessage row = inv.getArgument(0);
          row.setMessageId(99L);
          return row;
        });
    when(memberProfileService.resolveDisplayNicknames(List.of(sender, receiver)))
        .thenReturn(Map.of(1L, "보내는이", 2L, "받는이"));

    // when
    DmMessageView view = service().send(principal(1L), 2L, "안녕하세요");

    // then
    assertThat(view.messageId()).isEqualTo(99L);
    assertThat(view.senderNickname()).isEqualTo("보내는이");
    assertThat(view.receiverNickname()).isEqualTo("받는이");
    assertThat(view.read()).isFalse();
    verify(messageRepository).save(any(NewStartDirectMessage.class));
  }

  @Test
  @DisplayName("given 본인 전송 when 전송 then 거부")
  void givenSelfSend_whenSend_thenRejects() {
    // given + when + then
    assertThatThrownBy(() -> service().send(principal(1L), 1L, "나에게"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 없는 수신자 when 전송 then 거부")
  void givenMissingReceiver_whenSend_thenRejects() {
    // given
    when(userRepository.findById(9L)).thenReturn(Optional.empty());

    // when + then
    assertThatThrownBy(() -> service().send(principal(1L), 9L, "안녕"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("찾을 수 없습니다");
  }

  @Test
  @DisplayName("given 수신 거부 상대 when 전송 then 거부")
  void givenBlockedReceiver_whenSend_thenRejects() {
    // given
    when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "받는이")));
    when(presenceRepository.findById(2L)).thenReturn(Optional.of(
        NewStartMemberPresence.builder().userId(2L).dmBlocked(true).build()));

    // when + then
    assertThatThrownBy(() -> service().send(principal(1L), 2L, "안녕"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("쪽지 수신을 거부");
  }

  @Test
  @DisplayName("given 도배 제한 초과 when 전송 then 429 사유 예외")
  void givenRateLimited_whenSend_thenThrowsState() {
    // given
    when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "받는이")));
    when(presenceRepository.findById(2L)).thenReturn(Optional.empty());
    when(rateLimiter.tryAcquire(1L)).thenReturn(false);

    // when + then
    assertThatThrownBy(() -> service().send(principal(1L), 2L, "안녕"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("given 받은 쪽지 when 타인이 읽음 표시 then 거부")
  void givenOthersMessage_whenMarkRead_thenRejects() {
    // given
    NewStartDirectMessage row = NewStartDirectMessage.builder()
        .messageId(5L).senderUserId(1L).receiverUserId(2L).message("hi").build();
    when(messageRepository.findById(5L)).thenReturn(Optional.of(row));

    // when + then
    assertThatThrownBy(() -> service().markRead(principal(1L), 5L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 받은 쪽지 when 수신자가 읽음 표시 then 저장")
  void givenOwnMessage_whenMarkRead_thenSaves() {
    // given
    NewStartDirectMessage row = NewStartDirectMessage.builder()
        .messageId(5L).senderUserId(1L).receiverUserId(2L).message("hi").isRead(false).build();
    when(messageRepository.findById(5L)).thenReturn(Optional.of(row));

    // when
    service().markRead(principal(2L), 5L);

    // then
    assertThat(row.getIsRead()).isTrue();
    verify(messageRepository).save(row);
  }

  @Test
  @DisplayName("given 수신함 조회 when 호출 then 최신순 뷰 반환")
  void givenInbox_whenQuery_thenReturnsViews() {
    // given
    NewStartDirectMessage row = NewStartDirectMessage.builder()
        .messageId(5L).senderUserId(1L).receiverUserId(2L).message("hi").isRead(false).build();
    when(messageRepository.findByReceiverUserIdOrderByMessageIdDesc(2L, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(row)));
    User sender = user(1L, "보내는이");
    User receiver = user(2L, "받는이");
    when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(sender, receiver));
    when(memberProfileService.resolveDisplayNicknames(List.of(sender, receiver)))
        .thenReturn(Map.of(1L, "보내는이", 2L, "받는이"));

    // when
    var inbox = service().inbox(principal(2L), null, 20);

    // then
    assertThat(inbox).hasSize(1);
    assertThat(inbox.get(0).senderNickname()).isEqualTo("보내는이");
  }
}
