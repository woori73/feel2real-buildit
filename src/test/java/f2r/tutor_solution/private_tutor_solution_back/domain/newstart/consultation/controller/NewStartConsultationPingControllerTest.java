package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewStart 1:1 상담 ping — 신규 스레드·회원 후속 질문 감지")
class NewStartConsultationPingControllerTest {

  private static final String TOKEN = "f55996b037ea36a9608bd9342c11a858";

  @Mock private NewStartConsultationRepository consultationRepository;
  @Mock private NewStartConsultationMessageRepository messageRepository;

  private NewStartConsultationPingController controller;

  @BeforeEach
  void setUp() {
    controller = new NewStartConsultationPingController(consultationRepository, messageRepository);
  }

  @Test
  @DisplayName("given 회원 후속 질문이 있으면 when ping 호출 then latest_message_id가 후속 메시지 ID를 반환한다")
  void ping_returnsLatestMemberMessageId() {
    // given: 스레드 12번에 회원 후속 질문(messageId 45)이 가장 마지막 회원 메시지
    NewStartConsultation latest =
        NewStartConsultation.builder().consultationId(12L).build();
    NewStartConsultationMessage followUp =
        NewStartConsultationMessage.builder()
            .messageId(45L)
            .consultationId(12L)
            .authorRole(MessageAuthorRole.MEMBER)
            .build();
    when(consultationRepository.findTopByOrderByConsultationIdDesc())
        .thenReturn(Optional.of(latest));
    when(consultationRepository.countByStatus(ConsultationStatus.NEW)).thenReturn(0L);
    when(messageRepository.findTopByAuthorRoleOrderByMessageIdDesc(MessageAuthorRole.MEMBER))
        .thenReturn(Optional.of(followUp));

    // when
    ResponseEntity<Map<String, Object>> response = controller.ping(TOKEN);

    // then
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("latest_id")).isEqualTo(12L);
    assertThat(body.get("latest_message_id")).isEqualTo(45L);
    assertThat(body).containsKey("latest_message_at");
  }

  @Test
  @DisplayName("given 메시지가 하나도 없으면 when ping 호출 then latest_message_id가 0이다")
  void ping_returnsZeroMessageIdWhenEmpty() {
    // given
    when(consultationRepository.findTopByOrderByConsultationIdDesc())
        .thenReturn(Optional.empty());
    when(consultationRepository.countByStatus(ConsultationStatus.NEW)).thenReturn(0L);
    when(messageRepository.findTopByAuthorRoleOrderByMessageIdDesc(MessageAuthorRole.MEMBER))
        .thenReturn(Optional.empty());

    // when
    ResponseEntity<Map<String, Object>> response = controller.ping(TOKEN);

    // then
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("latest_id")).isEqualTo(0L);
    assertThat(body.get("latest_message_id")).isEqualTo(0L);
  }

  @Test
  @DisplayName("given 토큰이 틀리면 when ping 호출 then 404를 반환한다")
  void ping_rejectsWrongToken() {
    // given + when
    ResponseEntity<Map<String, Object>> response = controller.ping("wrong-token");

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  @DisplayName("given 운영자 답변만 있으면 when ping 호출 then 회원 메시지 기준 latest_message_id는 0이다")
  void ping_ignoresOperatorMessages() {
    // given: 운영자 메시지는 MEMBER 조회에 걸리지 않음
    when(consultationRepository.findTopByOrderByConsultationIdDesc())
        .thenReturn(Optional.of(NewStartConsultation.builder().consultationId(12L).build()));
    when(consultationRepository.countByStatus(ConsultationStatus.NEW)).thenReturn(0L);
    when(messageRepository.findTopByAuthorRoleOrderByMessageIdDesc(MessageAuthorRole.MEMBER))
        .thenReturn(Optional.empty());

    // when
    Map<String, Object> body = controller.ping(TOKEN).getBody();

    // then
    assertThat(body).isNotNull();
    assertThat(body.get("latest_message_id")).isEqualTo(0L);
  }
}
