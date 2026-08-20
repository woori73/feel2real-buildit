package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AcademyLessonLegacyApiContractTest {

  @Test
  void getLesson_keepsLegacyItemEndpoint() throws Exception {
    Method method = method("getLesson", 2);

    assertThat(method.getAnnotation(GetMapping.class).value())
        .containsExactly("/api/tutor/academy/lessons/{lessonId}");
  }

  @Test
  void createLesson_keepsLegacyItemEndpoint() throws Exception {
    Method method = method("createLesson", 2);

    assertThat(method.getAnnotation(PostMapping.class).value())
        .containsExactly("/api/tutor/academy/lessons");
  }

  @Test
  void updateLesson_keepsLegacyItemEndpoint() throws Exception {
    Method method = method("updateLesson", 3);

    assertThat(method.getAnnotation(PutMapping.class).value())
        .containsExactly("/api/tutor/academy/lessons/{lessonId}");
  }

  @Test
  void deleteLesson_keepsLegacyItemEndpoint() throws Exception {
    Method method = method("deleteLesson", 2);

    assertThat(method.getAnnotation(DeleteMapping.class).value())
        .containsExactly("/api/tutor/academy/lessons/{lessonId}");
  }

  private Method method(String name, int parameterCount) {
    return java.util.Arrays.stream(AcademyController.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(name))
        .filter(candidate -> candidate.getParameterCount() == parameterCount)
        .findFirst()
        .orElseThrow();
  }
}
