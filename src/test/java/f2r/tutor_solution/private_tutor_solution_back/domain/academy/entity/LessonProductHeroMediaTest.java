package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 수업상품 대표 미디어(히어로) 불변식. */
class LessonProductHeroMediaTest {

  @Test
  @DisplayName("given 이미지와 영상을 동시에 지정 when 저작 수정 then XOR 위반으로 거부한다")
  void updateAuthoringDetails_rejectsBothImageAndVideo() {
    // given
    LessonProduct lesson = lesson();

    // when / then
    assertThatThrownBy(() -> updateMedia(lesson, "image-key", 7L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("하나만");
  }

  @Test
  @DisplayName("given 이미지가 걸린 수업 when 영상으로 교체 then 이미지 key가 null이 된다")
  void updateAuthoringDetails_replacingImageWithVideo_clearsImageKey() {
    // given
    LessonProduct lesson = lesson();
    updateMedia(lesson, "image-key", null);

    // when
    updateMedia(lesson, null, 7L);

    // then
    assertThat(lesson.getMediaImageKey()).isNull();
    assertThat(lesson.getMediaVideoId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("given 미디어만 변경 when 저작 수정 then changed=true이고 revision이 오른다")
  void updateAuthoringDetails_mediaOnlyChange_incrementsRevision() {
    // given
    LessonProduct lesson = lesson();
    long before = lesson.getEffectiveAuthoringRevision();

    // when
    boolean changed = updateMedia(lesson, "hero.jpg", null);

    // then
    // false를 돌려주면 컬렉션 저장이 replayed로 분류해 변경을 조용히 버린다.
    assertThat(changed).isTrue();
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(before + 1);
  }

  @Test
  @DisplayName("given 미디어가 걸린 수업 when 레거시 updateDetails 호출 then 미디어가 보존된다")
  void updateDetails_preservesHeroMedia() {
    // given
    LessonProduct lesson = lesson();
    updateMedia(lesson, null, 42L);

    // when
    // 레거시 PUT /api/tutor/academy/lessons/{id} 경로. 미디어 필드를 받지 않는다.
    lesson.updateDetails(
        "이름 변경", lesson.getDescription(), lesson.getLessonType(), lesson.getTargetGroup(),
        lesson.getPriceAmount(), lesson.getPriceUnit(), lesson.getPriceSummary(),
        lesson.getMaxCapacity(), lesson.getScheduleType(), lesson.getWeeklyCount(),
        lesson.getStartDate(), lesson.getEndDate(), lesson.getDurationWeeks(),
        lesson.getDurationMonths(), lesson.getScheduleSummary(), lesson.getStatus(),
        lesson.getWeekdays());

    // then
    assertThat(lesson.getName()).isEqualTo("이름 변경");
    assertThat(lesson.getMediaVideoId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("given 영상이 걸린 수업 when 미디어 해제 then 두 필드 모두 null이 된다")
  void updateAuthoringDetails_clearingMedia_nullsBothFields() {
    // given
    LessonProduct lesson = lesson();
    updateMedia(lesson, null, 42L);

    // when
    updateMedia(lesson, null, null);

    // then
    assertThat(lesson.getMediaImageKey()).isNull();
    assertThat(lesson.getMediaVideoId()).isNull();
  }

  @Test
  @DisplayName("given 대표 이미지가 있는 수업 when OT 영상만 변경 then 대표 미디어는 보존된다")
  void updateAuthoringDetails_changingOtVideo_preservesHeroMedia() {
    // given
    LessonProduct lesson = lesson();
    updateMedia(lesson, "hero.jpg", null);
    long before = lesson.getEffectiveAuthoringRevision();

    // when
    boolean changed = updateOtVideo(lesson, 9L);

    // then
    assertThat(changed).isTrue();
    assertThat(lesson.getMediaImageKey()).isEqualTo("hero.jpg");
    assertThat(lesson.getMediaVideoId()).isNull();
    assertThat(lesson.getOtVideoId()).isEqualTo(9L);
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(before + 1);
  }

  private static LessonProduct lesson() {
    return LessonProduct.builder()
        .name("고2 영어")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .scheduleType(ScheduleType.WEEKLY)
        .status(LessonStatus.PUBLISHED)
        .authoringRevision(1L)
        .build();
  }

  private static boolean updateMedia(LessonProduct lesson, String imageKey, Long videoId) {
    return lesson.updateAuthoringDetails(
        lesson.getName(), lesson.getDescription(), lesson.getSchoolName(), lesson.getGradeLabel(),
        lesson.getLessonType(),
        lesson.getTargetGroup(), lesson.getPriceAmount(), lesson.getPriceUnit(),
        lesson.getPriceSummary(), lesson.getMaxCapacity(), lesson.getScheduleType(),
        lesson.getWeeklyCount(), lesson.getStartDate(), lesson.getEndDate(),
        lesson.getDurationWeeks(), lesson.getDurationMonths(), lesson.getScheduleSummary(),
        lesson.getStatus(), Set.of(), lesson.getTabGuidanceLabel(), lesson.getTabShortName(),
        lesson.getKicker(), lesson.getHighlight(), lesson.getLearnerNeed(),
        lesson.getCoreDetailTitle(), lesson.getExplanation1(), lesson.getExplanation2(),
        lesson.getExplanation3(), lesson.getTheme(), imageKey, videoId, lesson.getOtVideoId());
  }

  private static boolean updateOtVideo(LessonProduct lesson, Long otVideoId) {
    return lesson.updateAuthoringDetails(
        lesson.getName(), lesson.getDescription(), lesson.getSchoolName(), lesson.getGradeLabel(),
        lesson.getLessonType(),
        lesson.getTargetGroup(), lesson.getPriceAmount(), lesson.getPriceUnit(),
        lesson.getPriceSummary(), lesson.getMaxCapacity(), lesson.getScheduleType(),
        lesson.getWeeklyCount(), lesson.getStartDate(), lesson.getEndDate(),
        lesson.getDurationWeeks(), lesson.getDurationMonths(), lesson.getScheduleSummary(),
        lesson.getStatus(), Set.of(), lesson.getTabGuidanceLabel(), lesson.getTabShortName(),
        lesson.getKicker(), lesson.getHighlight(), lesson.getLearnerNeed(),
        lesson.getCoreDetailTitle(), lesson.getExplanation1(), lesson.getExplanation2(),
        lesson.getExplanation3(), lesson.getTheme(), lesson.getMediaImageKey(),
        lesson.getMediaVideoId(), otVideoId);
  }
}
