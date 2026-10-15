package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.NewStartThanksProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

@DisplayName("NewStart 새 상담 운영자 이메일 알림 — 민감정보 최소화·미설정 시 발송 안 함")
class NewStartConsultationNotifierTest {

  private static NewStartThanksProperties props(boolean configured) {
    NewStartThanksProperties p = new NewStartThanksProperties();
    p.setAlertTo("woorinaesin@naver.com");
    if (configured) {
      p.setSmtpHost("smtp.example.invalid");
      p.setSmtpUsername("user");
      p.setSmtpPassword("secret");
    }
    return p;
  }

  @Test
  @DisplayName("메일 제목·본문은 상담번호·닉네임·접수시각·짧은 미리보기·관리 링크만 담고 전문/긴 숫자열은 싣지 않는다")
  void messageHasMinimalContent() {
    NewStartConsultationNotifier n = new NewStartConsultationNotifier(props(true));
    String longBody = "카드론 1억2천 계좌번호 110123456789 연락처 01012345678 " + "가".repeat(200) + " 비밀끝마커";

    SimpleMailMessage m = n.buildMessage(123L, "길동님", longBody, LocalDateTime.of(2026, 10, 3, 9, 5));

    assertThat(m.getTo()).containsExactly("woorinaesin@naver.com");
    assertThat(m.getSubject()).isEqualTo("[NewStart] 새로운 1:1 비공개 상담 #123");
    assertThat(m.getText())
        .contains("상담번호: 123").contains("회원: 길동님").contains("2026-10-03 09:05")
        .contains("NewStart 관리자 상담함에서 확인하십시오.")
        .contains("/admin/newstart/consultations")
        .doesNotContain("110123456789").doesNotContain("01012345678")
        .doesNotContain("비밀끝마커");
    // 미리보기는 최대 30자 + 말줄임
    String preview = m.getText().lines().filter(l -> l.startsWith("- 미리보기: ")).findFirst().orElseThrow();
    assertThat(preview.length()).isLessThanOrEqualTo("- 미리보기: ".length() + 31);
  }

  @Test
  @DisplayName("닉네임의 제어문자(헤더 인젝션 시도)는 공백으로 바뀌고, 닉네임이 없으면 '회원'")
  void sanitizesNickname() {
    NewStartConsultationNotifier n = new NewStartConsultationNotifier(props(true));

    SimpleMailMessage m = n.buildMessage(1L, "나쁜\r\nBcc: x@y.z", "내용", null);
    assertThat(m.getText()).doesNotContain("\r").contains("회원: 나쁜 Bcc: x@y.z");

    SimpleMailMessage anon = n.buildMessage(2L, null, "내용", null);
    assertThat(anon.getText()).contains("회원: 회원");
  }

  @Test
  @DisplayName("SMTP 미설정이면 발송하지 않고 false(예외 없음)")
  void notConfiguredDoesNotSend() {
    NewStartConsultationNotifier n = new NewStartConsultationNotifier(props(false));

    boolean sent = n.deliver(1L, n.buildMessage(1L, "닉", "내용", null));

    assertThat(sent).isFalse();
  }

  @Test
  @DisplayName("SMTP 접속 실패는 예외로 새지 않고 false")
  void sendFailureIsSwallowed() {
    NewStartThanksProperties p = props(true);
    p.setSmtpPort(1); // 접속 불가 포트 — 연결 거부/타임아웃
    NewStartConsultationNotifier n = new NewStartConsultationNotifier(p);

    boolean sent = n.deliver(1L, n.buildMessage(1L, "닉", "내용", null));

    assertThat(sent).isFalse();
  }
}
