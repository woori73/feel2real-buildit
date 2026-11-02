package f2r.tutor_solution.private_tutor_solution_back.domain.sns;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsBrandPresetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/** 인스타 자동 피드 엔티티 매핑과 원자적 가져가기 쿼리(임베디드 H2, MySQL 모드). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:snsverify;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class SnsRepositoryJpaTest {

  @Autowired SnsBrandPresetRepository brands;
  @Autowired SnsAutomationRuleRepository rules;
  @Autowired SnsPostRepository posts;
  @Autowired SnsGenerationLogRepository logs;

  SnsAutomationRule rule;

  @BeforeEach
  void setUp() {
    SnsBrandPreset b = new SnsBrandPreset();
    b.setName("브랜드");
    b = brands.save(b);
    SnsAutomationRule r = new SnsAutomationRule();
    r.setTitle("규칙");
    r.setBrandId(b.getId());
    r.setImagePrompt("사진");
    r.setStartDate(LocalDate.of(2026, 11, 2));
    r.setEnabled(true);
    rule = rules.save(r);
  }

  @Test
  @DisplayName("given 오늘 아직 안 돈 규칙 when claimRun 두 번 then 한 번만 가져간다")
  void claimRun_onlyOncePerDay() {
    // given
    LocalDate today = LocalDate.of(2026, 11, 3);
    // when
    int first = rules.claimRun(rule.getId(), today);
    int second = rules.claimRun(rule.getId(), today);
    // then
    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    assertThat(rules.claimRun(rule.getId(), today.plusDays(1))).isEqualTo(1);
  }

  @Test
  @DisplayName("given 문구가 끝난 이미지 대기 글 when 후보 조회·가져가기 then 한 작업자만 가져가고 문구 미완료 글은 제외")
  void claimImageJob_atomicAndRequiresCaption() {
    // given
    SnsPost ready = post(SnsPost.PartStatus.DONE);
    post(SnsPost.PartStatus.RUNNING);
    LocalDateTime now = SnsTime.now();
    // when
    var candidates = posts.imageJobCandidates(now.minusMinutes(15), PageRequest.of(0, 5));
    int a = posts.claimImageJob(ready.getId(), 0, now, now.minusMinutes(15));
    int b = posts.claimImageJob(ready.getId(), 0, now, now.minusMinutes(15));
    // then
    assertThat(candidates).extracting(SnsPost::getId).containsExactly(ready.getId());
    assertThat(a).isEqualTo(1);
    assertThat(b).isZero();
    SnsPost after = posts.findById(ready.getId()).orElseThrow();
    assertThat(after.getImageStatus()).isEqualTo(SnsPost.PartStatus.RUNNING);
    assertThat(after.getImageAttempts()).isEqualTo(1);
  }

  @Test
  @DisplayName("given 오래 멈춘 RUNNING 작업 when 후보 조회 then 다시 내준다")
  void staleRunningJob_isReclaimable() {
    // given
    SnsPost p = post(SnsPost.PartStatus.DONE);
    p.setImageStatus(SnsPost.PartStatus.RUNNING);
    p.setImageAttempts(1);
    p.setImageClaimedAt(SnsTime.now().minusMinutes(30));
    posts.save(p);
    LocalDateTime now = SnsTime.now();
    // when
    int claimed = posts.claimImageJob(p.getId(), 1, now, now.minusMinutes(15));
    // then
    assertThat(claimed).isEqualTo(1);
  }

  @Test
  @DisplayName("given 시험·버린 글과 일반 글 when recentTopics then 일반 글 주제만 최신순")
  void recentTopics_excludesTrialAndDiscarded() {
    // given
    SnsPost normal = post(SnsPost.PartStatus.DONE);
    normal.setTopic("관계대명사");
    posts.save(normal);
    SnsPost trial = post(SnsPost.PartStatus.DONE);
    trial.setTrial(true);
    trial.setTopic("시험 주제");
    posts.save(trial);
    SnsPost discarded = post(SnsPost.PartStatus.DONE);
    discarded.setTopic("버린 주제");
    discarded.setStatus(SnsPost.Status.DISCARDED);
    posts.save(discarded);
    // when
    var topics = posts.recentTopics(rule.getId(), PageRequest.of(0, 30));
    // then
    assertThat(topics).containsExactly("관계대명사");
  }

  @Test
  @DisplayName("given 이번 달 생성 기록 when sumCostSince then 비용 합계")
  void sumCost() {
    // given
    logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.IMAGE, rule.getId(), null, "m", "p", 40, 100, true, null));
    logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.TEXT, rule.getId(), null, "m", "p", 0, 100, true, null));
    // when
    long sum = logs.sumCostSince(SnsTime.now().minusDays(1));
    long ruleSum = logs.sumCostSinceForRule(rule.getId(), SnsTime.now().minusDays(1));
    // then
    assertThat(sum).isEqualTo(40);
    assertThat(ruleSum).isEqualTo(40);
    assertThat(logs.countGenerationsSince(SnsTime.now().minusDays(1))).isEqualTo(2);
  }

  private SnsPost post(SnsPost.PartStatus captionStatus) {
    SnsPost p = new SnsPost();
    p.setRuleId(rule.getId());
    p.setCaptionStatus(captionStatus);
    p.setImageStatus(SnsPost.PartStatus.PENDING);
    p.setImagePrompt("prompt");
    return posts.save(p);
  }
}
