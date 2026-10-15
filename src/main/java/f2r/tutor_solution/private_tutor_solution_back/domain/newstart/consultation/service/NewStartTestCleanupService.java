package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.TestCleanupRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.TestCleanupResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN 전용 테스트 데이터 정리 (임시 endpoint — 정리 완료 후 제거 예정).
 *
 * <p>삭제 전 5가지 조건을 서버에서 재검증하고, 하나라도 불일치하면 {@link TestCleanupVerificationException}을
 * 던져 트랜잭션 전체를 롤백한다. 삭제 후에는 영향 행 수를 검증한다. DB 스키마·sequence 변경 없음.
 */
@Service
public class NewStartTestCleanupService {

  /** 테스트 계정 판별 prefix. */
  static final String TEST_LOGIN_PREFIX = "muse_pingtest_";
  /** 테스트 상담 판별 표식 — 상담 최초 사용자 메시지가 이 표식으로 시작해야 한다. */
  static final String TEST_CONTENT_MARKER = "[Muse 연동 테스트]";

  private final NewStartConsultationRepository consultationRepository;
  private final NewStartConsultationMessageRepository messageRepository;
  private final UserRepository userRepository;

  public NewStartTestCleanupService(
      NewStartConsultationRepository consultationRepository,
      NewStartConsultationMessageRepository messageRepository,
      UserRepository userRepository) {
    this.consultationRepository = consultationRepository;
    this.messageRepository = messageRepository;
    this.userRepository = userRepository;
  }

  /**
   * 지정된 테스트 상담·테스트 회원을 삭제한다. 순서: 상담 메시지 → 상담 → 회원.
   * 검증 실패 또는 영향 행 수 불일치 시 전체 롤백 (아무것도 삭제되지 않음).
   */
  @Transactional
  public TestCleanupResult cleanup(TestCleanupRequest request) {
    NewStartConsultation consultation = consultationRepository.findById(request.consultationId())
        .orElseThrow(() -> new TestCleanupVerificationException(
            "verification failed: consultation not found"));
    User user = userRepository.findByLoginId(request.testLoginId())
        .orElseThrow(() -> new TestCleanupVerificationException(
            "verification failed: test member not found"));
    List<NewStartConsultationMessage> messages =
        messageRepository.findByConsultationIdOrderByMessageIdAsc(consultation.getConsultationId());

    verifyTestTargets(consultation, user, messages, request);

    long deletedMessages = messageRepository.deleteByConsultationId(consultation.getConsultationId());
    consultationRepository.delete(consultation);
    userRepository.delete(user);
    consultationRepository.flush();

    verifyDeletionResult(consultation, user, messages.size(), deletedMessages);

    return new TestCleanupResult(
        consultation.getConsultationId(), deletedMessages, user.getUserId(), user.getLoginId());
  }

  /**
   * 5가지 조건 재검증. 하나라도 불일치하면 예외 → 호출자 트랜잭션 전체 롤백.
   * 단위 테스트를 위해 package-private.
   */
  void verifyTestTargets(NewStartConsultation consultation, User user,
      List<NewStartConsultationMessage> messages, TestCleanupRequest request) {
    if (!Objects.equals(request.consultationId(), consultation.getConsultationId())) {
      throw new TestCleanupVerificationException("verification failed: consultationId mismatch");
    }
    if (!Objects.equals(request.userId(), user.getUserId())) {
      throw new TestCleanupVerificationException("verification failed: userId mismatch");
    }
    if (!Objects.equals(request.testLoginId(), user.getLoginId())) {
      throw new TestCleanupVerificationException("verification failed: testLoginId mismatch");
    }
    if (user.getLoginId() == null || !user.getLoginId().startsWith(TEST_LOGIN_PREFIX)) {
      throw new TestCleanupVerificationException("verification failed: not a test account");
    }
    if (!Objects.equals(consultation.getMemberUserId(), user.getUserId())) {
      throw new TestCleanupVerificationException("verification failed: consultation owner mismatch");
    }
    String firstUserContent = messages.stream()
        .filter(m -> m.getAuthorRole() == MessageAuthorRole.MEMBER)
        .map(NewStartConsultationMessage::getContent)
        .findFirst()
        .orElse("");
    if (!firstUserContent.startsWith(TEST_CONTENT_MARKER)) {
      throw new TestCleanupVerificationException("verification failed: not a test consultation");
    }
  }

  /**
   * 삭제 영향 행 수 검증. 지정된 상담 1건·회원 1건이 정확히 삭제되지 않으면 예외 → 전체 롤백.
   * 단위 테스트를 위해 package-private.
   */
  void verifyDeletionResult(NewStartConsultation consultation, User user,
      int expectedMessageCount, long deletedMessages) {
    if (deletedMessages != expectedMessageCount) {
      throw new TestCleanupVerificationException("deletion failed: message count mismatch");
    }
    if (consultationRepository.existsById(consultation.getConsultationId())) {
      throw new TestCleanupVerificationException("deletion failed: consultation still exists");
    }
    if (userRepository.findByLoginId(user.getLoginId()).isPresent()) {
      throw new TestCleanupVerificationException("deletion failed: test member still exists");
    }
  }
}
