package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("S3 Academy multipart adapter — deterministic SDK contract")
class S3AcademyVideoUploadStorageTest {
  private S3Client s3;
  private S3Presigner presigner;
  private S3AcademyVideoUploadStorage storage;

  @BeforeEach
  void setUp() {
    s3 = mock(S3Client.class);
    presigner = mock(S3Presigner.class);
    storage = new S3AcademyVideoUploadStorage(new AcademyVideoUploadProperties());
    ReflectionTestUtils.setField(storage, "bucket", "private-bucket");
    ReflectionTestUtils.setField(storage, "region", "ap-northeast-2");
    ReflectionTestUtils.setField(storage, "keyPrefix", "test");
    ReflectionTestUtils.setField(storage, "s3", s3);
    ReflectionTestUtils.setField(storage, "presigner", presigner);
  }

  @Test
  @DisplayName("given initiate when presigning then every capability is exact key/upload/part and 180s")
  void initiate_presigns_exact_scope() throws Exception {
    // given
    when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenReturn(
        CreateMultipartUploadResponse.builder().uploadId("server-upload").build());
    when(presigner.presignUploadPart(any(UploadPartPresignRequest.class)))
        .thenAnswer(invocation -> {
          UploadPartPresignRequest request = invocation.getArgument(0);
          int part = request.uploadPartRequest().partNumber();
          PresignedUploadPartRequest signed = mock(PresignedUploadPartRequest.class);
          when(signed.url()).thenReturn(new URL("https://opaque.invalid/" + part));
          return signed;
        });

    // when
    var initiated = storage.initiate(
        "11111111-1111-1111-1111-111111111111", 30_000_000, 2);

    // then
    assertThat(initiated.stagingKey()).isEqualTo(
        "test/academy-video/staging/11111111-1111-1111-1111-111111111111/raw");
    assertThat(initiated.uploadId()).isEqualTo("server-upload");
    assertThat(initiated.opaquePartUrls()).containsExactly(
        "https://opaque.invalid/1", "https://opaque.invalid/2");
    org.mockito.ArgumentCaptor<UploadPartPresignRequest> captor =
        org.mockito.ArgumentCaptor.forClass(UploadPartPresignRequest.class);
    verify(presigner, org.mockito.Mockito.times(2)).presignUploadPart(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(request -> {
      assertThat(request.signatureDuration()).isEqualTo(Duration.ofSeconds(180));
      assertThat(request.uploadPartRequest().bucket()).isEqualTo("private-bucket");
      assertThat(request.uploadPartRequest().key()).isEqualTo(initiated.stagingKey());
      assertThat(request.uploadPartRequest().uploadId()).isEqualTo("server-upload");
    });
    assertThat(captor.getAllValues().stream()
        .map(request -> request.uploadPartRequest().partNumber())).containsExactly(1, 2);
  }

  @Test
  @DisplayName("given persisted ordered manifest when complete then exact SDK parts and Head metadata return")
  void complete_uses_ordered_manifest_and_head() {
    // given
    when(s3.listParts(any(ListPartsRequest.class))).thenReturn(ListPartsResponse.builder().parts(
        Part.builder().partNumber(1).eTag("\"one\"").size(5L).build(),
        Part.builder().partNumber(2).eTag("\"two\"").size(5L).build()).build());
    when(s3.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenReturn(
        CompleteMultipartUploadResponse.builder().eTag("\"whole\"").build());
    when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(
        HeadObjectResponse.builder().eTag("\"whole\"").contentLength(10L).build());

    // when
    var result = storage.complete("key", "upload",
        List.of(new AcademyVideoUploadStorage.Part(1, "one", null),
            new AcademyVideoUploadStorage.Part(2, "two", null)),
        Duration.ofSeconds(5));

    // then
    assertThat(result).isEqualTo(
        new AcademyVideoUploadStorage.CompletedObject("\"whole\"", 10));
    org.mockito.ArgumentCaptor<CompleteMultipartUploadRequest> captor =
        org.mockito.ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
    verify(s3).completeMultipartUpload(captor.capture());
    assertThat(captor.getValue().multipartUpload().parts())
        .extracting(software.amazon.awssdk.services.s3.model.CompletedPart::partNumber)
        .containsExactly(1, 2);
  }

  @Test
  @DisplayName("given missing or duplicate manifest parts when complete then fail before S3 completion")
  void rejects_manifest_substitution() {
    // given
    when(s3.listParts(any(ListPartsRequest.class))).thenReturn(ListPartsResponse.builder().parts(
        Part.builder().partNumber(1).eTag("one").size(5L).build(),
        Part.builder().partNumber(2).eTag("two").size(5L).build()).build());

    // when / then
    assertThatThrownBy(() -> storage.complete("key", "upload",
        List.of(new AcademyVideoUploadStorage.Part(1, "one", null),
            new AcademyVideoUploadStorage.Part(1, "two", null)),
        Duration.ofSeconds(5)))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("MANIFEST_MISMATCH");
    verify(s3, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
  }

  @Test
  @DisplayName("given abort already completed when abort retried then NoSuchUpload is idempotent")
  void abort_is_idempotent() {
    // given
    when(s3.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenThrow(
        NoSuchUploadException.builder().message("gone").build());

    // when
    storage.abort("key", "upload");
    storage.abort("key", "upload");

    // then
    verify(s3, org.mockito.Mockito.times(2))
        .abortMultipartUpload(any(AbortMultipartUploadRequest.class));
  }

  @Test
  @DisplayName("given object was already deleted when cleanup confirms with head then absence is typed success signal")
  void cleanup_head_normalizes_confirmed_absence() {
    // given
    when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(
        S3Exception.builder().statusCode(404).message("gone").build());

    // when / then
    assertThatThrownBy(() -> storage.inspectWorkerObject("key", Duration.ofSeconds(5)))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("OBJECT_MISSING");
  }

  @Test
  @DisplayName("given cleanup abort transient failure when strict abort runs then failure propagates")
  void cleanup_abort_propagates_transient_failure() {
    // given
    when(s3.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenThrow(
        S3Exception.builder().statusCode(503).message("transient").build());

    // when / then
    assertThatThrownBy(() -> storage.abortForCleanup("key", "upload"))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("S3_ABORT_FAILED");
  }

  @Test
  @DisplayName("given cleanup abort confirmed absent when strict abort runs then it succeeds")
  void cleanup_abort_accepts_only_confirmed_absence() {
    // given
    when(s3.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenThrow(
        NoSuchUploadException.builder().message("gone").build());

    // when / then
    storage.abortForCleanup("key", "upload");
    verify(s3).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
  }

  @Test
  @DisplayName("given configured key prefix when reconciling attempts then logical keys are mapped once")
  void reconciliation_applies_key_prefix_without_double_prefixing() {
    // given
    Instant cutoff = Instant.parse("2026-01-02T00:00:00Z");
    String physicalKey = "test/academy-video/attempts/7/2/lease/output.mp4";
    when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
        ListObjectsV2Response.builder().contents(S3Object.builder()
            .key(physicalKey).lastModified(cutoff.minusSeconds(1)).build()).build());

    // when
    var listed = storage.listWorkerObjects("academy-video/attempts/", cutoff, 10);
    storage.deleteWorkerObject("academy-video/attempts/7/2/lease/output.mp4");
    storage.deleteWorkerObject(listed.getFirst().key());

    // then
    var listCaptor = org.mockito.ArgumentCaptor.forClass(ListObjectsV2Request.class);
    verify(s3).listObjectsV2(listCaptor.capture());
    assertThat(listCaptor.getValue().prefix()).isEqualTo("test/academy-video/attempts/");
    assertThat(listed).extracting(AcademyVideoWorkerStorage.StoredKey::key)
        .containsExactly(physicalKey);
    assertThat(storage.attemptKey(7L, 2, "lease", "output.mp4")).isEqualTo(physicalKey);

    var deleteCaptor = org.mockito.ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3, org.mockito.Mockito.times(2)).deleteObject(deleteCaptor.capture());
    assertThat(deleteCaptor.getAllValues()).extracting(DeleteObjectRequest::key)
        .containsExactly(physicalKey, physicalKey);
  }

  @Test
  @DisplayName("given exhausted remaining budget when inspect/head/complete then no SDK call starts")
  void deadline_exhaustion_stops_sdk_calls() {
    // given / when / then
    assertThatThrownBy(() -> storage.inspect("key", "upload", Duration.ZERO))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("DEADLINE_EXCEEDED");
    assertThatThrownBy(() -> storage.head("key", Duration.ZERO))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("DEADLINE_EXCEEDED");
    verify(s3, never()).listParts(any(ListPartsRequest.class));
    verify(s3, never()).headObject(any(HeadObjectRequest.class));
  }
}
