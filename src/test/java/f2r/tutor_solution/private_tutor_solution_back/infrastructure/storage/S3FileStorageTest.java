package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FileStorageTest {

  private S3FileStorage storage;
  private S3Client s3;

  @BeforeEach
  void setUp() {
    storage = new S3FileStorage();
    s3 = mock(S3Client.class);
    ReflectionTestUtils.setField(storage, "s3", s3);
    ReflectionTestUtils.setField(storage, "bucket", "test-bucket");
  }

  @Test
  void readSingleLimited_returnsObjectWithinLimit() throws Exception {
    // given
    byte[] payload = {1, 2, 3, 4};
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenReturn(responseStream(payload.length, new ByteArrayInputStream(payload)));

    // when
    byte[] result = storage.readSingleLimited("secret/key.mp4", "UNKNOWN", payload.length);

    // then
    assertThat(result).containsExactly(payload);
  }

  @Test
  void readSingleLimited_rejectsMetadataOverLimitBeforeReadingBody() throws Exception {
    // given
    CountingInputStream body = new CountingInputStream(new byte[100]);
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenReturn(responseStream(100, body));

    // when / then
    assertThatThrownBy(() -> storage.readSingleLimited("secret/key.mp4", "UNKNOWN", 10))
        .isInstanceOf(StorageReadLimitExceededException.class)
        .hasMessageNotContaining("secret/key.mp4");
    assertThat(body.bytesRead()).isZero();
  }

  @Test
  void readSingleLimited_readsAtMostLimitPlusOneWhenMetadataIsWrong() throws Exception {
    // given
    CountingInputStream body = new CountingInputStream(new byte[100]);
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenReturn(responseStream(1, body));

    // when / then
    assertThatThrownBy(() -> storage.readSingleLimited("secret/key.mp4", "UNKNOWN", 10))
        .isInstanceOf(StorageReadLimitExceededException.class);
    assertThat(body.bytesRead()).isEqualTo(11);
  }

  @Test
  void readSingleLimited_normalizesNoSuchKeyWithoutLeakingObjectKey() {
    // given
    String secretKey = "prd/private/landing/secret-video.mp4";
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("missing " + secretKey).build());

    // when / then
    assertThatThrownBy(() -> storage.readSingleLimited(secretKey, "VIDEO", 10))
        .isExactlyInstanceOf(StorageReadException.class)
        .hasMessage("저장소 객체를 읽을 수 없습니다.")
        .hasMessageNotContaining(secretKey)
        .hasNoCause();
  }

  @Test
  void readSingleLimited_normalizesSdkClientFailureWithoutLeakingObjectKey() {
    // given
    String secretKey = "prd/private/landing/secret-video.mp4";
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenThrow(SdkClientException.builder().message("timeout reading " + secretKey).build());

    // when / then
    assertThatThrownBy(() -> storage.readSingleLimited(secretKey, "VIDEO", 10))
        .isExactlyInstanceOf(StorageReadException.class)
        .hasMessageNotContaining(secretKey)
        .hasNoCause();
  }

  @Test
  void readSingleLimited_normalizesResponseBodyIoFailureWithoutLeakingObjectKey()
      throws Exception {
    // given
    String secretKey = "prd/private/landing/secret-video.mp4";
    InputStream failingBody =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("socket failed while reading " + secretKey);
          }
        };
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenReturn(responseStream(1, failingBody));

    // when / then
    assertThatThrownBy(() -> storage.readSingleLimited(secretKey, "VIDEO", 10))
        .isExactlyInstanceOf(StorageReadException.class)
        .hasMessageNotContaining(secretKey)
        .hasNoCause();
  }

  @Test
  void deleteReported_isIdempotentAndReportsFailure() {
    // given
    doThrow(new RuntimeException("must-not-log-secret/key.mp4"))
        .when(s3).deleteObject(any(DeleteObjectRequest.class));

    // when / then
    assertThat(storage.deleteReported(null, "UNKNOWN")).isTrue();
    assertThat(storage.deleteReported(" ", "UNKNOWN")).isTrue();
    verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    assertThat(storage.deleteReported("secret/key.mp4", "UNKNOWN")).isFalse();
  }

  private ResponseInputStream<GetObjectResponse> responseStream(
      long contentLength, InputStream inputStream) {
    GetObjectResponse response = GetObjectResponse.builder()
        .contentLength(contentLength)
        .build();
    return new ResponseInputStream<>(response, inputStream);
  }

  private static final class CountingInputStream extends ByteArrayInputStream {
    private int bytesRead;

    private CountingInputStream(byte[] bytes) {
      super(bytes);
    }

    @Override
    public synchronized int read() {
      int value = super.read();
      if (value >= 0) {
        bytesRead++;
      }
      return value;
    }

    @Override
    public synchronized int read(byte[] bytes, int offset, int length) {
      int read = super.read(bytes, offset, length);
      if (read > 0) {
        bytesRead += read;
      }
      return read;
    }

    private int bytesRead() {
      return bytesRead;
    }
  }
}
