package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * designcraft-cli 0.6.0 실행 래퍼.
 *
 * <p>실측 검증된 호출 패턴만 사용한다 ({@code backend-verification.md}):
 * <ul>
 *   <li>단일 명령: {@code designcraft-cli run --cmd 'id={json}'}
 *   <li>페이지 PNG: {@code designcraft-cli run --in FILE --page N --scale S --export OUT.png}
 *   <li>{@code --cmd} 체인은 fail-fast이므로 명령은 1건씩 순차 실행한다
 * </ul>
 *
 * <p>CLI는 서버 내부에서만 실행되며 외부에서 직접 호출할 수 없다.
 * 명령 허용 검증은 {@link CommandAllowlist}가 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DesignCraftCliExecutor {

  /** 로그/응답에 포함할 CLI 출력 최대 길이. */
  private static final int MAX_OUTPUT_CHARS = 64 * 1024;

  /** CLI 에러 출력 prefix (실측). */
  private static final String CLI_ERROR_PREFIX = "designcraft-cli:";

  private final PublishingProperties props;
  private final ObjectMapper objectMapper;

  /** CLI 실행 결과. */
  public record CliResult(int exitCode, String output) {
  }

  /**
   * 단일 CLI 명령을 실행한다.
   *
   * @param command 실행할 명령 (allowlist 통과済み)
   * @param workDir CLI 프로세스의 작업 디렉토리 (격리됨)
   * @return CLI 실행 결과 (exit 0 보장)
   */
  public CliResult execute(PublishingCommand command, Path workDir) {
    Path cli = cliPath();
    List<String> cmd = new ArrayList<>();
    cmd.add(cli.toAbsolutePath().toString());
    cmd.add("run");
    cmd.add("--cmd");
    cmd.add(command.toCliArg(objectMapper));
    return runProcess(cmd, workDir);
  }

  /**
   * 페이지를 PNG로 렌더링한다.
   *
   * <p>{@code render_page}가 0.6.0에 없으므로
   * {@code run --in FILE --page N --scale S --export OUT.png}를 사용한다 (실측 성공).
   *
   * @param designcraftFile 정본 파일
   * @param page 1-based 페이지 번호
   * @param scale 배율 (1 = 100%, 썸네일은 0.5)
   * @param outPng 출력 PNG 경로
   * @param workDir 작업 디렉토리
   */
  public CliResult renderPage(Path designcraftFile, int page, double scale,
      Path outPng, Path workDir) {
    Path cli = cliPath();
    List<String> cmd = List.of(
        cli.toAbsolutePath().toString(),
        "run",
        "--in", designcraftFile.toAbsolutePath().toString(),
        "--page", String.valueOf(page),
        "--scale", String.valueOf(scale),
        "--export", outPng.toAbsolutePath().toString());
    return runProcess(new ArrayList<>(cmd), workDir);
  }

  /**
   * 모든 페이지를 PNG로 렌더링한다 (썸네일 목록용).
   *
   * @param designcraftFile 정본 파일
   * @param outDir 출력 디렉토리 (page-NNN.png 생성)
   * @param scale 배율
   * @param workDir 작업 디렉토리
   */
  public CliResult renderAllPages(Path designcraftFile, Path outDir, double scale,
      Path workDir) {
    Path cli = cliPath();
    List<String> cmd = new ArrayList<>(List.of(
        cli.toAbsolutePath().toString(),
        "run",
        "--in", designcraftFile.toAbsolutePath().toString(),
        "--all-pages", outDir.toAbsolutePath().toString(),
        "--scale", String.valueOf(scale)));
    return runProcess(cmd, workDir);
  }

  private Path cliPath() {
    Path cli = Path.of(props.getCliPath());
    if (!Files.isExecutable(cli)) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "DesignCraft CLI를 찾을 수 없습니다: " + props.getCliPath()
              + " — 관리자가 designcraft-cli 0.6.0 바이너리를 배치해야 합니다.");
    }
    return cli;
  }

  private CliResult runProcess(List<String> cmd, Path workDir) {
    try {
      Files.createDirectories(workDir);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(workDir.toFile());
      pb.redirectErrorStream(true);
      log.info("DesignCraft CLI 실행: {}", String.join(" ", cmd));

      Process process = pb.start();
      CompletableFuture<String> outputFuture =
          CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
      boolean finished = process.waitFor(props.getCliTimeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
            "DesignCraft CLI 실행 시간 초과 (" + props.getCliTimeoutSeconds() + "초)");
      }
      String output = truncate(outputFuture.get(5, TimeUnit.SECONDS));
      int exitCode = process.exitValue();
      if (exitCode != 0) {
        log.warn("DesignCraft CLI 비정상 종료: exit={}, output={}", exitCode, output);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "DesignCraft CLI 실행 실패 (exit=" + exitCode + "): " + output);
      }
      if (output.contains(CLI_ERROR_PREFIX)) {
        log.warn("DesignCraft CLI 에러 prefix 감지: {}", output);
      }
      return new CliResult(exitCode, output);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "DesignCraft CLI 실행 중 오류: " + e.getMessage(), e);
    }
  }

  private static String readAll(InputStream in) {
    try {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  private static String truncate(String s) {
    if (s != null && s.length() > MAX_OUTPUT_CHARS) {
      return s.substring(0, MAX_OUTPUT_CHARS) + "...(truncated)";
    }
    return s == null ? "" : s;
  }
}
