package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.TestCleanupRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.TestCleanupResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ADMIN 전용 테스트 데이터 정리 — 검증 5조건·영향 행 수 검증·롤백")
class NewStartTestCleanupServiceTest {

  @Mock private NewStartConsultationRepository consultationRepository;
  @Mock private NewStartConsultationMessageRepository messageRepository;
  @Mock private UserRepository userRepository;

  private NewStartTestCleanupService service;

  private static final Long TEST_CONSULT_ID = 3L;
  private static final Long TEST_USER_ID = 340L;
  private static final String TEST_LOGIN_ID = "muse_pingtest_20261003";

  @BeforeEach
  void setUp() {
    service = new NewStartTestCleanupService(consultationRepository, messageRepository, userRepository);
  }

  private static NewStartConsultation consultation(long id, long ownerId) {
    NewStartConsultation c = NewStartConsultation.builder()
        .memberUserId(ownerId).status(ConsultationStatus.NEW).linkDebt(false)
        .lastMessageAt(LocalDateTime.now()).build();
    c.setConsultationId(id);
    return c;
  }

  private static User user(long id, String loginId) {
    User u = User.builder().loginId(loginId).password("x").name("t").nickname("t")
        .role(Role.STUDENT).build();
    ReflectionTestUtils.setField(u, "userId", id);
    return u;
  }

  private static NewStartConsultationMessage message(long id, long consultationId, String content) {
    NewStartConsultationMessage m = NewStartConsultationMessage.builder()
        .consultationId(consultationId).authorRole(MessageAuthorRole.MEMBER).content(content).build();
    m.setMessageId(id);
    return m;
  }

  private TestCleanupRequest validRequest() {
    return new TestCleanupRequest(TEST_CONSULT_ID, TEST_USER_ID, TEST_LOGIN_ID);
  }

  private void stubValidLookups(NewStartConsultation c, User u, List<NewStartConsultationMessage> msgs) {
    when(consultationRepository.findById(TEST_CONSULT_ID)).thenReturn(Optional.of(c));
    // verifyDeletionResult에서 한 번 더 조회하므로 두 번째는 empty
    when(userRepository.findByLoginId(TEST_LOGIN_ID))
        .thenReturn(Optional.of(u), Optional.empty());
    when(messageRepository.findByConsultationIdOrderByMessageIdAsc(TEST_CONSULT_ID))
        .thenReturn(msgs);
    when(messageRepository.deleteByConsultationId(TEST_CONSULT_ID)).thenReturn((long) msgs.size());
    when(consultationRepository.existsById(TEST_CONSULT_ID)).thenReturn(false);
  }

  @Test
  @DisplayName("5조건 모두 일치하면 지정된 상담·메시지·회원만 삭제하고 결과 반환")
  void givenValidTestData_whenCleanup_thenDeletesOnlyDesignated() {
    NewStartConsultation testConsult = consultation(TEST_CONSULT_ID, TEST_USER_ID);
    User testUser = user(TEST_USER_ID, TEST_LOGIN_ID);
    NewStartConsultationMessage testMsg =
        message(3L, TEST_CONSULT_ID, "[Muse 연동 테스트] 1:1 상담 알림 연동 테스트입니다.");
    stubValidLookups(testConsult, testUser, List.of(testMsg));

    TestCleanupResult result = service.cleanup(validRequest());

    assertThat(result.deletedConsultationId()).isEqualTo(TEST_CONSULT_ID);
    assertThat(result.deletedMessageCount()).isEqualTo(1L);
    assertThat(result.deletedUserId()).isEqualTo(TEST_USER_ID);
    assertThat(result.deletedLoginId()).isEqualTo(TEST_LOGIN_ID);

    // 지정된 엔티티 정확히 1건씩만 삭제 호출됐는지 캡처로 증명
    ArgumentCaptor<NewStartConsultation> consultCaptor =
        ArgumentCaptor.forClass(NewStartConsultation.class);
    verify(consultationRepository, times(1)).delete(consultCaptor.capture());
    assertThat(consultCaptor.getValue().getConsultationId()).isEqualTo(TEST_CONSULT_ID);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository, times(1)).delete(userCaptor.capture());
    assertThat(userCaptor.getValue().getUserId()).isEqualTo(TEST_USER_ID);

