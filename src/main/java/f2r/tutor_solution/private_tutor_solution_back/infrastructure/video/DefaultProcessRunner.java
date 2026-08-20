package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * ProcessBuilder 기반 {@link ProcessRunner}.
 *
 * <p>stdout/stderr을 임시 파일로 리다이렉트한다. 파이프로 받으면 ffmpeg의 progress 로그가 버퍼를 채웠을
 * 때 자식 프로세스가 write에서 멈춰 교착이 생기는데, 파일 리다이렉트는 그 위험이 없고 별도 소비 스레드도
 * 필요 없다.
 */
@Slf4j
@Component
public class DefaultProcessRunner implements ProcessRunner {
  private static final long POLL_MILLIS = 50;
  private final LongSupplier nanoTime;

  public DefaultProcessRunner() {
    this(System::nanoTime);
  }

  DefaultProcessRunner(LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
  }

  @Override
  public ProcessOutput run(List<String> command, int timeoutSec) throws IOException {
    return runProgressAware(
        command, Duration.ofSeconds(timeoutSec), Duration.ZERO, () -> false);
  }

  @Override
  public ProcessOutput runProgressAware(
      List<String> command,
      Duration remaining,
      Duration mediaProgressStall,
      BooleanSupplier cancelled) throws IOException {
    return runProgressAware(
        command, remaining, mediaProgressStall, cancelled, () -> {});
  }

  @Override
  public ProcessOutput runProgressAware(
      List<String> command,
      Duration remaining,
      Duration mediaProgressStall,
      BooleanSupplier cancelled,
      Runnable mediaProgressObserved) throws IOException {
    Path stdout = Files.createTempFile("ffproc-out-", ".log");
    Path stderr = Files.createTempFile("ffproc-err-", ".log");
    Path progress = command.contains(PROGRESS_FILE_TOKEN)
        ? Files.createTempFile("ffproc-progress-", ".log")
        : null;
    List<String> actualCommand = replaceProgressToken(command, progress);
    Process process;
    try {
      process = new ProcessBuilder(actualCommand)
          .redirectOutput(stdout.toFile())
          .redirectError(stderr.toFile())
          .start();
    } catch (IOException e) {
      Files.deleteIfExists(stdout);
      Files.deleteIfExists(stderr);
      if (progress != null) Files.deleteIfExists(progress);
      throw new VideoToolUnavailableException(
          "영상 처리 도구를 실행할 수 없습니다: " + command.get(0), e);
    }

    long started = nanoTime.getAsLong();
    long deadline = saturatingAdd(started, remaining.toNanos());
    long lastMediaProgress = started;
    long lastMediaTimestamp = 0;
    try {
      while (true) {
        long now = nanoTime.getAsLong();
        if (now >= deadline) {
          terminateTree(process);
          throw new ProcessDeadlineExceededException("PROCESS_DEADLINE_EXCEEDED");
        }
        if (cancelled.getAsBoolean()) {
          terminateTree(process);
          throw new ProcessCancelledException("PROCESS_CANCELLED");
        }
        if (progress != null) {
          long mediaTimestamp = readMediaTimestamp(progress);
          if (mediaTimestamp > lastMediaTimestamp) {
            lastMediaTimestamp = mediaTimestamp;
            lastMediaProgress = now;
            mediaProgressObserved.run();
          }
          if (!mediaProgressStall.isZero()
              && now - lastMediaProgress >= mediaProgressStall.toNanos()) {
            terminateTree(process);
            throw new ProcessStalledException("PROCESS_STALLED");
          }
        }
        if (!process.isAlive()) {
          return new ProcessOutput(
              process.exitValue(), readAndDelete(stdout), readAndDelete(stderr));
        }
        try {
          Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
          terminateTree(process);
          Thread.currentThread().interrupt();
          throw new ProcessCancelledException("PROCESS_INTERRUPTED", e);
        }
      }
    } finally {
      Files.deleteIfExists(stdout);
      Files.deleteIfExists(stderr);
      if (progress != null) Files.deleteIfExists(progress);
    }
  }

  private static List<String> replaceProgressToken(List<String> command, Path progress) {
    List<String> replaced = new ArrayList<>(command.size());
    for (String argument : command) {
      replaced.add(PROGRESS_FILE_TOKEN.equals(argument) ? progress.toString() : argument);
    }
    return replaced;
  }

  private static long saturatingAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException e) {
      return Long.MAX_VALUE;
    }
  }

  private static void terminateTree(Process process) {
    ProcessHandle handle = process.toHandle();
    List<ProcessHandle> descendants = handle.descendants().toList().reversed();
    descendants.forEach(ProcessHandle::destroy);
    handle.destroy();
    descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    handle.destroyForcibly();
  }

  private static long readMediaTimestamp(Path progress) {
    try {
      long greatest = Long.MIN_VALUE;
      for (String line : Files.readAllLines(progress, StandardCharsets.UTF_8)) {
        if (line.startsWith("out_time_us=") || line.startsWith("out_time_ms=")) {
          int separator = line.indexOf('=');
          try {
            greatest = Math.max(greatest, Long.parseLong(line.substring(separator + 1).trim()));
          } catch (NumberFormatException ignored) {
          }
        }
      }
      return greatest;
    } catch (IOException e) {
      return Long.MIN_VALUE;
    }
  }

  private String readAndDelete(Path path) {
    try (var input = Files.newInputStream(path)) {
      return new String(input.readNBytes(16 * 1024), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("프로세스 출력 읽기 실패: {}", path, e);
      return "";
    }
  }
}
