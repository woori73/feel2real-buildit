package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingJobRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublishingWorkerClaimTest {
  @Test void losingClaimDoesNotProvisionOrMutateAnotherWorkersJob() {
    var repository = mock(PublishingJobRepository.class);
    var templates = mock(TemplateService.class);
    var worker = new PublishingWorker(repository, new PublishingProperties(), templates);
    when(repository.claimQueued(1L)).thenReturn(0);
    worker.claimAndProvision(1L);
    verifyNoInteractions(templates);
    verify(repository, never()).findById(anyLong());
  }

  @Test void provisionFailureRetriesWithinCapThenPersistsFailureHistory() throws Exception {
    var repository = mock(PublishingJobRepository.class);
    var templates = mock(TemplateService.class);
    var properties = new PublishingProperties();
    properties.setMaxAttempts(2);
    properties.setStorageRoot("/tmp/f2r-publishing-claim-test");
    var worker = new PublishingWorker(repository, properties, templates);
    var job = new PublishingJob(990001L, "test", PublishingJob.Template.KO_TEXTBOOK_A4);
    when(repository.claimQueued(1L)).thenReturn(1);
    when(repository.findById(1L)).thenReturn(Optional.of(job));
    when(templates.provision(any(), any())).thenThrow(new IOException("synthetic failure"));
    worker.claimAndProvision(1L);
    assertEquals(PublishingJob.Status.QUEUED, job.getStatus());
    assertEquals(1, job.getAttemptCount());
    assertTrue(job.getError().contains("synthetic failure"));
    worker.claimAndProvision(1L);
    assertEquals(PublishingJob.Status.FAILED, job.getStatus());
    assertEquals(2, job.getAttemptCount());
    verify(templates, times(2)).provision(any(), any());
  }

  @Test void transientFailureCanRecoverWithoutDeletingJobOrAttempts() throws Exception {
    var repository = mock(PublishingJobRepository.class);
    var templates = mock(TemplateService.class);
    var props = new PublishingProperties();
    props.setStorageRoot("/tmp/f2r-publishing-claim-test");
    props.setMaxAttempts(3);
    var worker = new PublishingWorker(repository, props, templates);
    var job = new PublishingJob(990001L, "test", PublishingJob.Template.KO_TEXTBOOK_A4);
    when(repository.claimQueued(1L)).thenReturn(1);
    when(repository.findById(1L)).thenReturn(Optional.of(job));
    when(templates.provision(any(), any())).thenThrow(new IOException("retry")).thenReturn(Path.of("/tmp/test.designcraft"));
    worker.claimAndProvision(1L);
    worker.claimAndProvision(1L);
    assertEquals(PublishingJob.Status.RUNNING, job.getStatus());
    assertEquals(2, job.getAttemptCount());
    verify(repository, never()).delete(any());
  }
}
