package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/**
 * ffmpeg/ffprobe 서브프로세스로 업로드 영상을 정규화한다.
 *
 * <p>출력은 H.264/AAC · MP4 · yuv420p로 고정한다. 아이폰 기본 촬영본(HEVC/.mov)이 Chrome·Firefox에서
 * 재생되지 않는 것이 이 컴포넌트의 존재 이유이므로, 컨테이너(.mp4)만이 아니라 코덱까지 바꿔야 한다.
 *
 * <p>변환은 요청 스레드 안에서 동기로 끝난다. 대신 (1) 프로세스 하드 타임아웃, (2) 동시 변환 수 제한을
 * 둬서 느린 입력 하나가 서버 전체를 잡아먹지 않게 한다.
 */
@Slf4j
@Component
public class VideoTranscoder {

  /** 짧은 변 상한. 세로 숏폼이면 720×1280, 가로면 1280×720이 된다. */
  static final int SHORT_SIDE_CAP = 720;
  /** 긴 변 상한. 극단적인 종횡비에서 용량이 튀는 것을 막는다. */
  static final int LONG_SIDE_CAP = 1280;

  private final ProcessRunner processRunner;
  private final String ffmpegPath;
  private final String ffprobePath;
  private final int timeoutSec;
  private final Semaphore transcodeSlots;

  public VideoTranscoder(
      ProcessRunner processRunner,
      @Value("${app.video.ffmpeg-path:ffmpeg}") String ffmpegPath,
      @Value("${app.video.ffprobe-path:ffprobe}") String ffprobePath,
      @Value("${app.video.transcode-timeout-sec:120}") int timeoutSec,
      @Value("${app.video.max-concurrent-transcodes:2}") int maxConcurrentTranscodes) {
    this.processRunner = processRunner;
    this.ffmpegPath = ffmpegPath;
    this.ffprobePath = ffprobePath;
    this.timeoutSec = timeoutSec;
    this.transcodeSlots = new Semaphore(maxConcurrentTranscodes);
  }

  /**
   * 원본의 해상도·코덱·길이를 읽는다. 업로드 제한 재검증은 이 결과로만 판정한다.
   *
   * <p>반환하는 width/height는 <b>화면에 보이는 방향</b> 기준이다. 아이폰 세로 촬영본은 프레임을
   * 1920×1080 가로로 저장하고 rotation=-90 메타데이터를 따로 붙이는 경우가 있는데, ffprobe의
   * stream width/height는 이 회전을 반영하지 않은 저장값이다. 반면 ffmpeg는 디코딩할 때 회전을
   * 자동 적용하므로, 저장값을 그대로 믿고 목표 해상도를 잡으면 세로 영상이 가로로 눌린다.
   */
  public VideoProbe probe(Path input) throws IOException {
    ProcessRunner.ProcessOutput output = processRunner.run(
        List.of(
            ffprobePath,
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries",
            "stream=width,height,codec_name:stream_side_data=rotation:format=duration",
            "-of", "default=noprint_wrappers=1:nokey=0",
            input.toString()),
        timeoutSec);

    if (!output.succeeded()) {
      throw new VideoTranscodeException("영상 정보를 읽을 수 없습니다. 지원하지 않는 형식일 수 있습니다.");
    }

    Map<String, String> fields = parseKeyValues(output.stdout());
    Integer width = parseIntOrNull(fields.get("width"));
    Integer height = parseIntOrNull(fields.get("height"));
    if (width == null || height == null) {
      throw new VideoTranscodeException("영상 트랙을 찾을 수 없습니다.");
    }

    boolean quarterTurned = isQuarterTurn(fields.get("rotation"));
    return new VideoProbe(
        quarterTurned ? height : width,
        quarterTurned ? width : height,
        fields.getOrDefault("codec_name", ""),
        parseDoubleOrZero(fields.get("duration")));
  }

  /** Durable-worker probe using the attempt's remaining monotonic budget. */
  public VideoProbe probe(
      Path input, Duration remaining, BooleanSupplier cancelled) throws IOException {
    ProcessRunner.ProcessOutput output = processRunner.runProgressAware(
        List.of(
            ffprobePath,
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries",
            "stream=width,height,codec_name:stream_side_data=rotation:format=duration",
            "-of", "default=noprint_wrappers=1:nokey=0",
            input.toString()),
        remaining,
        Duration.ZERO,
        cancelled);
    if (!output.succeeded()) {
      throw new VideoTranscodeException("VIDEO_PROBE_FAILED");
    }
    return parsedProbe(output.stdout());
  }

