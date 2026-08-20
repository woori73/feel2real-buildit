package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LessonProductRepositoryLockContractTest {

  @Test
  void singleLessonWriterHydration_fetchesOwnershipAndUsesPessimisticLock() throws Exception {
    // given
    Method method = LessonProductRepository.class.getMethod(
        "findForUpdateByLessonId", Long.class);
    Lock lock = method.getAnnotation(Lock.class);
    Query query = method.getAnnotation(Query.class);

    // then
    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    assertThat(normalized(query))
        .contains("join fetch l.academy a")
        .contains("join fetch a.tutor")
        .contains("l.lessonid = :lessonid");
  }

  @Test
  void creationKeyPreResolution_returnsScalarProjectionAndStaysAcademyScoped() throws Exception {
    // given
    Method method = LessonProductRepository.class.getMethod(
        "findCreationKeyBindings", Long.class, Set.class);
    Query query = method.getAnnotation(Query.class);

    // then
    assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
    ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
    assertThat(returnType.getRawType()).isEqualTo(List.class);
    assertThat(returnType.getActualTypeArguments())
        .containsExactly(LessonCreationKeyBindingProjection.class);
    assertThat(LessonCreationKeyBindingProjection.class.isInterface()).isTrue();
    assertThat(LessonProduct.class.isAssignableFrom(LessonCreationKeyBindingProjection.class))
        .isFalse();
    assertThat(projectionGetterNames())
        .containsExactlyInAnyOrder("getLessonId", "getCreationKey");

    String jpql = normalized(query);
    assertThat(jpql)
        .contains("select l.lessonid as lessonid")
        .contains("l.creationkey as creationkey")
        .contains("l.academy.academyid = :academyid")
        .contains("l.creationkey in :creationkeys")
        .doesNotContain("join fetch");
  }

  @Test
  void orderedEntityHydration_isAcademyScopedAscendingAndPessimisticallyLocked() throws Exception {
    // given
    Method method = LessonProductRepository.class.getMethod(
        "findAllForUpdateByAcademyIdAndLessonIds", Long.class, Collection.class);
    Lock lock = method.getAnnotation(Lock.class);
    Query query = method.getAnnotation(Query.class);

    // then
    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);

    String jpql = normalized(query);
    assertThat(jpql)
        .startsWith("select l from lessonproduct l")
        .contains("l.academy.academyid = :academyid")
        .contains("l.lessonid in :lessonids")
        .contains("order by l.lessonid asc");
  }

  private Set<String> projectionGetterNames() {
    return Arrays.stream(LessonCreationKeyBindingProjection.class.getDeclaredMethods())
        .map(Method::getName)
        .collect(java.util.stream.Collectors.toSet());
  }

  private String normalized(Query query) {
    assertThat(query).isNotNull();
    return query.value()
        .replaceAll("\\s+", " ")
        .trim()
        .toLowerCase(Locale.ROOT);
  }
}
