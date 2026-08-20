package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AcademyAccessStatisticsStoreMySqlTest {

  @Autowired AcademyRepository academyRepository;
  @Autowired AcademyAccessStatisticsStore store;
  @Autowired AcademyAccessStatisticsImportService importService;

  @Test
  void givenSameAlbObjectImportedTwice_whenCommittedInOneTransaction_thenCountIncreasesOnce() {
    // given
    Academy academy = academyRepository.findAll().getFirst();
    LocalDate date = LocalDate.of(2026, 8, 3);
    long before = store.summary(academy.getAcademyId()).totalRequestCount();
    var counts = List.of(new AcademyAccessStatisticsImportService.DailyCount(
        academy.getAcademyId(), date, 3, Instant.parse("2026-08-03T01:02:03Z")));

    // when
    importService.importObject("test/AWSLogs/idempotent-object.log.gz", "etag", counts);
    importService.importObject("test/AWSLogs/idempotent-object.log.gz", "etag", counts);

    // then
    assertThat(store.summary(academy.getAcademyId()).totalRequestCount()).isEqualTo(before + 3);
    assertThat(store.daily(academy.getAcademyId(), date)).contains(
        new AcademyAccessStatisticsStore.DailyRow(date, 3));
  }
}
