package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoOptimizationTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AcademyVideoOptimizationRepositoryContractTest {

  @Test
  void givenClaimRepository_whenInspected_thenItUsesSkipLockedAndDatabaseClock() throws Exception {
    Method claim = AcademyVideoOptimizationJobRepository.class
        .getMethod("lockNextDueClaimId");
    String sql = claim.getAnnotation(Query.class).value().toUpperCase();

    assertThat(sql).contains("FOR UPDATE SKIP LOCKED");
    assertThat(sql).contains("CURRENT_TIMESTAMP(6)");
    assertThat(sql).doesNotContain(" OR ");
    assertThat(sql).doesNotContain("CASE ");

    Method recovery = AcademyVideoOptimizationJobRepository.class
        .getMethod("lockNextExpiredId");
    assertThat(recovery.getAnnotation(Query.class).value().toUpperCase())
        .contains("STATUS = 'PROCESSING'", "FOR UPDATE SKIP LOCKED");
  }

  @Test
  void givenLeaseTransactions_whenInspected_thenEveryBoundaryRequiresNew() {
    for (String methodName : java.util.List.of(
        "claimNext", "heartbeat", "retryableFailure", "nonRetryableFailure",
        "enqueueAttemptCleanup")) {
      Method method = Arrays.stream(AcademyVideoOptimizationTransactions.class.getMethods())
          .filter(candidate -> candidate.getName().equals(methodName))
          .findFirst()
          .orElseThrow();
      assertThat(method.getAnnotation(Transactional.class).propagation())
          .as(methodName)
          .isEqualTo(Propagation.REQUIRES_NEW);
    }
  }
}
