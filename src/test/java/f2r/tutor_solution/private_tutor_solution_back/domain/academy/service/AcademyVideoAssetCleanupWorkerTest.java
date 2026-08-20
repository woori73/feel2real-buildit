package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoAssetClass;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoStorageException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoUploadStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoWorkerStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Academy video cleanup worker — confirmed absence and strict retry")
class AcademyVideoAssetCleanupWorkerTest {

  @Test
  @SuppressWarnings("unchecked")
  void givenWorkerStorageProviderResolutionFailure_whenReconcileRuns_thenFailureIsPropagated() {
    AcademyVideoCleanupTransactions transactions = mock(AcademyVideoCleanupTransactions.class);
    ObjectProvider<AcademyVideoWorkerStorage> workerProvider = mock(ObjectProvider.class);
    ObjectProvider<AcademyVideoUploadStorage> uploadProvider = mock(ObjectProvider.class);
    BeanCreationException failure = new BeanCreationException("worker storage is ambiguous");
    when(workerProvider.getIfAvailable()).thenThrow(failure);
    AcademyVideoAssetCleanupWorker worker = new AcademyVideoAssetCleanupWorker(
        transactions, workerProvider, uploadProvider, new AcademyVideoUploadProperties(),
        Runnable::run, mock(AcademyVideoMetrics.class));

    assertThatThrownBy(worker::reconcile).isSameAs(failure);
    verify(transactions).reconcileDatabaseState();
  }

  @Test
  @SuppressWarnings("unchecked")
  void givenUploadStorageProviderResolutionFailure_whenMultipartCleanupRuns_thenFailureIsRetried() {
    AcademyVideoCleanupTransactions transactions = mock(AcademyVideoCleanupTransactions.class);
    AcademyVideoCleanupTransactions.CleanupClaim claim = new AcademyVideoCleanupTransactions.CleanupClaim(
        3L, "lease", 1, VideoAssetClass.MULTIPART_UPLOAD, "upload-id", "staging/key", null);
    when(transactions.claimNext(any())).thenReturn(claim);
    ObjectProvider<AcademyVideoWorkerStorage> workerProvider = mock(ObjectProvider.class);
    ObjectProvider<AcademyVideoUploadStorage> uploadProvider = mock(ObjectProvider.class);
    when(uploadProvider.getIfAvailable())
        .thenThrow(new BeanCreationException("UPLOAD_PROVIDER_AMBIGUOUS"));
    AcademyVideoAssetCleanupWorker worker = new AcademyVideoAssetCleanupWorker(
        transactions, workerProvider, uploadProvider, new AcademyVideoUploadProperties(),
        Runnable::run, mock(AcademyVideoMetrics.class));

    worker.poll();

    verify(transactions).retry(claim, "UPLOAD_PROVIDER_AMBIGUOUS");
    verify(transactions, never()).complete(any());
  }

  @Test
  void givenObjectWasDeletedBeforeCrash_whenWorkerRetries_thenMissingIsSuccessAndDbCompletes() {
    // given
    AcademyVideoCleanupTransactions transactions = mock(AcademyVideoCleanupTransactions.class);
    AcademyVideoWorkerStorage workerStorage = mock(AcademyVideoWorkerStorage.class);
    AcademyVideoCleanupTransactions.CleanupClaim claim = new AcademyVideoCleanupTransactions.CleanupClaim(
        1L, "lease", 2, VideoAssetClass.RAW_VIDEO, "raw/key", null, 7L);
    when(transactions.claimNext(any())).thenReturn(claim);
    doThrow(new AcademyVideoStorageException("OBJECT_MISSING"))
        .when(workerStorage).inspectWorkerObject(eq("raw/key"), any(Duration.class));

    // when
    worker(transactions, workerStorage, null).poll();

    // then
    verify(workerStorage).deleteWorkerObject("raw/key");
    verify(transactions).complete(claim);
    verify(transactions, never()).retry(any(), any());
  }

  @Test
  void givenMultipartAbortHasTransientFailure_whenWorkerRuns_thenItRetriesAndNeverCompletes() {
    // given
    AcademyVideoCleanupTransactions transactions = mock(AcademyVideoCleanupTransactions.class);
    AcademyVideoUploadStorage uploadStorage = mock(AcademyVideoUploadStorage.class);
    AcademyVideoCleanupTransactions.CleanupClaim claim = new AcademyVideoCleanupTransactions.CleanupClaim(
        2L, "lease", 1, VideoAssetClass.MULTIPART_UPLOAD, "upload-id", "staging/key", null);
    when(transactions.claimNext(any())).thenReturn(claim);
    doThrow(new AcademyVideoStorageException("S3_ABORT_FAILED"))
        .when(uploadStorage).abortForCleanup("staging/key", "upload-id");

    // when
    worker(transactions, null, uploadStorage).poll();

    // then
    verify(transactions).retry(claim, "S3_ABORT_FAILED");
    verify(transactions, never()).complete(any());
  }

  @SuppressWarnings("unchecked")
  private static AcademyVideoAssetCleanupWorker worker(
      AcademyVideoCleanupTransactions transactions,
      AcademyVideoWorkerStorage workerStorage,
      AcademyVideoUploadStorage uploadStorage) {
    ObjectProvider<AcademyVideoWorkerStorage> workerProvider = mock(ObjectProvider.class);
    ObjectProvider<AcademyVideoUploadStorage> uploadProvider = mock(ObjectProvider.class);
    when(workerProvider.getIfAvailable()).thenReturn(workerStorage);
    when(uploadProvider.getIfAvailable()).thenReturn(uploadStorage);
    return new AcademyVideoAssetCleanupWorker(
        transactions, workerProvider, uploadProvider, new AcademyVideoUploadProperties(),
        Runnable::run, mock(AcademyVideoMetrics.class));
  }
}
