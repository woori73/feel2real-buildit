package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AcademyHandleResolverTest {

  @Test
  void givenHandleWasChanged_whenResolvingLogTime_thenEachHandleMapsToSameAcademy() {
    // given
    AcademyHandleResolver resolver = new AcademyHandleResolver(List.of(
        new AcademyHandleResolver.HandlePeriod(7L, "old-handle",
            Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z")),
        new AcademyHandleResolver.HandlePeriod(7L, "new-handle",
            Instant.parse("2026-08-01T00:00:00Z"), null)));

    // when / then
    assertThat(resolver.resolve("old-handle", Instant.parse("2026-07-31T23:59:59Z"))).contains(7L);
    assertThat(resolver.resolve("new-handle", Instant.parse("2026-08-03T00:00:00Z"))).contains(7L);
    assertThat(resolver.resolve("old-handle", Instant.parse("2026-08-03T00:00:00Z"))).isEmpty();
  }
}
