package f2r.tutor_solution.private_tutor_solution_back.domain.landing.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LandingVideoSlotRepositoryContractTest {

  @Test
  void replacementLookup_usesPessimisticWriteLock() throws Exception {
    // given
    Method method = LandingVideoSlotRepository.class.getMethod(
        "findBySlotForUpdate", LandingVideoSlotKey.class);

    // when
    Lock lock = method.getAnnotation(Lock.class);
    Query query = method.getAnnotation(Query.class);

    // then
    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    assertThat(query).isNotNull();
    assertThat(query.value().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
        .contains("from landingvideoslot slot")
        .contains("slot.slot = :slot");
  }
}
