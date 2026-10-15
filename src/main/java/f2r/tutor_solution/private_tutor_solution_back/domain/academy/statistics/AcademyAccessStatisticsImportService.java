package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademyAccessStatisticsImportService {
  private final AcademyAccessStatisticsStore store;

  @Transactional
  public void importObject(String objectKey, String etag, List<DailyCount> counts) {
    if (store.wasImported(objectKey)) return;
    long imported = 0;
    for (DailyCount count : counts) {
      store.increment(count.academyId(), count.date(), count.count(), count.lastLogAt());
      imported += count.count();
    }
    store.markImported(objectKey, etag, imported);
  }

  public record DailyCount(Long academyId, LocalDate date, long count, Instant lastLogAt) {}
}
