package f2r.tutor_solution.private_tutor_solution_back.domain.landing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VideoUploadRejectedException;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoSourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlot;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.repository.LandingVideoSlotRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.StorageReadException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.TranscodeResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoProbe;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("LandingVideoService")
@ExtendWith(OutputCaptureExtension.class)
class LandingVideoServiceTest {

  private LandingVideoSlotRepository repository;
  private LandingVideoSlotTransaction transactions;
  private FileStorage storage;
  private VideoTranscoder transcoder;
  private LandingVideoService service;

  @BeforeEach
  void setUp() {
    repository = mock(LandingVideoSlotRepository.class);
    transactions = mock(LandingVideoSlotTransaction.class);
    storage = mock(FileStorage.class);
    transcoder = mock(VideoTranscoder.class);
    service =
        new LandingVideoService(
            repository, transactions, storage, transcoder,
            60, 100L * 1024 * 1024, 30L * 1024 * 1024, 2L * 1024 * 1024, 3);
  }

  @Test
  @DisplayName("given row 없음 when 관리자 목록/랜딩 해석 then 고정 순서 번들 3개")
  void missingRowsResolveToFixedBundledSlots() {
    // given
    when(repository.findAll()).thenReturn(List.of());

    // when
    var admin = service.listAdmin();
    var render = service.resolveRenderItems();

    // then
    assertThat(admin).extracting(response -> response.slot())
        .containsExactly(LandingVideoSlotKey.values());
    assertThat(admin).allMatch(response -> response.sourceType() == LandingVideoSourceType.BUNDLED);
    assertThat(render).allMatch(item -> item.visible() && item.sourceUrl().startsWith("/video/"));
  }

