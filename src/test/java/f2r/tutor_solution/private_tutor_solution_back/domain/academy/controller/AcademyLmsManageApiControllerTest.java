package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LmsManageLessonResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.PriceUnit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AcademyLmsManageApiControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void listLessonsDelegatesHandleOwnerLookupAndReturnsItsStableOrder() {
    // given
    AcademyService academyService = Mockito.mock(AcademyService.class);
    List<LmsManageLessonResponse> expected = List.of(
        new LmsManageLessonResponse(2L, "압구정중", "중2", "A", 149000, PriceUnit.COURSE, "소개"),
        new LmsManageLessonResponse(7L, null, null, "B", null, null, null));
    when(academyService.listOwnedLmsLessonsByHandle("edu-holic")).thenReturn(expected);
    AcademyLmsManageApiController controller = new AcademyLmsManageApiController(academyService);

    // when
    var response = controller.listLessons("edu-holic");

    // then
    assertThat(response.getBody()).containsExactlyElementsOf(expected);
    verify(academyService).listOwnedLmsLessonsByHandle("edu-holic");
  }

  @Test
  void manageJsonUsesOnlyTheSevenFieldAllowlist() throws Exception {
    // given
    LmsManageLessonResponse response = new LmsManageLessonResponse(
        2L, "압구정중", "중2", "A", 149000, PriceUnit.COURSE, "소개");

    // when
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));
    Set<String> keys = java.util.stream.StreamSupport.stream(
        ((Iterable<String>) () -> json.fieldNames()).spliterator(), false).collect(Collectors.toSet());

    // then
    assertThat(keys).containsExactlyInAnyOrder(
        "lessonId", "schoolName", "gradeLabel", "name", "priceAmount", "priceUnit", "description");
    assertThat(keys).doesNotContain(
        "creationKey", "authoringRevision", "mediaImageKey", "mediaVideoId", "otVideoId",
        "scheduleType", "maxCapacity", "highlight");
  }
}
