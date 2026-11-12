package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublishingJobLockedStoriesTest {

  @Test
  @DisplayName("lockedStories가 없으면 빈 집합을 반환한다")
  void emptyWhenNotSet() {
    // given
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    // when & then
    assertThat(job.getLockedStoryIds()).isEmpty();
    assertThat(job.isStoryLocked(20L)).isFalse();
  }

  @Test
  @DisplayName("JSON 배열을 스토리 ID 집합으로 파싱한다")
  void parsesJsonArray() {
    // given
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    // when
    job.setLockedStories("[20, 35]");
    // then
    assertThat(job.getLockedStoryIds()).containsExactlyInAnyOrder(20L, 35L);
    assertThat(job.isStoryLocked(20L)).isTrue();
    assertThat(job.isStoryLocked(99L)).isFalse();
  }

  @Test
  @DisplayName("잘못된 JSON이면 빈 집합을 반환한다")
  void emptyOnInvalidJson() {
    // given
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    // when
    job.setLockedStories("not-json");
    // then
    assertThat(job.getLockedStoryIds()).isEmpty();
  }

  @Test
  @DisplayName("EN_WORKSHEET_A4 템플릿 코드가 등록된다")
  void worksheetTemplateCode() {
    assertThat(PublishingJob.Template.fromCode("en-worksheet-a4"))
        .isEqualTo(PublishingJob.Template.EN_WORKSHEET_A4);
  }
}
