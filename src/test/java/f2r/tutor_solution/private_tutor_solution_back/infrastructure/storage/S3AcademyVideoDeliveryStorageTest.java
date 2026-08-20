package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3AcademyVideoDeliveryStorageTest {

  @Test
  void signsPrivateGetWithCanonicalTypeNoStoreAndThirtySecondMaximum() throws Exception {
    S3Presigner presigner = mock(S3Presigner.class);
    PresignedGetObjectRequest signed = PresignedGetObjectRequest.builder()
        .httpRequest(SdkHttpRequest.builder()
            .uri(URI.create("https://bucket.example/object?signature=opaque"))
            .method(SdkHttpMethod.GET)
            .build())
        .expiration(Instant.now().plusSeconds(30))
        .isBrowserExecutable(true)
        .signedHeaders(Map.of("host", List.of("bucket.example")))
        .build();
    when(presigner.presignGetObject(org.mockito.ArgumentMatchers.any(GetObjectPresignRequest.class)))
        .thenReturn(signed);
    S3AcademyVideoDeliveryStorage storage = new S3AcademyVideoDeliveryStorage();
    ReflectionTestUtils.setField(storage, "bucket", "private-bucket");
    ReflectionTestUtils.setField(storage, "presigner", presigner);

    var target = (AcademyVideoDeliveryStorage.RedirectTarget) storage.resolve(
        "academy-video/staging/session/raw", "video/webm", Duration.ofSeconds(30));

    assertThat(target.location())
        .isEqualTo(URI.create("https://bucket.example/object?signature=opaque"));
    ArgumentCaptor<GetObjectPresignRequest> captor =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(presigner).presignGetObject(captor.capture());
    assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(30));
    assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("private-bucket");
    assertThat(captor.getValue().getObjectRequest().key())
        .isEqualTo("academy-video/staging/session/raw");
    assertThat(captor.getValue().getObjectRequest().responseContentType()).isEqualTo("video/webm");
    assertThat(captor.getValue().getObjectRequest().responseCacheControl()).isEqualTo("no-store");
  }

  @Test
  void rejectsSigningTtlOverThirtySeconds() {
    S3AcademyVideoDeliveryStorage storage = new S3AcademyVideoDeliveryStorage();

    assertThatThrownBy(() -> storage.resolve(
        "key", "video/mp4", Duration.ofSeconds(31)))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("INVALID_DELIVERY_REQUEST");
  }
}
