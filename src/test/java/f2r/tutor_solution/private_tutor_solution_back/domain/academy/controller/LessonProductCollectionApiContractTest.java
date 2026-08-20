package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LessonProductCollectionApiContractTest {

  private static final String DTO_PACKAGE =
      "f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.";

  @Test
  void collectionSaveDtoTypes_existAsRecords() throws Exception {
    assertThat(Class.forName(DTO_PACKAGE + "LessonCollectionSaveRequest").isRecord()).isTrue();
    assertThat(Class.forName(DTO_PACKAGE + "LessonCollectionItemRequest").isRecord()).isTrue();
    assertThat(Class.forName(DTO_PACKAGE + "LessonCollectionSaveResponse").isRecord()).isTrue();
  }

  @Test
  void collectionItem_requiresStableIdentityAndRevisionWithoutOperationalCounters() throws Exception {
    Class<?> itemType = Class.forName(DTO_PACKAGE + "LessonCollectionItemRequest");
    Set<String> components = Arrays.stream(itemType.getRecordComponents())
        .map(RecordComponent::getName)
        .collect(Collectors.toSet());

    assertThat(components)
        .contains("lessonId", "creationKey", "expectedAuthoringRevision", "schoolName", "gradeLabel")
        .doesNotContain("currentCount", "visible", "isVisible");
  }

  @Test
  void collectionSave_hasOnePutEndpoint() {
    Method endpoint = Arrays.stream(AcademyController.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(PutMapping.class))
        .filter(method -> Arrays.asList(method.getAnnotation(PutMapping.class).value())
            .contains("/api/tutor/academy/lessons/collection"))
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "PUT /api/tutor/academy/lessons/collection endpoint must exist"));

    assertThat(endpoint.getReturnType().getSimpleName()).isEqualTo("ResponseEntity");
  }
}
