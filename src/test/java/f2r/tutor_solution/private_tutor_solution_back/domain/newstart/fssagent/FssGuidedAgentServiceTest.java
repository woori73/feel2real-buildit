package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Guided 세션 상태전이 계약 — user-confirm advance(1→…→14→finished), 되돌리기, 재시작,
 * 그리고 로그에 userId+단계 label 외 개인정보성 텍스트가 남지 않는다.
 */
@DisplayName("FSS guided session — user-confirm transitions, privacy-safe logs")
class FssGuidedAgentServiceTest {

  private FssGuidedAgentService service;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger rootLogger;

  @BeforeEach
  void setUp() {
    service = new FssGuidedAgentService();
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    logAppender = new ListAppender<>();
    logAppender.start();
    rootLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    rootLogger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  void newSessionStartsAtStepOne() {
    FssGuidedSession s = service.session(7L);
    assertThat(s.currentStep()).isEqualTo(1);
    assertThat(s.finished()).isFalse();
  }

  @Test
  void advanceWalksToFourteenThenStaysFinished() {
    for (int expected = 2; expected <= 14; expected++) {
      assertThat(service.completeCurrentStep(1L).currentStep()).isEqualTo(expected);
    }
    FssGuidedSession done = service.completeCurrentStep(1L);
    assertThat(done.currentStep()).isEqualTo(14);
    assertThat(done.finished()).isTrue();
    assertThat(service.completeCurrentStep(1L).finished()).isTrue();
    assertThat(service.completeCurrentStep(1L).currentStep()).isEqualTo(14);
  }

  @Test
  void sessionsAreIsolatedPerUser() {
    service.completeCurrentStep(10L);
    assertThat(service.session(10L).currentStep()).isEqualTo(2);
    assertThat(service.session(11L).currentStep()).isEqualTo(1);
  }

  @Test
  void backGoesOneStepAndClampsAtOne() {
    service.completeCurrentStep(20L);
    assertThat(service.previousStep(20L).currentStep()).isEqualTo(1);
    assertThat(service.previousStep(20L).currentStep()).isEqualTo(1);
  }

  @Test
  void backAfterFinishedUnfinishesAtLastStep() {
    FssGuidedSession s = service.session(30L);
    for (int i = 0; i < 14; i++) {
      s = service.completeCurrentStep(30L);
    }
    assertThat(s.finished()).isTrue();
    FssGuidedSession back = service.previousStep(30L);
    assertThat(back.finished()).isFalse();
    assertThat(back.currentStep()).isEqualTo(14);
  }

  @Test
  void restartReturnsToStepOne() {
    service.completeCurrentStep(40L);
    service.completeCurrentStep(40L);
    FssGuidedSession s = service.restart(40L);
    assertThat(s.currentStep()).isEqualTo(1);
    assertThat(s.finished()).isFalse();
  }

  @Test
  @DisplayName("로그는 userId+단계 label만 — 사용자 입력·화면 내용·인증 정보는 애초에 경로에 없다")
  void logsCarryOnlyUserIdAndStepLabel() {
    service.session(99L);
    service.completeCurrentStep(99L);
    String all = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
        .collect(java.util.stream.Collectors.joining("\n"));
    assertThat(all).contains("userId=99");
    assertThat(all).contains(FssGuidedWorkflow.step(1).title());
    assertThat(all).doesNotContain("주민").doesNotContain("비밀번호").doesNotContain("인증번호");
  }
}
