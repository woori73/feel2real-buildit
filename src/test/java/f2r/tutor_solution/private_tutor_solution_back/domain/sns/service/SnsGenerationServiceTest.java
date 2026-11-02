package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsBrandPresetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnsGenerationServiceTest {

  SnsAutomationRuleRepository rules = mock(SnsAutomationRuleRepository.class);
  SnsBrandPresetRepository brands = mock(SnsBrandPresetRepository.class);
  SnsPostRepository posts = mock(SnsPostRepository.class);
  SnsGenerationLogRepository logs = mock(SnsGenerationLogRepository.class);
  SnsCaptionService caption = mock(SnsCaptionService.class);
  SnsProperties props = new SnsProperties();
  SnsBudgetService budget = new SnsBudgetService(props, logs);
  SnsGenerationService service = new SnsGenerationService(rules, brands, posts, logs, caption, budget, props, Runnable::run);

  SnsAutomationRule rule;
  SnsBrandPreset brand;
  List<SnsGenerationLog> savedLogs = new ArrayList<>();

  @BeforeEach
  void setUp() {
    props.setMonthlyBudgetKrw(30000);
    rule = new SnsAutomationRule();
    rule.setId(1L);
    rule.setTitle("영어");
    rule.setBrandId(2L);
    rule.setImagePrompt("세븐");
    rule.setStartDate(LocalDate.of(2026, 11, 2));
    rule.setReferenceImageId(3L);
    brand = new SnsBrandPreset();
    brand.setId(2L);
    brand.setName("F2R");
    when(rules.findById(1L)).thenReturn(Optional.of(rule));
    when(brands.findById(2L)).thenReturn(Optional.of(brand));
    when(logs.save(any())).thenAnswer(inv -> {
      savedLogs.add(inv.getArgument(0));
      return inv.getArgument(0);
    });
    when(posts.save(any())).thenAnswer(inv -> {
      SnsPost p = inv.getArgument(0);
      if (p.getId() == null) p.setId(10L);
      when(posts.findById(10L)).thenReturn(Optional.of(p));
      return p;
    });
  }

  @Test
  @DisplayName("given 상한 미설정 when startRun then 게시물을 만들지 않고 막음")
  void startRun_blockedWithoutBudget() {
    // given
    props.setMonthlyBudgetKrw(null);
    // when / then
    assertThatThrownBy(() -> service.startRun(rule, false)).isInstanceOf(SnsGenerationService.GenerationBlocked.class);
    verify(posts, never()).save(any());
  }

  @Test
  @DisplayName("given 문구 생성 성공 when startRun then 문구 완료·이미지 대기, 영어 이미지 프롬프트에 글자 금지 붙음")
  void startRun_captionThenImagePending() {
    // given
    when(caption.generate(any(), any(), any())).thenReturn(new SnsCaptionService.Generated("주제", "본문", "본문\n\n고정", "#a",
        "a man", "muse", "prompt"));
    // when
    SnsPost p = service.startRun(rule, false);
    // then
    assertThat(p.getCaptionStatus()).isEqualTo(SnsPost.PartStatus.DONE);
    assertThat(p.getImageStatus()).isEqualTo(SnsPost.PartStatus.PENDING);
    assertThat(p.getStatus()).isEqualTo(SnsPost.Status.GENERATING);
    assertThat(p.getImagePrompt()).startsWith("a man, photorealistic").contains("no text");
    assertThat(savedLogs).extracting(SnsGenerationLog::getKind).containsExactly(SnsGenerationLog.Kind.TEXT);
  }

  @Test
  @DisplayName("given 문구가 두 번 거절 when startRun then 실패 탭에 사유, 세 번째 호출 없음")
  void caption_failsTwice() {
    // given
    when(caption.generate(any(), any(), any())).thenThrow(new SnsCaptionService.CaptionRejected("금지 표현"));
    // when
    SnsPost p = service.startRun(rule, false);
    // then
    verify(caption, times(2)).generate(any(), any(), any());
    assertThat(p.getStatus()).isEqualTo(SnsPost.Status.FAILED);
    assertThat(p.getFailReason()).contains("2회").contains("금지 표현");
    assertThat(savedLogs).hasSize(2).allMatch(l -> !l.isSuccess());
  }

  @Test
  @DisplayName("given 이미지 작업 RUNNING when completeImage then 비용 기록·공개 토큰 발급·검토 대기")
  void completeImage_toReview() {
    // given
    SnsPost p = running(1);
    // when
    SnsPost done = service.completeImage(10L, "s3/key.jpg", 90_000, "comfyui");
    // then
    assertThat(done.getStatus()).isEqualTo(SnsPost.Status.REVIEW);
    assertThat(done.getMediaToken()).hasSizeGreaterThan(20);
    assertThat(done.getImagePaths()).isEqualTo("s3/key.jpg");
    assertThat(savedLogs.get(0).getCostKrw()).isEqualTo(32); // 90초 × 1,261원/시간 올림
  }

  @Test
  @DisplayName("given 첫 실패 when failImage then 다시 대기, 두 번째 실패면 실패 처리")
  void failImage_retryOnce() {
    // given
    running(1);
    // when
    SnsPost first = service.failImage(10L, "CUDA OOM", 1000);
    // then
    assertThat(first.getImageStatus()).isEqualTo(SnsPost.PartStatus.PENDING);
    // given
    first.setImageStatus(SnsPost.PartStatus.RUNNING);
    first.setImageAttempts(2);
    // when
    SnsPost second = service.failImage(10L, "CUDA OOM", 1000);
    // then
    assertThat(second.getStatus()).isEqualTo(SnsPost.Status.FAILED);
    assertThat(second.getFailReason()).contains("CUDA OOM");
  }

  @Test
  @DisplayName("given 이미 두 번 가져간 멈춘 작업 when claimNextImageJob then 내주지 않고 실패 처리")
  void claim_staleSecondAttemptFails() {
    // given
    SnsPost p = running(2);
    when(posts.imageJobCandidates(any(), any())).thenReturn(List.of(p));
    // when
    var job = service.claimNextImageJob();
    // then
    assertThat(job).isEmpty();
    assertThat(p.getStatus()).isEqualTo(SnsPost.Status.FAILED);
    verify(posts, never()).claimImageJob(anyLong(), anyInt(), any(), any());
  }

  @Test
  @DisplayName("given 대기 작업 when claimNextImageJob then 프롬프트·비율·참고 이미지와 함께 내줌")
  void claim_returnsJob() {
    // given
    SnsPost p = running(0);
    p.setImageStatus(SnsPost.PartStatus.PENDING);
    when(posts.imageJobCandidates(any(), any())).thenReturn(List.of(p));
    when(posts.claimImageJob(eq(10L), eq(0), any(), any())).thenReturn(1);
    // when
    var job = service.claimNextImageJob().orElseThrow();
    // then
    assertThat(job.postId()).isEqualTo(10L);
    assertThat(job.referenceImageId()).isEqualTo(3L);
    assertThat(job.aspectRatio()).isEqualTo("1:1");
    assertThat(job.attempt()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 검토 대기 글 when regenerateImage then 생성 중·이미지 대기로 되돌림")
  void regenerate() {
    // given
    SnsPost p = running(1);
    p.setStatus(SnsPost.Status.REVIEW);
    p.setImageStatus(SnsPost.PartStatus.DONE);
    // when
    SnsPost r = service.regenerateImage(10L);
    // then
    ArgumentCaptor<SnsPost> c = ArgumentCaptor.forClass(SnsPost.class);
    verify(posts).save(c.capture());
    assertThat(r.getStatus()).isEqualTo(SnsPost.Status.GENERATING);
    assertThat(r.getImageStatus()).isEqualTo(SnsPost.PartStatus.PENDING);
    assertThat(r.getImageAttempts()).isZero();
  }

  private SnsPost running(int attempts) {
    SnsPost p = new SnsPost();
    p.setId(10L);
    p.setRuleId(1L);
    p.setStatus(SnsPost.Status.GENERATING);
    p.setCaptionStatus(SnsPost.PartStatus.DONE);
    p.setImageStatus(SnsPost.PartStatus.RUNNING);
    p.setImageAttempts(attempts);
    p.setImagePrompt("a man");
    when(posts.findById(10L)).thenReturn(Optional.of(p));
    return p;
  }
}