  /** 90°/270°(부호 무관) 회전이면 가로·세로가 뒤바뀐다. 180°는 크기가 그대로다. */
  static boolean isQuarterTurn(String rotation) {
    if (rotation == null) {
      return false;
    }
    try {
      return Math.floorMod(Math.round(Double.parseDouble(rotation.trim())), 180L) == 90L;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * H.264/AAC/MP4로 변환하고 poster 썸네일 1장을 뽑는다.
   *
   * @param workDir 산출물을 둘 디렉터리. 호출자가 정리한다.
   * @throws VideoTranscoderBusyException 동시 변환 상한 초과(대기 없이 즉시 실패)
   */
  public TranscodeResult transcode(Path input, VideoProbe probe, Path workDir) throws IOException {
    if (!transcodeSlots.tryAcquire()) {
      throw new VideoTranscoderBusyException("영상 변환이 혼잡합니다. 잠시 후 다시 시도해 주세요.");
    }
    try {
      int[] target = targetDimensions(probe.width(), probe.height());
      Path mp4 = workDir.resolve("output.mp4");
      Path thumbnail = workDir.resolve("thumbnail.jpg");

      runFfmpeg(List.of(
          ffmpegPath,
          "-y",
          "-i", input.toString(),
          "-vf", "scale=" + target[0] + ":" + target[1],
          "-c:v", "libx264",
          "-preset", "veryfast",
          "-crf", "26",
          "-pix_fmt", "yuv420p",
          "-c:a", "aac",
          "-b:a", "128k",
          "-movflags", "+faststart",
          "-map_metadata", "-1",
          mp4.toString()), "영상 변환에 실패했습니다.");

      if (!Files.exists(mp4) || Files.size(mp4) == 0) {
        throw new VideoTranscodeException("영상 변환 결과가 비어 있습니다.");
      }

      runFfmpeg(List.of(
          ffmpegPath,
          "-y",
          "-ss", thumbnailSeekSeconds(probe.durationSec()),
          "-i", mp4.toString(),
          "-frames:v", "1",
          "-q:v", "3",
          thumbnail.toString()), "썸네일 추출에 실패했습니다.");

      if (!Files.exists(thumbnail) || Files.size(thumbnail) == 0) {
        throw new VideoTranscodeException("썸네일 추출 결과가 비어 있습니다.");
      }

      return new TranscodeResult(
          mp4,
          thumbnail,
          target[0],
          target[1],
          (int) Math.round(probe.durationSec()),
          Files.size(mp4));
    } finally {
      transcodeSlots.release();
    }
  }

  /**
   * Worker-owned conversion. Concurrency is deliberately not managed here: the worker acquires
   * its one local slot before it claims a database lease.
   */
  public TranscodeResult transcodeAttempt(
      Path input,
      VideoProbe probe,
      Path workDir,
      java.util.function.Supplier<Duration> remaining,
      BooleanSupplier cancelled,
      Runnable mediaProgressObserved) throws IOException {
    int[] target = targetDimensions(probe.width(), probe.height());
    Path mp4 = workDir.resolve("output.mp4");
    Path thumbnail = workDir.resolve("thumbnail.jpg");

    runProgressFfmpeg(List.of(
        ffmpegPath,
        "-y",
        "-nostats",
        "-progress", ProcessRunner.PROGRESS_FILE_TOKEN,
        "-i", input.toString(),
        "-vf", "scale=" + target[0] + ":" + target[1],
        "-c:v", "libx264",
        "-preset", "veryfast",
        "-crf", "26",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "128k",
        "-movflags", "+faststart",
        "-map_metadata", "-1",
        mp4.toString()), remaining.get(), cancelled, mediaProgressObserved,
        "VIDEO_TRANSCODE_FAILED");
    if (!Files.exists(mp4) || Files.size(mp4) == 0) {
      throw new VideoTranscodeException("VIDEO_OUTPUT_EMPTY");
    }

    runProgressFfmpeg(List.of(
        ffmpegPath,
        "-y",
        "-nostats",
        "-progress", ProcessRunner.PROGRESS_FILE_TOKEN,
        "-ss", thumbnailSeekSeconds(probe.durationSec()),
        "-i", mp4.toString(),
        "-frames:v", "1",
        "-q:v", "3",
        thumbnail.toString()), remaining.get(), cancelled, mediaProgressObserved,
        "VIDEO_THUMBNAIL_FAILED");
    if (!Files.exists(thumbnail) || Files.size(thumbnail) == 0) {
      throw new VideoTranscodeException("VIDEO_THUMBNAIL_EMPTY");
    }
    return new TranscodeResult(
        mp4, thumbnail, target[0], target[1],
        (int) Math.round(probe.durationSec()), Files.size(mp4));
  }

  /**
   * ImageIO가 못 읽는 정지 이미지(HEIC/AVIF 등)를 PNG로 디코딩한다.
   *
   * <p>영상 변환 슬롯을 쓰지 않는다. {@code transcodeSlots}는 대기 없이 즉시 실패하고 최대
   * {@code transcode-timeout-sec}까지 붙들려 있어서, 공유하면 영상 두 건이 도는 동안 모든 이미지
   * 업로드가 503이 된다. 이미지 쪽 동시성은 호출자가 따로 제한한다.
   *
   * @param maxDimension 최장변 캡. 이 경로는 디코딩 전에 픽셀 수를 알 수 없어(ImageIO가 못 읽는
   *     포맷이다) 여기서 상한을 걸지 않으면 폰 원본이 그대로 펴져 힙을 먹는다. 최종 축소·EXIF·품질
   *     처리는 호출자의 sanitizer가 담당하고, 이 값은 안전 천장일 뿐이다.
   */
  public void decodeStillImage(Path input, Path output, int maxDimension) throws IOException {
    runFfmpeg(List.of(
        ffmpegPath,
        "-y",
        "-i", input.toString(),
        "-vf", "scale='min(" + maxDimension + ",iw)':'min(" + maxDimension + ",ih)'"
            + ":force_original_aspect_ratio=decrease",
        "-frames:v", "1",
        output.toString()), "이미지를 변환할 수 없습니다.");

    if (!Files.exists(output) || Files.size(output) == 0) {
      throw new VideoTranscodeException("이미지 변환 결과가 비어 있습니다.");
    }
  }

  private void runFfmpeg(List<String> command, String failureMessage) throws IOException {
    ProcessRunner.ProcessOutput output = processRunner.run(command, timeoutSec);
    if (!output.succeeded()) {
      log.warn("ffmpeg 실패 (exit {}): {}", output.exitCode(), output.stderr());
      throw new VideoTranscodeException(failureMessage);
    }
  }

  private void runProgressFfmpeg(
      List<String> command,
      Duration remaining,
      BooleanSupplier cancelled,
      Runnable mediaProgressObserved,
      String failureCode) throws IOException {
    ProcessRunner.ProcessOutput output = processRunner.runProgressAware(
        command, remaining, Duration.ofMillis(90_000), cancelled, mediaProgressObserved);
    if (!output.succeeded()) {
      log.warn("academy video ffmpeg failed with exit {}", output.exitCode());
      throw new VideoTranscodeException(failureCode);
    }
  }

  private static VideoProbe parsedProbe(String output) {
    Map<String, String> fields = parseKeyValues(output);
    Integer width = parseIntOrNull(fields.get("width"));
    Integer height = parseIntOrNull(fields.get("height"));
    if (width == null || height == null) {
      throw new VideoTranscodeException("VIDEO_TRACK_MISSING");
    }
    boolean quarterTurned = isQuarterTurn(fields.get("rotation"));
    return new VideoProbe(
        quarterTurned ? height : width,
        quarterTurned ? width : height,
        fields.getOrDefault("codec_name", ""),
        parseDoubleOrZero(fields.get("duration")));
  }

  /**
   * 비율을 유지하며 짧은 변을 720, 긴 변을 1280으로 캡한다. 원본이 이미 작으면 확대하지 않는다.
   *
   * <p>세로 숏폼을 1280×720 박스에 맞추면 405×720처럼 필요 이상으로 작아지므로, 박스가 아니라
   * 짧은 변을 기준으로 잡는다.
   */
  static int[] targetDimensions(int width, int height) {
    int shorter = Math.min(width, height);
    int longer = Math.max(width, height);
    double scale = 1.0;
    if (shorter > SHORT_SIDE_CAP) {
      scale = (double) SHORT_SIDE_CAP / shorter;
    }
    if (longer * scale > LONG_SIDE_CAP) {
      scale = (double) LONG_SIDE_CAP / longer;
    }
    return new int[] {toEven(width * scale), toEven(height * scale)};
  }

  /** libx264 yuv420p는 짝수 해상도만 받는다. */
  private static int toEven(double value) {
    int rounded = (int) Math.round(value);
    if (rounded < 2) {
      return 2;
    }
    return rounded % 2 == 0 ? rounded : rounded - 1;
  }

  /** 1초 지점을 쓰되, 그보다 짧은 영상이면 중간 지점을 쓴다(빈 프레임 방지). */
  static String thumbnailSeekSeconds(double durationSec) {
    double seek = durationSec > 0 && durationSec < 2 ? durationSec / 2 : 1.0;
    return String.format(Locale.ROOT, "%.3f", seek);
  }

  private static Map<String, String> parseKeyValues(String output) {
    Map<String, String> fields = new HashMap<>();
    for (String line : output.split("\\R")) {
      int separator = line.indexOf('=');
      if (separator > 0) {
        fields.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
      }
    }
    return fields;
  }

  private static Integer parseIntOrNull(String value) {
    try {
      return value == null ? null : Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static double parseDoubleOrZero(String value) {
    try {
      return value == null ? 0d : Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0d;
    }
  }
}
