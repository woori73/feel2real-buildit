package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonTheme;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.PriceUnit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.ScheduleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LessonResponseAuthoringDefaultsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void from_mapsNullableLegacyAuthoringValuesToEffectiveThemeAndRevision() {
    // given
    LessonProduct legacyLesson = LessonProduct.builder()
        .name("레거시 수업")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주3회 월/수/금")
        .theme(null)
        .authoringRevision(null)
        .build();

    // when
    LessonResponse response = LessonResponse.from(legacyLesson);

    // then
    assertThat(response.theme()).isEqualTo(LessonTheme.NAVY);
    assertThat(response.authoringRevision()).isZero();
    assertThat(response.creationKey()).isNull();
    assertThat(response.tabGuidanceLabel()).isNull();
    assertThat(response.tabShortName()).isNull();
    assertThat(response.kicker()).isNull();
    assertThat(response.highlight()).isNull();
    assertThat(response.learnerNeed()).isNull();
    assertThat(response.coreDetailTitle()).isNull();
    assertThat(response.explanation1()).isNull();
    assertThat(response.explanation2()).isNull();
    assertThat(response.explanation3()).isNull();
  }

  @Test
  void serialization_omitsInternalAuthoringMetadataOnlyFromPublicResponse() throws Exception {
    // given: revision이 null인 레거시 수업도 강사 응답에서는 유효 revision 0을 제공한다.
    LessonProduct legacyLesson = LessonProduct.builder()
        .name("레거시 수업")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주3회 월/수/금")
        .creationKey("5fd2e8f8-37f0-4f8d-a56c-fd42ed54c553")
        .authoringRevision(null)
        .build();

    // when
    JsonNode tutorJson = objectMapper.readTree(
        objectMapper.writeValueAsBytes(LessonResponse.from(legacyLesson)));
    JsonNode publicJson = objectMapper.readTree(
        objectMapper.writeValueAsBytes(LessonResponse.fromPublic(legacyLesson)));

    // then
    assertThat(tutorJson.has("creationKey")).isTrue();
    assertThat(tutorJson.path("creationKey").asText())
        .isEqualTo("5fd2e8f8-37f0-4f8d-a56c-fd42ed54c553");
    assertThat(tutorJson.has("authoringRevision")).isTrue();
    assertThat(tutorJson.path("authoringRevision").asLong()).isZero();
    assertThat(publicJson.has("creationKey")).isFalse();
    assertThat(publicJson.has("authoringRevision")).isFalse();
    assertThat(publicJson.path("name").asText()).isEqualTo("레거시 수업");
    assertThat(publicJson.has("description")).isTrue();
  }

  @Test
  void serialization_includesSchoolGradeForTutorAndOmitsThemFromPublicResponse() throws Exception {
    // given
    LessonProduct lesson = LessonProduct.builder()
        .name("중2 영어")
        .description("내신 대비")
        .schoolName("압구정중")
        .gradeLabel("중2")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .scheduleType(ScheduleType.WEEKLY)
        .scheduleSummary("주3회")
        .build();

    // when
    JsonNode tutorJson = objectMapper.readTree(objectMapper.writeValueAsBytes(LessonResponse.from(lesson)));
    JsonNode publicJson = objectMapper.readTree(objectMapper.writeValueAsBytes(LessonResponse.fromPublic(lesson)));

    // then
    assertThat(tutorJson.path("schoolName").asText()).isEqualTo("압구정중");
    assertThat(tutorJson.path("gradeLabel").asText()).isEqualTo("중2");
    assertThat(publicJson.has("schoolName")).isFalse();
    assertThat(publicJson.has("gradeLabel")).isFalse();
  }
}
