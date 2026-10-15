package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * V1 is collection foundation only: no code path in the collector services may reach the
 * ledger (EasyTransaction/EasyJournalService/EasyAccount*). Enforced structurally — neither
 * service declares a field of any accounting-write-capable type, so it is not merely untested
 * but physically unreachable from this code.
 */
class EasyCollectorNoAccountingWriteTest {

  private static final String[] FORBIDDEN_TYPE_NAME_FRAGMENTS = {
      "EasyTransaction", "EasyJournal", "EasyAccount", "EasyLedger"
  };

  @Test
  void localCollectorServiceHasNoAccountingDependency() {
    assertNoForbiddenFieldType(EasyLocalCollectorService.class);
  }

  @Test
  void collectorTaskServiceHasNoAccountingDependency() {
    assertNoForbiddenFieldType(EasyCollectorTaskService.class);
  }

  private void assertNoForbiddenFieldType(Class<?> type) {
    for (Field field : type.getDeclaredFields()) {
      String fieldTypeName = field.getType().getSimpleName();
      assertThat(Arrays.stream(FORBIDDEN_TYPE_NAME_FRAGMENTS).noneMatch(fieldTypeName::contains))
          .as("%s.%s has forbidden accounting-write-capable type %s", type.getSimpleName(),
              field.getName(), fieldTypeName)
          .isTrue();
    }
  }
}
