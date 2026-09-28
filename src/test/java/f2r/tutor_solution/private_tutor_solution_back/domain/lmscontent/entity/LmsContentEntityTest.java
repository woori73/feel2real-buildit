package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LmsContentEntityTest {

  private static LmsContent.LmsContentBuilder validBuilder() {
    return LmsContent.builder()
        .academy(mock(Academy.class))
        .lessonProduct(mock(LessonProduct.class))
        .title("압구정중 2학년 영어 내신특강 Lesson 5 복습");
  }

  @Test
  void builder_throwsWhenAcademyMissing() {
    assertThatThrownBy(() -> LmsContent.builder()
        .lessonProduct(mock(LessonProduct.class))
        .title("t")
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("academy");
  }

  @Test
  void builder_throwsWhenTitleBlank() {
    assertThatThrownBy(() -> LmsContent.builder()
        .academy(mock(Academy.class))
        .lessonProduct(mock(LessonProduct.class))
        .title("  ")
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void builder_defaultsContentTypeAndStatusWhenOmitted() {
    LmsContent content = validBuilder().build();

    assertThat(content.getContentType()).isEqualTo(LmsContentType.VIDEO);
    assertThat(content.getStatus()).isEqualTo(LmsContentStatus.READY);
  }

  @Test
  void publish_throwsWhenNoVideoAttached() {
    LmsContent content = validBuilder().build();

    assertThatThrownBy(() -> content.publish(LocalDateTime.now()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void publish_setsPublishedStatusAndTimestampOnce() {
    LmsContent content = validBuilder().storageKey("s3://bucket/key.mp4").build();
    LocalDateTime firstPublish = LocalDateTime.of(2026, 9, 24, 10, 0);
    LocalDateTime secondPublish = LocalDateTime.of(2026, 9, 25, 10, 0);

    content.publish(firstPublish);
    content.hide();
    content.publish(secondPublish);

    assertThat(content.getStatus()).isEqualTo(LmsContentStatus.PUBLISHED);
    // 최초 공개 시각은 재공개해도 바뀌지 않는다(발행 이력의 기준점이라 최초값을 지킨다).
    assertThat(content.getPublishedAt()).isEqualTo(firstPublish);
  }

  @Test
  void hide_setsHiddenStatus() {
    LmsContent content = validBuilder().storageKey("s3://bucket/key.mp4").build();
    content.publish(LocalDateTime.now());

    content.hide();

    assertThat(content.getStatus()).isEqualTo(LmsContentStatus.HIDDEN);
  }

  @Test
  void updateDetails_throwsWhenTitleBlank() {
    LmsContent content = validBuilder().build();

    assertThatThrownBy(() -> content.updateDetails("  ", null, null, null, null, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateDetails_updatesEditableFields() {
    LmsContent content = validBuilder().build();

    content.updateDetails("새 제목", "새 설명", "신구중", "중2", "2학기 중간고사", "Lesson 5", 3);

    assertThat(content.getTitle()).isEqualTo("새 제목");
    assertThat(content.getDescription()).isEqualTo("새 설명");
    assertThat(content.getSchoolName()).isEqualTo("신구중");
    assertThat(content.getGradeLabel()).isEqualTo("중2");
    assertThat(content.getExamName()).isEqualTo("2학기 중간고사");
    assertThat(content.getUnitLabel()).isEqualTo("Lesson 5");
    assertThat(content.getDisplayOrder()).isEqualTo(3);
  }
}