  @Test
  @DisplayName("given hidden managed row when 랜딩 해석/public read then media 제거/404")
  void hiddenManagedAssetIsNotPublic() throws Exception {
    // given
    LandingVideoSlot hidden = managed(LandingVideoSlotKey.HERO_BACKGROUND, false, "secret-key", 3L);
    when(repository.findAll()).thenReturn(List.of(hidden));
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND)).thenReturn(Optional.of(hidden));

    // when / then
    assertThat(service.resolveRenderItems().get(0).sourceUrl()).isNull();
    assertThatThrownBy(() -> service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error ->
            assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    verify(storage, never()).readSingleLimited(anyString(), anyString(), anyLong());
  }

  @Test
  @DisplayName("given 3개 응답 body 미완료 when 4번째 읽기 then 503; body 완료/실패 모두 permit 반환")
  void permitsLiveUntilResponseBodyFinishes() throws Exception {
    // given
    LandingVideoSlot entity = managed(LandingVideoSlotKey.HERO_BACKGROUND, true, "key", 3L);
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(entity));
    when(storage.readSingleLimited("key", "VIDEO", 30L * 1024 * 1024))
        .thenReturn(new byte[] {1, 2, 3});

    // when
    var first = service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND);
    var second = service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND);
    var third = service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND);

    // then
    assertThatThrownBy(() -> service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error ->
            assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));

    first.body().writeTo(new ByteArrayOutputStream());
    var reused = service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND);

    assertThatThrownBy(
            () ->
                second.body().writeTo(
                    new OutputStream() {
                      @Override
                      public void write(int value) throws IOException {
                        throw new IOException("client disconnected");
                      }

                      @Override
                      public void write(byte[] bytes, int off, int len) throws IOException {
                        throw new IOException("client disconnected");
                      }
                    }))
        .isInstanceOf(IOException.class);
    var reusedAfterFailure = service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND);

    third.close();
    reused.close();
    reusedAfterFailure.close();
  }

  @Test
  @DisplayName("given old key read race when key 변경 then 1회 최신 key 재시도")
  void retriesOnceOnlyWhenKeyChanged() throws Exception {
    // given
    LandingVideoSlot old = managed(LandingVideoSlotKey.HERO_BACKGROUND, true, "old-key", 3L);
    LandingVideoSlot latest = managed(LandingVideoSlotKey.HERO_BACKGROUND, true, "new-key", 3L);
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(old))
        .thenReturn(Optional.of(latest));
    when(storage.readSingleLimited("old-key", "VIDEO", 30L * 1024 * 1024))
        .thenThrow(new StorageReadException());
    when(storage.readSingleLimited("new-key", "VIDEO", 30L * 1024 * 1024))
        .thenReturn(new byte[] {4, 5, 6});

    // when
    var media = service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    media.body().writeTo(output);

    // then
    assertThat(output.toByteArray()).containsExactly(4, 5, 6);
    verify(storage).readSingleLimited("old-key", "VIDEO", 30L * 1024 * 1024);
    verify(storage).readSingleLimited("new-key", "VIDEO", 30L * 1024 * 1024);
  }

  @Test
  @DisplayName("given old key read failure when 최신 key 동일 then storage 재시도 없이 503")
  void doesNotRetryStorageReadWhenKeyIsUnchanged() throws Exception {
    // given
    LandingVideoSlot entity = managed(LandingVideoSlotKey.HERO_BACKGROUND, true, "same-key", 3L);
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(entity))
        .thenReturn(Optional.of(entity));
    when(storage.readSingleLimited("same-key", "VIDEO", 30L * 1024 * 1024))
        .thenThrow(new IOException("read failed"));

    // when / then
    assertThatThrownBy(() -> service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error ->
            assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));
    verify(repository, times(2)).findById(LandingVideoSlotKey.HERO_BACKGROUND);
    verify(storage).readSingleLimited("same-key", "VIDEO", 30L * 1024 * 1024);
  }

  @Test
  @DisplayName("given same key normalized storage failure when public read then key-free 503/log and permit release")
  void sameKeyNormalizedFailureIsSanitizedAndReleasesPermit(CapturedOutput output)
      throws Exception {
    // given
    String secretKey = "prd/private/landing/secret-video.mp4";
    LandingVideoSlot entity =
        managed(LandingVideoSlotKey.HERO_BACKGROUND, true, secretKey, 3L);
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(entity));
    when(storage.readSingleLimited(secretKey, "VIDEO", 30L * 1024 * 1024))
        .thenThrow(new StorageReadException())
        .thenReturn(new byte[] {1, 2, 3});
    service =
        new LandingVideoService(
            repository, transactions, storage, transcoder,
            60, 100L * 1024 * 1024, 30L * 1024 * 1024, 2L * 1024 * 1024, 1);

    // when / then
    assertThatThrownBy(() -> service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException response = (ResponseStatusException) error;
          assertThat(response.getStatusCode().value()).isEqualTo(503);
          assertThat(response.getReason()).doesNotContain(secretKey);
        });

    var media = service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND);
    media.close();
    assertThat(output.getOut()).doesNotContain(secretKey);
    assertThat(output.getErr()).doesNotContain(secretKey);
  }

  @Test
  @DisplayName("given old key failure and latest hidden when public retry then visibility 재검사 후 404")
  void retryRechecksLatestPublicVisibility() throws Exception {
    // given
    LandingVideoSlot old = managed(LandingVideoSlotKey.HERO_BACKGROUND, true, "old-key", 3L);
    LandingVideoSlot hidden =
        managed(LandingVideoSlotKey.HERO_BACKGROUND, false, "new-key", 3L);
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(old))
        .thenReturn(Optional.of(hidden));
    when(storage.readSingleLimited("old-key", "VIDEO", 30L * 1024 * 1024))
        .thenThrow(new StorageReadException());

    // when / then
    assertThatThrownBy(() -> service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error ->
            assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    verify(storage, never()).readSingleLimited("new-key", "VIDEO", 30L * 1024 * 1024);
  }

  @Test
  @DisplayName("given persisted size 상한 초과 when stream then storage 전 503")
  void rejectsPersistedOversizeBeforeStorageRead() throws Exception {
    // given
    LandingVideoSlot entity =
        managed(
            LandingVideoSlotKey.HERO_BACKGROUND,
            true,
            "must-not-be-logged-or-read",
            30L * 1024 * 1024 + 1);
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(entity));

    // when / then
    assertThatThrownBy(() -> service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error ->
            assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));
    verify(storage, never()).readSingleLimited(anyString(), anyString(), anyLong());
  }

  @Test
  @DisplayName("given 실제 bytes와 persisted size 불일치 when stream then 503")
  void rejectsActualSizeMismatch() throws Exception {
    // given
    LandingVideoSlot entity =
        managed(LandingVideoSlotKey.HERO_BACKGROUND, true, "opaque-key", 4L);
    when(repository.findById(LandingVideoSlotKey.HERO_BACKGROUND))
        .thenReturn(Optional.of(entity));
    when(storage.readSingleLimited("opaque-key", "VIDEO", 30L * 1024 * 1024))
        .thenReturn(new byte[] {1, 2, 3});

    // when / then
    assertThatThrownBy(() -> service.readPublicVideo(LandingVideoSlotKey.HERO_BACKGROUND))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error ->
            assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));
  }

  @Test
  @DisplayName("given 정상 업로드 when 저장/swap then principal userId 사용 후 구 key 정리")
  void uploadUsesPrincipalAndCleansOldAfterSwap() throws Exception {
    // given
    var principal = new JwtTokenProvider.AuthenticatedPrincipal(77L, "admin", "ADMIN");
    var file = new MockMultipartFile("video", "clip.mov", "video/quicktime", "input".getBytes());
    when(transcoder.probe(any()))
        .thenReturn(
            new VideoProbe(1080, 1920, "hevc", 12),
            new VideoProbe(704, 1260, "h264", 11.6));
    when(transcoder.transcode(any(), any(), any()))
        .thenAnswer(invocation -> {
          java.nio.file.Path workDir = invocation.getArgument(2);
          java.nio.file.Path mp4 = workDir.resolve("output.mp4");
          java.nio.file.Path thumbnail = workDir.resolve("thumbnail.jpg");
          Files.write(mp4, new byte[] {1, 2, 3});
          Files.write(thumbnail, new byte[] {9});
          return new TranscodeResult(mp4, thumbnail, 720, 1280, 12, 3);
        });
    when(storage.saveRaw(eq(77L), eq("landing-video.mp4"), any(), eq(3L)))
        .thenReturn("new-video");
    when(storage.saveImage(eq(77L), eq("landing-video-thumbnail"), any()))
        .thenReturn("new-thumb");
    LandingVideoSlotState previous =
        new LandingVideoSlotState(
            LandingVideoSlotKey.HERO_BACKGROUND,
            "old.mp4",
            "old-video",
            "old-thumb",
            3,
            3L,
            10,
            10,
            false);
    LandingVideoSlotState current =
        new LandingVideoSlotState(
            LandingVideoSlotKey.HERO_BACKGROUND,
            "clip.mov",
            "new-video",
            "new-thumb",
            12,
            3L,
            704,
            1260,
            false);
    when(transactions.replace(
            eq(LandingVideoSlotKey.HERO_BACKGROUND),
            eq("clip.mov"),
            eq("new-video"),
            eq("new-thumb"),
            eq(12),
            eq(3L),
            eq(704),
            eq(1260)))
        .thenReturn(new LandingVideoSlotTransaction.Replacement(previous, current));
    when(storage.deleteReported(anyString(), anyString())).thenReturn(true);

    // when
    var response = service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file);

    // then
    assertThat(response.visible()).isFalse();
    InOrder order = inOrder(transactions, storage);
    order.verify(transactions).replace(
        eq(LandingVideoSlotKey.HERO_BACKGROUND),
        eq("clip.mov"),
        eq("new-video"),
        eq("new-thumb"),
        eq(12),
        eq(3L),
        eq(704),
        eq(1260));
    order.verify(storage).deleteReported("old-video", "VIDEO");
    order.verify(storage).deleteReported("old-thumb", "IMAGE");
  }

  @Test
  @DisplayName("given thumbnail 저장 실패 when upload then 신규 mp4만 보상 삭제")
  void thumbnailSaveFailureCompensatesNewVideo() throws Exception {
    // given
    var principal = new JwtTokenProvider.AuthenticatedPrincipal(77L, "admin", "ADMIN");
    var file = new MockMultipartFile("video", "clip.mp4", "video/mp4", "input".getBytes());
    when(transcoder.probe(any())).thenReturn(new VideoProbe(640, 480, "h264", 3));
    when(transcoder.transcode(any(), any(), any()))
        .thenAnswer(invocation -> {
          java.nio.file.Path workDir = invocation.getArgument(2);
          java.nio.file.Path mp4 = workDir.resolve("output.mp4");
          java.nio.file.Path thumbnail = workDir.resolve("thumbnail.jpg");
          Files.write(mp4, new byte[] {1});
          Files.write(thumbnail, new byte[] {2});
          return new TranscodeResult(mp4, thumbnail, 640, 480, 3, 1);
        });
    when(storage.saveRaw(eq(77L), anyString(), any(), eq(1L))).thenReturn("new-video");
    when(storage.saveImage(eq(77L), anyString(), any())).thenThrow(new IOException("fail"));
    when(storage.deleteReported("new-video", "VIDEO")).thenReturn(true);
    when(storage.deleteReported(null, "IMAGE")).thenReturn(true);

    // when / then
    assertThatThrownBy(
            () -> service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file))
        .isInstanceOf(ResponseStatusException.class);
    verify(storage).deleteReported("new-video", "VIDEO");
    verify(transactions, never()).replace(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("given 빈 파일 when upload then 저장하지 않는다")
  void emptyUploadStopsBeforeStorage() throws Exception {
    // given
    var principal = new JwtTokenProvider.AuthenticatedPrincipal(77L, "admin", "ADMIN");
    var empty = new MockMultipartFile("video", "clip.mp4", "video/mp4", new byte[0]);

    // when / then
    assertThatThrownBy(
            () -> service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, empty))
        .isInstanceOf(VideoUploadRejectedException.class);
    verify(storage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given 정확히 60.0초 영상 when upload then 저장하지 않는다")
  void exactDurationLimitStopsBeforeStorage() throws Exception {
    // given
    var principal = principal();
    var file = videoFile();
    when(transcoder.probe(any())).thenReturn(new VideoProbe(640, 480, "h264", 60.0));

    // when / then
    assertThatThrownBy(
            () -> service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("60초 미만");
    verify(transcoder, never()).transcode(any(), any(), any());
    verify(storage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given N/A·0·negative·NaN·infinite input duration when upload then transcode/storage 차단")
  void invalidInputDurationsStopBeforeTranscode() throws Exception {
    // given
    var principal = principal();
    double[] invalidDurations = {
        0.0, // ffprobe의 N/A/비숫자 duration이 현재 probe 경계에서 표현되는 값
        -1.0,
        Double.NaN,
        Double.POSITIVE_INFINITY
    };

    for (double invalidDuration : invalidDurations) {
      when(transcoder.probe(any()))
          .thenReturn(new VideoProbe(640, 480, "h264", invalidDuration));

      // when / then
      assertThatThrownBy(
              () ->
                  service.upload(
                      LandingVideoSlotKey.HERO_BACKGROUND, principal, videoFile()))
          .isInstanceOf(VideoUploadRejectedException.class)
          .hasMessageContaining("0초보다 크고 60초 미만");
    }
    verify(transcoder, never()).transcode(any(), any(), any());
    verify(storage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given input <60 but normalized mp4 >=60 when upload then storage 전 거부")
  void normalizedOutputDurationIsRevalidatedBeforeStorage() throws Exception {
    // given
    var principal = principal();
    when(transcoder.probe(any()))
        .thenReturn(
            new VideoProbe(640, 480, "h264", 10.0),
            new VideoProbe(640, 480, "h264", 60.0));
    when(transcoder.transcode(any(), any(), any()))
        .thenAnswer(invocation -> {
          Path workDir = invocation.getArgument(2);
          Path mp4 = workDir.resolve("output.mp4");
          Path thumbnail = workDir.resolve("thumbnail.jpg");
          Files.write(mp4, new byte[] {1, 2, 3});
          Files.write(thumbnail, new byte[] {4});
          return new TranscodeResult(mp4, thumbnail, 640, 480, 10, 3);
        });

    // when / then
    assertThatThrownBy(
            () ->
                service.upload(
                    LandingVideoSlotKey.HERO_BACKGROUND, principal, videoFile()))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("60초 미만");
    verify(storage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given 미지원 codec 영상 when upload then 저장하지 않는다")
  void unsupportedCodecStopsBeforeStorage() throws Exception {
    // given
    var principal = principal();
    var file = videoFile();
    when(transcoder.probe(any())).thenReturn(new VideoProbe(640, 480, "theora", 10.0));

    // when / then
    assertThatThrownBy(
            () -> service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("지원하지 않는 영상 형식");
    verify(transcoder, never()).transcode(any(), any(), any());
    verify(storage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given 변환 mp4가 30MiB 초과 when upload then 저장하지 않는다")
  void oversizedTranscodedVideoStopsBeforeStorage() throws Exception {
    // given
    var principal = principal();
    var file = videoFile();
    stubTranscode(30L * 1024 * 1024 + 1, 1);

    // when / then
    assertThatThrownBy(
            () -> service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("변환된 영상이 너무 큽니다");
    verify(storage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given thumbnail이 2MiB 초과 when upload then 저장하지 않는다")
  void oversizedThumbnailStopsBeforeStorage() throws Exception {
    // given
    var principal = principal();
    var file = videoFile();
    stubTranscode(1, 2L * 1024 * 1024 + 1);

    // when / then
    assertThatThrownBy(
            () -> service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file))
        .isInstanceOf(VideoUploadRejectedException.class)
        .hasMessageContaining("썸네일이 너무 큽니다");
    verify(storage, never()).saveRaw(anyLong(), anyString(), any(), anyLong());
  }

  @Test
  @DisplayName("given seeded row insert race when upload then fresh transaction으로 복구")
  void insertRaceRecoversInFreshTransaction() throws Exception {
    // given
    var principal = principal();
    var file = videoFile();
    stubTranscode(3, 1);
    when(storage.saveRaw(eq(77L), eq("landing-video.mp4"), any(), eq(3L)))
        .thenReturn("new-video");
    when(storage.saveImage(eq(77L), eq("landing-video-thumbnail"), any()))
        .thenReturn("new-thumb");
    when(transactions.replace(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("insert race"));
    LandingVideoSlotState previous =
        new LandingVideoSlotState(
            LandingVideoSlotKey.HERO_BACKGROUND,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true);
    LandingVideoSlotState current =
        new LandingVideoSlotState(
            LandingVideoSlotKey.HERO_BACKGROUND,
            "clip.mp4",
            "new-video",
            "new-thumb",
            10,
            3L,
            640,
            480,
            true);
    when(transactions.replaceAfterInsertRace(
            eq(LandingVideoSlotKey.HERO_BACKGROUND),
            eq("clip.mp4"),
            eq("new-video"),
            eq("new-thumb"),
            eq(10),
            eq(3L),
            eq(640),
            eq(480)))
        .thenReturn(new LandingVideoSlotTransaction.Replacement(previous, current));
    when(storage.deleteReported(any(), anyString())).thenReturn(true);

    // when
    var response = service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file);

    // then
    assertThat(response.previewUrl())
        .isEqualTo("/api/admin/landing/videos/HERO_BACKGROUND/stream");
    verify(transactions).replaceAfterInsertRace(
        LandingVideoSlotKey.HERO_BACKGROUND,
        "clip.mp4",
        "new-video",
        "new-thumb",
        10,
        3L,
        640,
        480);
  }

  @Test
  @DisplayName("given insert race 복구 row도 없음 when upload then 신규 asset을 보상 삭제하고 실패")
  void unrecoverableInsertRaceCompensatesNewAssets() throws Exception {
    // given
    var principal = principal();
    var file = videoFile();
    stubTranscode(3, 1);
    when(storage.saveRaw(eq(77L), eq("landing-video.mp4"), any(), eq(3L)))
        .thenReturn("new-video");
    when(storage.saveImage(eq(77L), eq("landing-video-thumbnail"), any()))
        .thenReturn("new-thumb");
    when(transactions.replace(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("insert race"));
    when(transactions.replaceAfterInsertRace(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("row still missing"));
    when(storage.deleteReported(anyString(), anyString())).thenReturn(true);

    // when / then
    assertThatThrownBy(
            () -> service.upload(LandingVideoSlotKey.HERO_BACKGROUND, principal, file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("row still missing");
    verify(storage).deleteReported("new-video", "VIDEO");
    verify(storage).deleteReported("new-thumb", "IMAGE");
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal() {
    return new JwtTokenProvider.AuthenticatedPrincipal(77L, "admin", "ADMIN");
  }

  private MockMultipartFile videoFile() {
    return new MockMultipartFile("video", "clip.mp4", "video/mp4", "input".getBytes());
  }

  private void stubTranscode(long videoBytes, long thumbnailBytes) throws Exception {
    when(transcoder.probe(any()))
        .thenReturn(
            new VideoProbe(640, 480, "h264", 10.0),
            new VideoProbe(640, 480, "h264", 10.0));
    when(transcoder.transcode(any(), any(), any()))
        .thenAnswer(invocation -> {
          Path workDir = invocation.getArgument(2);
          Path mp4 = workDir.resolve("output.mp4");
          Path thumbnail = workDir.resolve("thumbnail.jpg");
          writeSizedFile(mp4, videoBytes);
          writeSizedFile(thumbnail, thumbnailBytes);
          return new TranscodeResult(mp4, thumbnail, 640, 480, 10, videoBytes);
        });
  }

  private void writeSizedFile(Path path, long size) throws IOException {
    try (var channel =
        Files.newByteChannel(
            path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      if (size > 0) {
        channel.position(size - 1);
        channel.write(ByteBuffer.wrap(new byte[] {0}));
      }
    }
  }

  private LandingVideoSlot managed(
      LandingVideoSlotKey slot, boolean visible, String key, long size) {
    LandingVideoSlot entity = LandingVideoSlot.bundled(slot, visible);
    entity.replaceAsset("video.mp4", key, "thumb-key", 3, size, 640, 480);
    return entity;
  }
}