    verify(messageRepository, times(1)).deleteByConsultationId(TEST_CONSULT_ID);
  }

  @Test
  @DisplayName("userId 불일치 시 아무것도 삭제하지 않고 예외")
  void givenUserIdMismatch_whenCleanup_thenNothingDeleted() {
    NewStartConsultation testConsult = consultation(TEST_CONSULT_ID, TEST_USER_ID);
    User testUser = user(TEST_USER_ID, TEST_LOGIN_ID);
    when(consultationRepository.findById(TEST_CONSULT_ID)).thenReturn(Optional.of(testConsult));
    when(userRepository.findByLoginId(TEST_LOGIN_ID)).thenReturn(Optional.of(testUser));
    when(messageRepository.findByConsultationIdOrderByMessageIdAsc(TEST_CONSULT_ID))
        .thenReturn(List.of(message(3L, TEST_CONSULT_ID, "[Muse 연동 테스트] x")));

    assertThatThrownBy(() ->
        service.cleanup(new TestCleanupRequest(TEST_CONSULT_ID, 999L, TEST_LOGIN_ID)))
        .isInstanceOf(TestCleanupVerificationException.class);

    verify(messageRepository, never()).deleteByConsultationId(anyLong());
    verify(consultationRepository, never()).delete(any());
    verify(userRepository, never()).delete(any());
  }

  @Test
  @DisplayName("테스트 계정 prefix가 아니면 아무것도 삭제하지 않고 예외")
  void givenNonTestLoginId_whenCleanup_thenNothingDeleted() {
    NewStartConsultation testConsult = consultation(TEST_CONSULT_ID, TEST_USER_ID);
    User realUser = user(TEST_USER_ID, "realuser01");
    when(consultationRepository.findById(TEST_CONSULT_ID)).thenReturn(Optional.of(testConsult));
    when(userRepository.findByLoginId("realuser01")).thenReturn(Optional.of(realUser));
    when(messageRepository.findByConsultationIdOrderByMessageIdAsc(TEST_CONSULT_ID))
        .thenReturn(List.of(message(3L, TEST_CONSULT_ID, "[Muse 연동 테스트] x")));

    assertThatThrownBy(() ->
        service.cleanup(new TestCleanupRequest(TEST_CONSULT_ID, TEST_USER_ID, "realuser01")))
        .isInstanceOf(TestCleanupVerificationException.class)
        .hasMessageContaining("not a test account");

    verify(messageRepository, never()).deleteByConsultationId(anyLong());
    verify(consultationRepository, never()).delete(any());
    verify(userRepository, never()).delete(any());
  }

  @Test
  @DisplayName("상담 소유자 불일치 시 아무것도 삭제하지 않고 예외")
  void givenOwnerMismatch_whenCleanup_thenNothingDeleted() {
    NewStartConsultation otherConsult = consultation(TEST_CONSULT_ID, 777L);
    User testUser = user(TEST_USER_ID, TEST_LOGIN_ID);
    when(consultationRepository.findById(TEST_CONSULT_ID)).thenReturn(Optional.of(otherConsult));
    when(userRepository.findByLoginId(TEST_LOGIN_ID)).thenReturn(Optional.of(testUser));
    when(messageRepository.findByConsultationIdOrderByMessageIdAsc(TEST_CONSULT_ID))
        .thenReturn(List.of(message(3L, TEST_CONSULT_ID, "[Muse 연동 테스트] x")));

    assertThatThrownBy(() -> service.cleanup(validRequest()))
        .isInstanceOf(TestCleanupVerificationException.class)
        .hasMessageContaining("owner mismatch");

    verify(messageRepository, never()).deleteByConsultationId(anyLong());
    verify(consultationRepository, never()).delete(any());
    verify(userRepository, never()).delete(any());
  }

  @Test
  @DisplayName("최초 사용자 메시지가 테스트 표식으로 시작하지 않으면 아무것도 삭제하지 않고 예외")
  void givenNoTestMarker_whenCleanup_thenNothingDeleted() {
    NewStartConsultation testConsult = consultation(TEST_CONSULT_ID, TEST_USER_ID);
    User testUser = user(TEST_USER_ID, TEST_LOGIN_ID);
    when(consultationRepository.findById(TEST_CONSULT_ID)).thenReturn(Optional.of(testConsult));
    when(userRepository.findByLoginId(TEST_LOGIN_ID)).thenReturn(Optional.of(testUser));
    // 표식이 중간에 포함돼도 시작으로 시작하지 않으면 실패
    when(messageRepository.findByConsultationIdOrderByMessageIdAsc(TEST_CONSULT_ID))
        .thenReturn(List.of(message(3L, TEST_CONSULT_ID, "일반 상담입니다 [Muse 연동 테스트]")));

    assertThatThrownBy(() -> service.cleanup(validRequest()))
        .isInstanceOf(TestCleanupVerificationException.class)
        .hasMessageContaining("not a test consultation");

    verify(messageRepository, never()).deleteByConsultationId(anyLong());
    verify(consultationRepository, never()).delete(any());
    verify(userRepository, never()).delete(any());
  }

  @Test
  @DisplayName("메시지 삭제 행 수가 예상과 다르면 예외 (롤백)")
  void givenMessageCountMismatch_whenCleanup_thenThrows() {
    NewStartConsultation testConsult = consultation(TEST_CONSULT_ID, TEST_USER_ID);
    User testUser = user(TEST_USER_ID, TEST_LOGIN_ID);
    NewStartConsultationMessage testMsg =
        message(3L, TEST_CONSULT_ID, "[Muse 연동 테스트] 1:1 상담 알림 연동 테스트입니다.");
    when(consultationRepository.findById(TEST_CONSULT_ID)).thenReturn(Optional.of(testConsult));
    when(userRepository.findByLoginId(TEST_LOGIN_ID))
        .thenReturn(Optional.of(testUser), Optional.empty());
    when(messageRepository.findByConsultationIdOrderByMessageIdAsc(TEST_CONSULT_ID))
        .thenReturn(List.of(testMsg));
    when(messageRepository.deleteByConsultationId(TEST_CONSULT_ID)).thenReturn(0L);

    assertThatThrownBy(() -> service.cleanup(validRequest()))
        .isInstanceOf(TestCleanupVerificationException.class)
        .hasMessageContaining("message count mismatch");
  }

  @Test
  @DisplayName("상담이 이미 삭제된 뒤에도 남아있으면 예외 (롤백)")
  void givenConsultationStillExists_whenCleanup_thenThrows() {
    NewStartConsultation testConsult = consultation(TEST_CONSULT_ID, TEST_USER_ID);
    User testUser = user(TEST_USER_ID, TEST_LOGIN_ID);
    NewStartConsultationMessage testMsg =
        message(3L, TEST_CONSULT_ID, "[Muse 연동 테스트] 1:1 상담 알림 연동 테스트입니다.");
    when(consultationRepository.findById(TEST_CONSULT_ID)).thenReturn(Optional.of(testConsult));
    when(userRepository.findByLoginId(TEST_LOGIN_ID))
        .thenReturn(Optional.of(testUser), Optional.empty());
    when(messageRepository.findByConsultationIdOrderByMessageIdAsc(TEST_CONSULT_ID))
        .thenReturn(List.of(testMsg));
    when(messageRepository.deleteByConsultationId(TEST_CONSULT_ID)).thenReturn(1L);
    when(consultationRepository.existsById(TEST_CONSULT_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.cleanup(validRequest()))
        .isInstanceOf(TestCleanupVerificationException.class)
        .hasMessageContaining("consultation still exists");
  }

  @Test
  @DisplayName("다른 상담·다른 회원은 삭제 대상으로 조회·삭제되지 않음")
  void givenOtherData_whenCleanup_thenOnlyDesignatedDeleted() {
    NewStartConsultation testConsult = consultation(TEST_CONSULT_ID, TEST_USER_ID);
    NewStartConsultation realConsult = consultation(1L, 100L);
    User testUser = user(TEST_USER_ID, TEST_LOGIN_ID);
    User realUser = user(100L, "realuser01");
    NewStartConsultationMessage testMsg =
        message(3L, TEST_CONSULT_ID, "[Muse 연동 테스트] 1:1 상담 알림 연동 테스트입니다.");
    stubValidLookups(testConsult, testUser, List.of(testMsg));

    service.cleanup(validRequest());

    // 실제 상담/실제 회원은 어떤 조회·삭제 호출에도 등장하지 않음
    verify(consultationRepository, never()).findById(1L);
    verify(consultationRepository, never()).delete(realConsult);
    verify(userRepository, never()).findByLoginId("realuser01");
    verify(userRepository, never()).delete(realUser);
    verify(messageRepository, never()).deleteByConsultationId(1L);
    verify(messageRepository, never()).findByConsultationIdOrderByMessageIdAsc(1L);
  }
}
