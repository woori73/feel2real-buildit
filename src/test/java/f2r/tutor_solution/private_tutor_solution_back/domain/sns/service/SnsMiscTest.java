package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class SnsMiscTest {

  @Test
  @DisplayName("given PNG·빈 값 when requireJpeg then 거절, 진짜 JPEG는 통과")
  void requireJpeg() throws Exception {
    // given
    BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream jpg = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", jpg);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(img, "png", png);
    // when / then
    SnsMediaService.requireJpeg(jpg.toByteArray());
    assertThatThrownBy(() -> SnsMediaService.requireJpeg(png.toByteArray())).hasMessageContaining("JPEG");
    assertThatThrownBy(() -> SnsMediaService.requireJpeg(new byte[0])).hasMessageContaining("빈");
  }

  @Test
  @DisplayName("given 19:05 켜진 규칙 when runDueRules then 하루 실행권을 가져간 경우에만 실행")
  void scheduler_runsOnlyWhenClaimed() {
    // given
    SnsProperties props = new SnsProperties();
    SnsAutomationRuleRepository rules = mock(SnsAutomationRuleRepository.class);
    SnsGenerationService gen = mock(SnsGenerationService.class);
    TransactionTemplate tx = mock(TransactionTemplate.class);
    when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
    SnsAutomationRule a = rule(1L);
    SnsAutomationRule b = rule(2L);
    when(rules.findByEnabledTrue()).thenReturn(List.of(a, b));
    LocalDateTime now = LocalDateTime.of(2026, 11, 3, 19, 5);
    when(rules.claimRun(1L, now.toLocalDate())).thenReturn(1);
    when(rules.claimRun(2L, now.toLocalDate())).thenReturn(0);
    SnsScheduler scheduler = new SnsScheduler(props, rules, gen, mock(SnsPublishService.class), tx);
    // when
    int ran = scheduler.runDueRules(now);
    // then
    assertThat(ran).isEqualTo(1);
    verify(gen).startRun(eq(a), eq(false));
    verify(gen, never()).startRun(eq(b), eq(false));
  }

  @Test
  @DisplayName("given 꺼진 스케줄러 when everyMinute then 아무것도 안 함")
  void scheduler_disabled() {
    // given
    SnsAutomationRuleRepository rules = mock(SnsAutomationRuleRepository.class);
    SnsScheduler scheduler = new SnsScheduler(new SnsProperties(), rules, mock(SnsGenerationService.class),
        mock(SnsPublishService.class), mock(TransactionTemplate.class));
    // when
    scheduler.everyMinute();
    // then
    verify(rules, never()).findByEnabledTrue();
  }

  static SnsAutomationRule rule(Long id) {
    SnsAutomationRule r = new SnsAutomationRule();
    r.setId(id);
    r.setEnabled(true);
    r.setStartDate(LocalDate.of(2026, 11, 2));
    r.setRunTime(LocalTime.of(19, 0));
    return r;
  }
}
