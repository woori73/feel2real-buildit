package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AcademyHandleHistoryService {
  private final AcademyHandleHistoryRepository repository;

  public void recordCreated(Academy academy) {
    if (academy.getHandle() != null) {
      repository.save(new AcademyHandleHistory(academy, academy.getHandle(), Instant.now()));
    }
  }

  public void recordChange(Academy academy, String previousHandle, String nextHandle) {
    if (Objects.equals(previousHandle, nextHandle)) return;
    Instant changedAt = Instant.now();
    repository.findFirstByAcademy_AcademyIdAndValidToUtcIsNullOrderByValidFromUtcDesc(
            academy.getAcademyId())
        .ifPresent(open -> open.closeAt(changedAt));
    repository.save(new AcademyHandleHistory(academy, nextHandle, changedAt));
  }
}
