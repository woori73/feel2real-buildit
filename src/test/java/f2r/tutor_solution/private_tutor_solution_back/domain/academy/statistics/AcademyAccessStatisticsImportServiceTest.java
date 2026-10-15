package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

class AcademyAccessStatisticsImportServiceTest {

  @Test
  void givenAlreadyImportedObject_whenImporting_thenCountsAreNotIncrementedAgain() {
    // given
    AcademyAccessStatisticsStore store = mock(AcademyAccessStatisticsStore.class);
    when(store.wasImported("AWSLogs/object.log.gz")).thenReturn(true);
    AcademyAccessStatisticsImportService service = new AcademyAccessStatisticsImportService(store);

    // when
    service.importObject("AWSLogs/object.log.gz", "etag", List.of(
        new AcademyAccessStatisticsImportService.DailyCount(10L, LocalDate.of(2026, 8, 3), 3,
            Instant.parse("2026-08-03T01:02:03Z"))));

    // then
    verify(store, never()).increment(anyLong(), any(), anyLong(), any());
    verify(store, never()).markImported(any(), any(), anyLong());
  }

  @Test
  void givenNewObject_whenImporting_thenDailyAndTotalAreIncrementedAndObjectIsMarked() {
    // given
    AcademyAccessStatisticsStore store = mock(AcademyAccessStatisticsStore.class);
    when(store.wasImported("AWSLogs/object.log.gz")).thenReturn(false);
    AcademyAccessStatisticsImportService service = new AcademyAccessStatisticsImportService(store);
    Instant lastLogAt = Instant.parse("2026-08-03T01:02:03Z");

    // when
    service.importObject("AWSLogs/object.log.gz", "etag", List.of(
        new AcademyAccessStatisticsImportService.DailyCount(
            10L, LocalDate.of(2026, 8, 3), 3, lastLogAt),
        new AcademyAccessStatisticsImportService.DailyCount(
            11L, LocalDate.of(2026, 8, 3), 2, lastLogAt)));

    // then
    verify(store).increment(10L, LocalDate.of(2026, 8, 3), 3, lastLogAt);
    verify(store).increment(11L, LocalDate.of(2026, 8, 3), 2, lastLogAt);
    verify(store).markImported("AWSLogs/object.log.gz", "etag", 5);
  }
}
