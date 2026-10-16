package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

  /** 작업 디렉토리 안의 정본 파일명 (TemplateService가 배치한다). */
  static final String DOCUMENT_FILE = "job.designcraft";

  /** CLI 에러 출력 prefix (실측). */
  private static final String CLI_ERROR_PREFIX = "designcraft-cli:";

  private final PublishingProperties props;
  private final ObjectMapper objectMapper;

  /** CLI 실행 결과. */
  public record CliResult(int exitCode, String output) {
  }

  /**
   * 작업 문서에 대해 명령을 실행한다 — <b>읽기 전용</b>({@code run --in FILE --cmd}, 변경은 저장되지 않는다).
   *
   * <p>검수·출력·조회용이다. 작업 문서({@value #DOCUMENT_FILE})가 없으면 CLI 기본 샘플 문서로 대체하지 않고
   * 실패한다({@code 409}). 문서를 바꾸는 명령은 {@link #executeOnDocument}를 쓴다.
   *
   * @param command 실행할 명령 (allowlist 통과済み)
   * @param workDir 작업 디렉토리 (정본 {@value #DOCUMENT_FILE}이 있어야 한다)
   * @return CLI 실행 결과 (exit 0 보장)
   */
  public CliResult execute(PublishingCommand command, Path workDir) {
    Path cli = cliPath();
    Path document = requireDocument(workDir);
    List<String> cmd = new ArrayList<>(List.of(
        cli.toAbsolutePath().toString(),
        "run",
        "--in", document.toAbsolutePath().toString(),
        "--cmd", command.toCliArg(objectMapper)));
    return runProcess(cmd, workDir, null);
  }

  /** 작업 디렉토리의 정본 문서. 없으면 기본 샘플로 대체하지 않고 409로 실패한다. */
  private Path requireDocument(Path workDir) {
    Path document = workDir.resolve(DOCUMENT_FILE);
    if (!Files.isRegularFile(document)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "작업 문서가 준비되지 않았습니다.");
    }
    return document;
  }

  /** 문서 구조(JSON)를 조회한다 ({@code document.inspect}, 문서는 변경되지 않는다). */
  public com.fasterxml.jackson.databind.JsonNode inspect(Path designcraftFile, Path workDir) {
    List<String> cmd = new ArrayList<>(List.of(
        cliPath().toAbsolutePath().toString(),
        "run",
        "--in", designcraftFile.toAbsolutePath().toString(),
        "--cmd", "document.inspect"));
    String output = runProcess(cmd, workDir, null).output();
    try {
      return objectMapper.readTree(output);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "문서 정보를 읽을 수 없습니다: " + e.getMessage(), e);
    }
  }

  /**
   * 작업 문서({@value #DOCUMENT_FILE})에 명령을 실행하고 변경을 문서에 저장한다.
   *
   * <p>{@code script - --in FILE --save TMP}로 실행한 뒤 임시 파일을 정본으로 원자적으로 교체한다.
   * 명령이 실패하면 정본은 그대로 남는다. output은 {@code run}과 같은 모양(단일 결과 JSON)으로 돌려준다.
   *
   * @param command 실행할 명령 (allowlist 통과済み)
   * @param workDir 작업 디렉토리 (정본 {@value #DOCUMENT_FILE}이 있어야 한다)
   */
  public CliResult executeOnDocument(PublishingCommand command, Path workDir) {
    return executeOnDocument(List.of(command), workDir);
  }

  /**
   * 여러 명령을 한 프로세스(한 {@code script})로 순서대로 실행하고 마지막에 한 번만 저장한다.
   * 선택 상태 같은 프로세스 내 상태가 명령 사이에 이어지고, 중간에 실패하면 정본은 그대로다.
   * 명령이 1건일 때만 output을 단일 결과로 풀어 준다(2건 이상은 {@code script} 출력 그대로).
   */
  public CliResult executeOnDocument(List<PublishingCommand> commands, Path workDir) {
    Path cli = cliPath();
    Path document = requireDocument(workDir);
    Path saved = workDir.resolve(DOCUMENT_FILE + ".saving");
    List<String> cmd = new ArrayList<>(List.of(
        cli.toAbsolutePath().toString(),
        "script", "-",
        "--in", document.toAbsolutePath().toString(),
        "--save", saved.toAbsolutePath().toString()));
    try {
      StringBuilder script = new StringBuilder();
      for (PublishingCommand c : commands) {
        script.append(c.toCliArg(objectMapper)).append('\n');
      }
      CliResult result = runProcess(cmd, workDir, script.toString());
      Files.move(saved, document, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      return commands.size() == 1
          ? new CliResult(result.exitCode(), unwrapSingleResult(result.output()))
          : result;
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "편집 결과 저장 실패: " + e.getMessage(), e);
    } finally {
      try {
        Files.deleteIfExists(saved);
      } catch (IOException ignored) {
        // 임시 파일 정리 실패는 무시 (다음 실행이 덮어쓴다)
      }
    }
  }

  /** 문서의 페이지 수 ({@link #inspect}의 {@code pageCount}). */
  public int pageCount(Path designcraftFile, Path workDir) {
    return inspect(designcraftFile, workDir).path("pageCount").asInt(0);
  }

  /** {@code script} 출력({@code {"completed":1,"results":[X]}})에서 X만 꺼내 {@code run} 출력과 같은 모양으로 맞춘다. */
  private String unwrapSingleResult(String output) {
    try {
      var node = objectMapper.readTree(output);
      if (node.path("results").isArray() && node.path("results").size() == 1) {
        return objectMapper.writeValueAsString(node.path("results").get(0));
      }
    } catch (IOException ignored) {
      // JSON이 아니면 원문 그대로 돌려준다
    }
    return output;
  }

  /**
   * 페이지를 PNG로 렌더링한다.
   *
   * <p>{@code render_page}가 0.6.0에 없으므로
   * {@code run --in FILE --page N --scale S --export OUT.png}를 사용한다 (실측 성공).
   *
   * @param designcraftFile 정본 파일
   * @param page 1-based 페이지 번호 (CLI에는 0-based로 변환해 넘긴다)
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
        "--page", String.valueOf(page - 1), // CLI의 --page는 0-based다 (API/화면은 1-based)
        "--scale", String.valueOf(scale),
        "--export", outPng.toAbsolutePath().toString());
    return runProcess(new ArrayList<>(cmd), workDir, null);
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
    return runProcess(cmd, workDir, null);
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

  private CliResult runProcess(List<String> cmd, Path workDir, String stdin) {
    try {
      Files.createDirectories(workDir);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(workDir.toFile());
      pb.redirectErrorStream(true);
      log.info("DesignCraft CLI 실행: {}", String.join(" ", cmd));

      Process process = pb.start();
      f2r.tutor_solution.private_tutor_solution_back.domain.publishing.maintenance.PublishingMaintenanceGate.trackCurrentProcess(process);
      try (var out = process.getOutputStream()) {
        if (stdin != null) {
          out.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
      }
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
