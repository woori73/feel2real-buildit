package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Muse 1:1 상담 신청 감지용 공개 ping 엔드포인트 (read-only).
 *
 * <p>반환값에는 개인정보가 전혀 없다 — 최신 상담 ID, 미확인(NEW) 상담 건수, 최신 접수 시각,
 * 회원 최신 메시지 ID·시각만 반환한다. URL의 토큰 세그먼트는 남용 방지용 난독화이며, 토큰이 틀리면 404를 반환한다.
 *
 * <p>기존 상담 기능·DB 스키마에는 어떤 변경도 없다 (조회 전용 쿼리만 사용).
 * {@code latest_message_id}는 회원(MEMBER) 작성 메시지만 추적하므로 운영자 답변에는 알림이 가지 않는다.
 */
@RestController
@RequestMapping("/api/muse/newstart/consultations")
public class NewStartConsultationPingController {

  /**
   * 경로 난독화용 토큰. 회전 시 이 값과 Muse 측 Hook의 폴링 URL을 함께 교체한다.
   */
  private static final String PING_TOKEN = "f55996b037ea36a9608bd9342c11a858";

  private final NewStartConsultationRepository consultationRepository;
  private final NewStartConsultationMessageRepository messageRepository;

  public NewStartConsultationPingController(
      NewStartConsultationRepository consultationRepository,
      NewStartConsultationMessageRepository messageRepository) {
    this.consultationRepository = consultationRepository;
    this.messageRepository = messageRepository;
  }

  /**
   * 새 상담·후속 질문 여부 판별용 최소 정보만 반환한다. {@code latest_id}가 0이면 아직 상담이 없다.
   * {@code latest_message_id}는 회원 후속 질문까지 감지한다 (신규 스레드·후속 질문 모두 커버).
   */
  @GetMapping("/ping/{token}")
  public ResponseEntity<Map<String, Object>> ping(@PathVariable String token) {
    if (!PING_TOKEN.equals(token)) {
      return ResponseEntity.notFound().build();
    }
    Optional<NewStartConsultation> latest =
        consultationRepository.findTopByOrderByConsultationIdDesc();
    long pendingCount = consultationRepository.countByStatus(ConsultationStatus.NEW);
    Long latestId = latest.map(NewStartConsultation::getConsultationId).orElse(0L);
    LocalDateTime latestAt = latest.map(NewStartConsultation::getCreatedAt).orElse(null);
    Optional<NewStartConsultationMessage> latestMessage =
        messageRepository.findTopByAuthorRoleOrderByMessageIdDesc(MessageAuthorRole.MEMBER);
    Long latestMessageId = latestMessage.map(NewStartConsultationMessage::getMessageId).orElse(0L);
    LocalDateTime latestMessageAt =
        latestMessage.map(NewStartConsultationMessage::getCreatedAt).orElse(null);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("latest_id", latestId);
    body.put("pending_count", pendingCount);
    body.put("latest_at", latestAt == null ? null : latestAt.toString());
    body.put("latest_message_id", latestMessageId);
    body.put("latest_message_at", latestMessageAt == null ? null : latestMessageAt.toString());
    return ResponseEntity.ok(body);
  }
}
