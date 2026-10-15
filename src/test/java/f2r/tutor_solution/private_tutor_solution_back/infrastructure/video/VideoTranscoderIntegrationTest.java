package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 ffmpeg 바이너리로 변환을 검증한다.
 *
 * <p>dev/stg의 일반 bootRun 게이트에는 ffmpeg가 없을 수 있으므로 단위 테스트와 분리했다.
 * 바이너리가 없으면 실패가 아니라 skip한다 — 없는 환경에서 빌드를 깨뜨리면 안 된다.
 *
 * <p>브라우저 재생 자체는 자동화할 수 없어서, 그 대리 검증으로 산출물 코덱이 h264/aac인지를 본다.
 * 아이폰 기본 촬영본(HEVC)이 크롬·파이어폭스에서 재생되지 않는 것이 이 기능의 출발점이기 때문이다.
 */
@DisplayName("VideoTranscoder 통합 — 실제 ffmpeg 변환")
class VideoTranscoderIntegrationTest {

  private static final DefaultProcessRunner RUNNER = new DefaultProcessRunner();

  @BeforeAll
  static void requireFfmpeg() {
    Assumptions.assumeTrue(binaryAvailable("ffmpeg") && binaryAvailable("ffprobe"),
        "ffmpeg/ffprobe가 없어 통합 테스트를 건너뜁니다.");
  }

  private static boolean binaryAvailable(String binary) {
    try {
      return RUNNER.run(List.of(binary, "-version"), 20).succeeded();
    } catch (RuntimeException | IOException e) {
      return false;
    }
  }

  private VideoTranscoder transcoder() {
    return new VideoTranscoder(RUNNER, "ffmpeg", "ffprobe", 120, 2);
  }

  /** 지정한 코덱으로 세로(1080x1920) 샘플 영상을 만든다. 해당 인코더가 없으면 null. */
  private Path createPortraitSample(Path dir, String videoEncoder, String extension) throws IOException {
    Path sample = dir.resolve("sample." + extension);
    boolean created = RUNNER.run(List.of(
        "ffmpeg", "-y",
        "-f", "lavfi", "-i", "testsrc=size=1080x1920:rate=30:duration=3",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
        "-c:v", videoEncoder,
        "-c:a", "aac",
        "-t", "3",
        sample.toString()), 120).succeeded();
    return created && Files.exists(sample) ? sample : null;
  }

  private String probeField(Path file, String stream, String entry) throws IOException {
    ProcessRunner.ProcessOutput output = RUNNER.run(List.of(
        "ffprobe", "-v", "error",
        "-select_streams", stream,
        "-show_entries", "stream=" + entry,
        "-of", "default=noprint_wrappers=1:nokey=1",
        file.toString()), 30);
    return output.stdout().trim();
  }

  @Test
  @DisplayName("given 아이폰류 HEVC 세로 영상 when 변환 then h264/aac/yuv420p 720x1280 mp4가 나온다")
  void transcodes_hevc_portrait_to_h264(@TempDir Path dir) throws IOException {
    // given — 아이폰 기본 촬영 포맷(HEVC)을 흉내낸 샘플
    Path sample = createPortraitSample(dir, "hevc_videotoolbox", "mov");
    if (sample == null) {
      sample = createPortraitSample(dir, "libx265", "mov");
    }
    Assumptions.assumeTrue(sample != null, "HEVC 인코더가 없어 이 케이스를 건너뜁니다.");

    // when
    VideoProbe probe = transcoder().probe(sample);
    TranscodeResult result = transcoder().transcode(sample, probe, dir);

    // then — 컨테이너만 mp4로 바꾸는 것으로는 부족하고 코덱까지 h264여야 한다
    assertThat(probe.videoCodec()).isEqualTo("hevc");
    assertThat(probeField(result.mp4(), "v:0", "codec_name")).isEqualTo("h264");
    assertThat(probeField(result.mp4(), "a:0", "codec_name")).isEqualTo("aac");
    assertThat(probeField(result.mp4(), "v:0", "pix_fmt")).isEqualTo("yuv420p");
    assertThat(result.width()).isEqualTo(720);
    assertThat(result.height()).isEqualTo(1280);
    assertThat(result.sizeBytes()).isPositive();
  }

  @Test
  @DisplayName("given 변환된 영상 when 썸네일 추출 then 비어 있지 않은 JPEG 파일이 생긴다")
  void extracts_non_empty_thumbnail(@TempDir Path dir) throws IOException {
    // given
    Path sample = createPortraitSample(dir, "libx264", "mp4");
    Assumptions.assumeTrue(sample != null, "샘플 생성에 실패해 건너뜁니다.");

    // when
    VideoProbe probe = transcoder().probe(sample);
    TranscodeResult result = transcoder().transcode(sample, probe, dir);

    // then
    assertThat(result.thumbnail()).exists();
    assertThat(Files.size(result.thumbnail())).isPositive();
    assertThat(probeField(result.thumbnail(), "v:0", "codec_name")).isEqualTo("mjpeg");
  }

  @Test
  @DisplayName("given 영상이 아닌 파일 when probe then VideoTranscodeException으로 거부한다")
  void rejects_non_video_file(@TempDir Path dir) throws IOException {
    // given
    Path notVideo = dir.resolve("not-a-video.mp4");
    Files.writeString(notVideo, "this is definitely not a video");

    // when / then
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> transcoder().probe(notVideo))
        .isInstanceOf(VideoTranscodeException.class);
  }
}
