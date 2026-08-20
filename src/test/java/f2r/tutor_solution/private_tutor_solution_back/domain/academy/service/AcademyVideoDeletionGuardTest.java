package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AcademyVideoDeletionGuardTest {

  @Test
  void givenAnyVideoLifecycleState_whenParentDeleteGuards_thenConflict() {
    var f = fixtures();
    when(f.videos.existsByAcademy_AcademyId(7L)).thenReturn(true);

    assertThatThrownBy(() -> f.guard.requireFullyRemoved(7L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }

  @Test
  void givenUploadSessionJobOrCleanupState_whenParentDeleteGuards_thenConflict() {
    var session = fixtures();
    when(session.sessions.existsByAcademy_AcademyId(7L)).thenReturn(true);
    assertThatThrownBy(() -> session.guard.requireFullyRemoved(7L))
        .isInstanceOf(ResponseStatusException.class);

    var job = fixtures();
    when(job.jobs.existsByVideo_Academy_AcademyId(7L)).thenReturn(true);
    assertThatThrownBy(() -> job.guard.requireFullyRemoved(7L))
        .isInstanceOf(ResponseStatusException.class);

    var cleanup = fixtures();
    when(cleanup.cleanups.existsByVideo_Academy_AcademyId(7L)).thenReturn(true);
    assertThatThrownBy(() -> cleanup.guard.requireFullyRemoved(7L))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void givenAllVideoStatePhysicallyRemoved_whenParentDeleteGuards_thenItAllowsDelete() {
    var f = fixtures();
    assertThatCode(() -> f.guard.requireFullyRemoved(7L)).doesNotThrowAnyException();
  }

  private static Fixtures fixtures() {
    AcademyVideoRepository videos = mock(AcademyVideoRepository.class);
    AcademyVideoUploadSessionRepository sessions = mock(AcademyVideoUploadSessionRepository.class);
    AcademyVideoOptimizationJobRepository jobs = mock(AcademyVideoOptimizationJobRepository.class);
    AcademyVideoAssetCleanupRepository cleanups = mock(AcademyVideoAssetCleanupRepository.class);
    return new Fixtures(videos, sessions, jobs, cleanups,
        new AcademyVideoDeletionGuard(videos, sessions, jobs, cleanups));
  }

  private record Fixtures(
      AcademyVideoRepository videos,
      AcademyVideoUploadSessionRepository sessions,
      AcademyVideoOptimizationJobRepository jobs,
      AcademyVideoAssetCleanupRepository cleanups,
      AcademyVideoDeletionGuard guard) {}
}
