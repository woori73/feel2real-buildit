package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartThanksProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksVerification;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksVerificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ThanksExceptionNotifier — 예외만 알림, 개인정보 최소화, SMTP 미설정이어도 흐름 유지")
class ThanksExceptionNotifierTest {

  private static NewStartThanksVerification exception(String nickname, String reason) {
    NewStartThanksVerification v = NewStartThanksVerification.builder().userId(7L).rosterId(11L).nicknameInput(nickname)
        .providerUserId("naver-subject-SECRET").outcome(NewStartThanksVerification.EXCEPTION).exceptionReason(reason).build();
    ReflectionTestUtils.setField(v, "verificationId", 100L);
    return v;
  }

  private static NewStartThanksProperties configured() {
    NewStartThanksProperties p = new NewStartThanksProperties();
    p.setAlertTo("operator@example.com");
    p.setSmtpHost("smtp.example.com");
    p.setSmtpUsername("sender@example.com");
    p.setSmtpPassword("pw-should-never-appear");
    p.setAdminUrl("https://feel2real.co.kr/admin/newstart/thanks-exceptions");
    return p;
  }

  @Test
  @DisplayName("메일 본문은 닉네임·사유·발생시각·관리자 링크만 담고 이메일 주소·OAuth 식별값·비밀값은 담지 않는다")
  void message_containsOnlyMinimalFields() {
    NewStartThanksVerificationRepository repo = mock(NewStartThanksVerificationRepository.class);
    ThanksExceptionNotifier notifier = new ThanksExceptionNotifier(configured(), repo);

    SimpleMailMessage m = notifier.buildMessage(exception("카페닉", ThanksExceptionReason.EMAIL_MISSING));

    assertThat(m.getTo()).containsExactly("operator@example.com");
    assertThat(m.getSubject()).contains("감사회원 인증 예외").contains("OAuth 이메일 없음");
    assertThat(m.getText())
        .contains("카페닉").contains("OAuth 이메일 없음").contains("발생시각").contains("KST")
        .contains("https://feel2real.co.kr/admin/newstart/thanks-exceptions")
        .doesNotContain("naver-subject-SECRET").doesNotContain("pw-should-never-appear").doesNotContain("@naver.com");
  }

  @Test
  @DisplayName("입력 닉네임의 줄바꿈·제어문자는 제거되고 길이가 제한된다(메일 구조 주입 방지)")
  void nickname_isSanitized() {
    String evil = "카페닉\r\nBcc: attacker@x.com\n" + "a".repeat(200);
    String safe = ThanksExceptionNotifier.safeNickname(evil);
    assertThat(safe).doesNotContain("\n").doesNotContain("\r").hasSizeLessThanOrEqualTo(50);
  }

  @Test
  @DisplayName("SMTP 미설정이면 발송을 건너뛰고 NOT_CONFIGURED만 기록한다(예외함 적재는 이미 끝남, 흐름 영향 없음)")
  void notConfigured_marksStatusAndDoesNotThrow() {
    NewStartThanksVerificationRepository repo = mock(NewStartThanksVerificationRepository.class);
    NewStartThanksVerification v = exception("카페닉", ThanksExceptionReason.EMAIL_NOT_NAVER);
    when(repo.findById(100L)).thenReturn(Optional.of(v));
    NewStartThanksProperties unconfigured = new NewStartThanksProperties();   // 기본값: SMTP 비어 있음

    String status = new ThanksExceptionNotifier(unconfigured, repo).notifyNow(100L);

    assertThat(status).isEqualTo(NewStartThanksVerification.EMAIL_NOT_CONFIGURED);
    assertThat(v.getEmailStatus()).isEqualTo(NewStartThanksVerification.EMAIL_NOT_CONFIGURED);
    verify(repo).save(v);
  }

  @Test
  @DisplayName("SMTP 발송 실패는 FAILED로 기록되고 예외를 밖으로 던지지 않는다")
  void sendFailure_isRecordedNotThrown() {
    NewStartThanksVerificationRepository repo = mock(NewStartThanksVerificationRepository.class);
    NewStartThanksVerification v = exception("카페닉", ThanksExceptionReason.REPEATED_FAILURE);
    when(repo.findById(100L)).thenReturn(Optional.of(v));
    NewStartThanksProperties p = configured();
    p.setSmtpHost("127.0.0.1");
    p.setSmtpPort(1);                              // 닫힌 포트 — 연결 거부

    String status = new ThanksExceptionNotifier(p, repo).notifyNow(100L);

    assertThat(status).isEqualTo(NewStartThanksVerification.EMAIL_FAILED);
    verify(repo).save(v);
  }

  @Test
  @DisplayName("존재하지 않는 예외 id는 무시한다(저장·발송 없음)")
  void unknownId_isIgnored() {
    NewStartThanksVerificationRepository repo = mock(NewStartThanksVerificationRepository.class);
    when(repo.findById(any())).thenReturn(Optional.empty());
    assertThat(new ThanksExceptionNotifier(configured(), repo).notifyNow(1L)).isNull();
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("사유 코드는 운영자용 한글 라벨을 가진다")
  void reasonLabels() {
    for (String code : new String[] {ThanksExceptionReason.EMAIL_MISSING, ThanksExceptionReason.EMAIL_NOT_NAVER,
        ThanksExceptionReason.AMBIGUOUS_CANDIDATES,
        ThanksExceptionReason.ROSTER_ALREADY_LINKED, ThanksExceptionReason.DUPLICATE_OAUTH_LINK,
        ThanksExceptionReason.REPEATED_FAILURE, ThanksExceptionReason.CONCURRENT_CONFLICT}) {
      assertThat(ThanksExceptionReason.label(code)).isNotEqualTo("기타");
    }
  }
}
