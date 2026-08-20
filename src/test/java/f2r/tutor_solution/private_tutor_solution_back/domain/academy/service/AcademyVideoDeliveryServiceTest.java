package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoDeliveryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademyVideoDeliveryServiceTest {
  @TempDir Path tempDir;

  private AcademyService academyService;
  private AcademyVideoRepository videoRepository;
  private AcademyVideoDeliveryStorage storage;
  private AcademyVideoDeliveryService service;
  private Academy academy;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    videoRepository = mock(AcademyVideoRepository.class);
    storage = mock(AcademyVideoDeliveryStorage.class);
    AcademyVideoUploadProperties properties = new AcademyVideoUploadProperties();
    properties.setDeliverySignedUrlTtlMs(30_000);
    service = new AcademyVideoDeliveryService(
        academyService, videoRepository, storage, properties);
    academy = Academy.builder().slug("published").name("Published").build();
    ReflectionTestUtils.setField(academy, "academyId", 11L);
    when(academyService.getPublishedEntity("published")).thenReturn(academy);
  }

  @Test
  void givenRawWebm_whenFullGet_thenStreamsCanonicalTypeWithoutByteArrayBuffer() throws Exception {
    byte[] bytes = "0123456789".getBytes();
    AcademyVideo video = rawVideo("video/webm", bytes.length);
    Path object = Files.write(tempDir.resolve("raw.webm"), bytes);
    when(storage.resolve(eq("staging/11111111-1111-1111-1111-111111111111/raw"),
        eq("video/webm"), any())).thenAnswer(ignored ->
        new AcademyVideoDeliveryStorage.LocalTarget(
            Files.newByteChannel(object, StandardOpenOption.READ), bytes.length));

    var response = service.get("published", 7L, null);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    response.getBody().writeTo(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("video/webm");
    assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
    assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(body.toByteArray()).isEqualTo(bytes);
  }

  @Test
  void givenSingleRange_whenGet_thenReturns206AndExactSlice() throws Exception {
    byte[] bytes = "0123456789".getBytes();
    rawVideo("video/quicktime", bytes.length);
    Path object = Files.write(tempDir.resolve("raw.mov"), bytes);
    when(storage.resolve(any(), any(), any())).thenAnswer(ignored ->
        new AcademyVideoDeliveryStorage.LocalTarget(
            Files.newByteChannel(object, StandardOpenOption.READ), bytes.length));

    var response = service.get("published", 7L, "bytes=2-5");
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    response.getBody().writeTo(body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
        .isEqualTo("bytes 2-5/10");
    assertThat(body.toString()).isEqualTo("2345");
  }

  @Test
  void givenUnsatisfiableRange_whenGet_thenReturns416WithoutOpeningStorage() throws Exception {
    rawVideo("video/webm", 10);

    var response = service.get("published", 7L, "bytes=10-20");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */10");
    verify(storage, never()).resolve(any(), any(), any());
  }

  @Test
  void givenReadyVideo_whenHead_thenReturnsMetadataWithoutStorageOrRedirect() throws Exception {
    rawVideo("video/webm", 10);

    var response = service.head("published", 7L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("video/webm");
    assertThat(response.getHeaders().getContentLength()).isEqualTo(10);
    assertThat(response.getBody()).isNull();
    verify(storage, never()).resolve(any(), any(), any());
  }

  @Test
  void givenS3Delivery_whenGet_thenReturnsShortLivedNoStoreRedirect() throws Exception {
    rawVideo("video/webm", 10);
    when(storage.resolve(any(), eq("video/webm"), eq(java.time.Duration.ofSeconds(30))))
        .thenReturn(new AcademyVideoDeliveryStorage.RedirectTarget(
            URI.create("https://bucket.example/signed")));

    var response = service.get("published", 7L, "bytes=0-9");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation())
        .isEqualTo(URI.create("https://bucket.example/signed"));
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).isNull();
  }

  @Test
  void givenNormalizedSource_whenHead_thenReturnsMp4Metadata() {
    AcademyVideo video = AcademyVideo.readyRaw(
        academy, "clip.webm", "staging/11111111-1111-1111-1111-111111111111/raw",
        "video/webm", 10, 1_000, 1280, 720);
    assertThat(video.publishNormalized(
        0, "academy-video/attempts/1/1/token/normalized.mp4", 8,
        "academy-video/attempts/1/1/token/thumb.jpg", 1_000, 1280, 720)).isTrue();
    ReflectionTestUtils.setField(video, "videoId", 7L);
    when(videoRepository.findByVideoIdAndAcademy_AcademyId(7L, 11L))
        .thenReturn(Optional.of(video));

    var response = service.head("published", 7L);

    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("video/mp4");
    assertThat(response.getHeaders().getContentLength()).isEqualTo(8);
  }

  private AcademyVideo rawVideo(String mediaType, long size) {
    AcademyVideo video = AcademyVideo.readyRaw(
        academy, "clip", "staging/11111111-1111-1111-1111-111111111111/raw",
        mediaType, size, 1_000, 1280, 720);
    ReflectionTestUtils.setField(video, "videoId", 7L);
    when(videoRepository.findByVideoIdAndAcademy_AcademyId(7L, 11L))
        .thenReturn(Optional.of(video));
    return video;
  }
}
