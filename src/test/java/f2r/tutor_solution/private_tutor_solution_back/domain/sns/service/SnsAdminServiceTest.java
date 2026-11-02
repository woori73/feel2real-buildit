package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.dto.SnsDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsReferenceImage;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsBrandPresetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsReferenceImageRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnsAdminServiceTest {

  SnsAccountRepository accounts = mock(SnsAccountRepository.class);
  SnsBrandPresetRepository brands = mock(SnsBrandPresetRepository.class);
  SnsReferenceImageRepository refs = mock(SnsReferenceImageRepository.class);
  SnsAutomationRuleRepository rules = mock(SnsAutomationRuleRepository.class);
  SnsPostRepository posts = mock(SnsPostRepository.class);
  SnsGenerationLogRepository logs = mock(SnsGenerationLogRepository.class);
  SnsProperties props = new SnsProperties();
  SnsAdminService service = new SnsAdminService(accounts, brands, refs, rules, posts, logs, new SnsBudgetService(props, logs),
      mock(SnsCaptionService.class), new SnsTokenCipher(props), props);

  @BeforeEach
  void setUp() {
    SnsBrandPreset b = new SnsBrandPreset();
    b.setId(1L);
    b.setName("F2R");
    when(brands.findById(1L)).thenReturn(Optional.of(b));
    when(rules.save(any())).thenAnswer(i -> i.getArgument(0));
    when(posts.save(any())).thenAnswer(i -> i.getArgument(0));
    when(rules.findAll()).thenReturn(List.of());
  }

  static SnsDtos.RuleRequest req(String saveMode, String tags, Long refId) {
    return new SnsDtos.RuleRequest("규칙", null, 1L, refId, "사진", "PHOTO", "1:1", tags, true, "DAILY", null,
        LocalTime.of(19, 0), null, saveMode, null);
  }

  @Test
  @DisplayName("given 자동 발행 when saveRule then 1차에서는 막음")
  void saveRule_rejectsAutoPublish() {
    // given / when / then
    assertThatThrownBy(() -> service.saveRule(null, req("PUBLISH", null, null))).hasMessageContaining("자동 발행");
  }

  @Test
  @DisplayName("given 고정 해시태그 6개 when saveRule then 거절, 5개면 저장")
  void saveRule_hashtagLimit() {
    // given / when / then
    assertThatThrownBy(() -> service.saveRule(null, req("LIST", "#a #b #c #d #e #f", null))).hasMessageContaining("최대 5개");
    assertThat(service.saveRule(null, req("LIST", "#a #b #c #d #e", null)).fixedHashtags()).isEqualTo("#a #b #c #d #e");
  }

  @Test
  @DisplayName("given 미승인 참고 이미지 when saveRule then 거절")
  void saveRule_requiresApprovedReference() {
    // given
    SnsReferenceImage r = new SnsReferenceImage();
    r.setApproved(false);
    when(refs.findById(9L)).thenReturn(Optional.of(r));
    // when / then
    assertThatThrownBy(() -> service.saveRule(null, req("LIST", null, 9L))).hasMessageContaining("승인");
  }

  @Test
  @DisplayName("given 규칙이 쓰는 브랜드 when deleteBrand then 참조 차단")
  void deleteBrand_blockedWhenUsed() {
    // given
    when(rules.countByBrandId(1L)).thenReturn(1L);
    // when / then
    assertThatThrownBy(() -> service.deleteBrand(1L)).hasMessageContaining("규칙");
    verify(brands, never()).delete(any());
  }

  @Test
  @DisplayName("given 발행 실패 글 when editPost then 해시태그 정리·다시 검토 대기")
  void editPost_failedBackToReview() {
    // given
    SnsPost p = new SnsPost();
    p.setId(3L);
    p.setRuleId(1L);
    p.setStatus(SnsPost.Status.FAILED);
    p.setCaptionStatus(SnsPost.PartStatus.DONE);
    p.setImageStatus(SnsPost.PartStatus.DONE);
    p.setFailReason("발행 실패");
    when(posts.findById(3L)).thenReturn(Optional.of(p));
    // when
    SnsDtos.PostView v = service.editPost(3L, new SnsDtos.PostEditRequest(" 새 문구 ", "영어 #영어 #내신"));
    // then
    assertThat(v.status()).isEqualTo("REVIEW");
    assertThat(v.caption()).isEqualTo("새 문구");
    assertThat(v.hashtags()).isEqualTo("#영어 #내신");
  }

  @Test
  @DisplayName("given 발행된 글 when discard then 거절(인스타그램에서 직접 삭제)")
  void discard_rejectsPublished() {
    // given
    SnsPost p = new SnsPost();
    p.setId(4L);
    p.setStatus(SnsPost.Status.PUBLISHED);
    when(posts.findById(4L)).thenReturn(Optional.of(p));
    // when / then
    assertThatThrownBy(() -> service.discard(4L)).hasMessageContaining("이미 발행");
  }

  @Test
  @DisplayName("given 상한·작업자·키·Meta 미설정 when summary then 알림 4개")
  void summary_alerts() {
    // given
    props.setMonthlyBudgetKrw(0);
    when(accounts.findAll()).thenReturn(List.of());
    // when
    SnsDtos.Summary s = service.summary();
    // then
    assertThat(s.alerts()).hasSize(4);
    assertThat(s.budgetConfigured()).isFalse();
  }
}
