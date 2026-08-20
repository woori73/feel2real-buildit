package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import java.time.Instant;
import java.util.*;

public final class AcademyHandleResolver {
  private final Map<String, List<HandlePeriod>> periodsByHandle;

  public AcademyHandleResolver(List<HandlePeriod> periods) {
    Map<String, List<HandlePeriod>> grouped = new HashMap<>();
    for (HandlePeriod period : periods) {
      grouped.computeIfAbsent(period.handle(), ignored -> new ArrayList<>()).add(period);
    }
    grouped.values().forEach(values -> values.sort(Comparator.comparing(HandlePeriod::validFrom)));
    this.periodsByHandle = Map.copyOf(grouped);
  }

  public Optional<Long> resolve(String handle, Instant occurredAt) {
    return periodsByHandle.getOrDefault(handle, List.of()).stream()
        .filter(period -> !occurredAt.isBefore(period.validFrom()))
        .filter(period -> period.validTo() == null || occurredAt.isBefore(period.validTo()))
        .map(HandlePeriod::academyId)
        .findFirst();
  }

  public record HandlePeriod(Long academyId, String handle, Instant validFrom, Instant validTo) {}
}
