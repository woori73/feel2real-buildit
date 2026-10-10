package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.config.ArtCraftEditorProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditCommandRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftCliException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 한글 패치된 PdfCraft CLI({@code pdfcraft-cli run --script steps.json}) 실행 래퍼.
 *
 * <p>동작:
 * <ol>
 *   <li>편집 명령 목록을 서버 측에서 정규화한 뒤 {@code steps.json}
 *       (JSON 배열, {@code [{"tool":..,"args":{..}}]}) 으로 기록
 *   <li>{@code ProcessBuilder}로 CLI 실행. 작업 디렉토리는 프로젝트 디렉토리로 격리하므로
 *       {@code doc_save}의 상대 경로 출력물이 프로젝트 안에 생성된다.
 *   <li>표준 출력/에러를 병합 수집하고, 설정된 타임아웃(기본 60초)을 적용
 * </ol>
 *
 * <p><b>명령 정규화 (P0-③):</b> 매 편집 요청은 새 CLI 프로세스에서 실행되므로 문서 id는
 * 항상 1부터 시작한다.
 * <ul>
 *   <li>첫 step이 {@code doc_open}이 아니면 선두에
 *       {@code {"tool":"doc_open","args":{"path":"source.pdf"}}} 를 삽입한다.
 *       클라이언트가 이미 첫 step으로 {@code doc_open}을 넣었으면 중복 삽입하지 않는다.
 *   <li>{@code doc_open}을 제외한 각 후속 step의 {@code args}에 {@code doc} 키가 없으면
 *       {@code 1}을 기본 주입한다 (클라이언트가 명시한 값은 존중).
 *   <li>{@code doc_save} step의 {@code args.path}가 없거나 비어 있으면 서버가
 *       {@code "output.pdf"}로 고정 주입한다.
 * </ul>
 *
 * <p><b>체인 중단 정책:</b> {@code pdfcraft-cli run --script}는 스크립트 레벨의
 * {@code stop_on_error} 옵션을 지원하지 않으며, 첫 실패 step에서 즉시 중단하고
 * nonzero exit + {@code pdfcraft-cli: step N (tool): ...} (stderr) 형태로 보고한다
 * (pdfcraft-cli {@code main.rs}의 {@code run()} 실측). {@code stop_on_error}는 MCP
 * {@code command_batch} 도구에만 존재하는데, 이 도구는 headless command id 기반이라
 * 화이트리스트의 {@code page_render}을 실행할 수 없어 체인 래퍼로 쓸 수 없다.
 * 따라서 "실패해도 계속"의 완전한 구현은 불가하고, 그 대신 실패한 step은 502 예외 대신
 * {@link CliResult#warnings()} 목록으로 반환한다 (편집 전 스냅샷이 이미 저장되어 있어
 * 되돌리기로 복구 가능). 바이너리 부재(503)·타임아웃(504)은 기존대로 예외를 던진다.
 *
 * <p>CLI는 서버 내부에서만 실행되며 외부에서 직접 호출할 수 없다.
 * 명령 화이트리스트 검증은 호출자({@link ArtCraftEditorService})가 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArtCraftCliExecutor {

  /** 로그/응답에 포함할 CLI 출력 최대 길이. */
  private static final int MAX_OUTPUT_CHARS = 64 * 1024;

  /** 개별 경고 메시지의 최대 길이. */
  private static final int MAX_WARNING_CHARS = 500;

  /** 삽입되는 {@code doc_open}이 여는 프로젝트 소스 PDF (프로젝트 디렉토리 기준 상대 경로). */
  private static final String SOURCE_PDF_PATH = "source.pdf";

  /** {@code doc_save}의 path가 없을 때 서버가 고정 주입하는 출력 경로 (프로젝트 디렉토리 기준). */
  private static final String DEFAULT_DOC_SAVE_PATH = "output.pdf";

  /**
   * 매 편집 요청이 새 CLI 프로세스에서 실행되므로 첫 번째로 열리는 문서의 id는 항상 1이다.
   * {@code doc}을 명시하지 않은 후속 step에 주입되는 기본값.
   */
  private static final int FIRST_DOC_ID = 1;

  /** CLI stderr의 step 실패 보고 형식: {@code pdfcraft-cli: step N (tool): message}. */
  private static final Pattern STEP_ERROR_PATTERN =
      Pattern.compile("(?m)^pdfcraft-cli: step (\\d+) \\(([^)]+)\\):\\s*(.+)$");

  private final ArtCraftEditorProperties props;
  private final ObjectMapper objectMapper;

  /** CLI 실행 결과. {@code warnings}는 실패한 step의 요약 (성공 시 빈 목록). */
  public record CliResult(int exitCode, String output, List<String> warnings) {
    /** 하위 호환 생성자 — 기존 호출부(테스트 포함)는 warnings 없이 호출한다. */
    public CliResult(int exitCode, String output) {
      this(exitCode, output, List.of());
    }
  }

  /**
   * 편집 명령을 정규화·steps.json으로 기록하고 CLI를 실행한다.
   *
   * @param commands 화이트리스트를 통과한 편집 명령 목록
   * @param workDir steps.json을 기록할 격리된 작업 디렉토리 (호출자가 생성/정리)
   * @param projectDir CLI 프로세스의 작업 디렉토리 (= 프로젝트 디렉토리)
   * @return CLI 실행 결과. step 실패 시에도 예외 대신 {@code warnings}에 담아 반환한다.
   * @throws ArtCraftCliException 바이너리 부재(503), 타임아웃(504), 실행 인프라 오류(502)
   */
  public CliResult execute(List<EditCommandRequest> commands, Path workDir, Path projectDir) {
    Path cli = Path.of(props.getCliPath());
    if (!Files.isExecutable(cli)) {
      throw new ArtCraftCliException(HttpStatus.SERVICE_UNAVAILABLE,
          "PDF 편집 CLI를 찾을 수 없습니다: " + props.getCliPath()
              + " — 관리자가 한글 패치된 pdfcraft-cli 바이너리를 배치해야 합니다.");
    }
    try {
      Files.createDirectories(workDir);
      Path stepsFile = workDir.resolve("steps.json");
      List<Map<String, Object>> steps = buildSteps(commands);
      objectMapper.writeValue(stepsFile.toFile(), steps);

      ProcessBuilder pb = new ProcessBuilder(
          cli.toAbsolutePath().toString(), "run", "--script",
          stepsFile.toAbsolutePath().toString());
      pb.directory(projectDir.toFile());
      pb.redirectErrorStream(true);
      log.info("ArtCraft CLI 실행: {} (workDir={}, steps={})",
          String.join(" ", pb.command()), workDir, steps.size());

      Process process = pb.start();
      // 파이프 데드락 방지: 출력은 별도 스레드에서 수집하면서 타임아웃 대기
      CompletableFuture<String> outputFuture =
          CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
      boolean finished = process.waitFor(props.getCliTimeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new ArtCraftCliException(HttpStatus.GATEWAY_TIMEOUT,
            "PDF 편집 CLI 실행 시간 초과 (" + props.getCliTimeoutSeconds() + "초)");
      }
      String output = truncate(outputFuture.get(5, TimeUnit.SECONDS));
      int exitCode = process.exitValue();
      if (exitCode != 0) {
        // P0-③ 정책: step 실패는 502 예외가 아니라 warnings 목록으로 반환한다.
        // 편집 전 스냅샷이 이미 저장되어 있으므로 되돌리기로 복구할 수 있어 상태는 안전하다.
        List<String> stepWarnings = parseStepErrors(output, steps);
        log.warn("ArtCraft CLI step 실패: exit={}, warnings={}", exitCode, stepWarnings);
        return new CliResult(exitCode, output, stepWarnings);
      }
      return new CliResult(exitCode, output);
    } catch (ArtCraftCliException e) {
      throw e;
    } catch (Exception e) {
      throw new ArtCraftCliException(HttpStatus.BAD_GATEWAY,
          "PDF 편집 CLI 실행 중 오류: " + e.getMessage(), e);
    }
  }

  /**
   * 클라이언트 명령을 CLI 실행용 steps로 정규화한다 (P0-③).
   *
   * <ul>
   *   <li>첫 step이 {@code doc_open}이 아니면
   *       {@code {"tool":"doc_open","args":{"path":"source.pdf"}}} 삽입
   *   <li>{@code doc_open}을 제외한 각 step의 {@code args.doc} 기본값 {@code 1} 주입
   *   <li>{@code doc_save}의 {@code args.path} 기본값 {@code "output.pdf"} 주입
   * </ul>
   */
  private static List<Map<String, Object>> buildSteps(List<EditCommandRequest> commands) {
    List<Map<String, Object>> steps = new ArrayList<>();
    boolean clientOpenedDoc =
        !commands.isEmpty() && "doc_open".equals(commands.get(0).tool());
    if (!clientOpenedDoc) {
      Map<String, Object> openArgs = new LinkedHashMap<>();
      openArgs.put("path", SOURCE_PDF_PATH);
      steps.add(step("doc_open", openArgs));
    }
    for (EditCommandRequest cmd : commands) {
      Map<String, Object> args =
          new LinkedHashMap<>(cmd.args() == null ? Map.of() : cmd.args());
      if (!"doc_open".equals(cmd.tool()) && !args.containsKey("doc")) {
        args.put("doc", FIRST_DOC_ID);
      }
      if ("doc_save".equals(cmd.tool())) {
        Object path = args.get("path");
        if (!(path instanceof String pathStr) || pathStr.isBlank()) {
          args.put("path", DEFAULT_DOC_SAVE_PATH);
        }
      }
      steps.add(step(cmd.tool(), args));
    }
    return steps;
  }

  private static Map<String, Object> step(String tool, Map<String, Object> args) {
    Map<String, Object> step = new LinkedHashMap<>();
    step.put("tool", tool);
    step.put("args", args);
    return step;
  }

  /**
   * CLI 출력(stderr 병합됨)에서 step 실패 보고를 추출해 warnings 목록으로 변환한다.
   * 형식이 맞지 않으면 일반적인 실패 메시지로 대체한다 (경고 누락 방지).
   */
  private static List<String> parseStepErrors(String output, List<Map<String, Object>> steps) {
    List<String> warnings = new ArrayList<>();
    if (output != null) {
      Matcher m = STEP_ERROR_PATTERN.matcher(output);
      while (m.find()) {
        int idx;
        try {
          idx = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
          continue;
        }
        // CLI가 보고하는 step 번호는 정규화된 steps 기준(주입된 doc_open 포함)이다.
        String tool = (idx >= 1 && idx <= steps.size())
            ? String.valueOf(steps.get(idx - 1).get("tool"))
            : m.group(2);
        warnings.add("step " + idx + " (" + tool + ") 실패: "
            + truncateWarning(m.group(3).trim()));
      }
    }
    if (warnings.isEmpty()) {
      warnings.add("PDF 편집 CLI가 비정상 종료되었습니다 (상세 내용은 cliOutput 참조).");
    }
    return warnings;
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

  private static String truncateWarning(String s) {
    if (s != null && s.length() > MAX_WARNING_CHARS) {
      return s.substring(0, MAX_WARNING_CHARS) + "...(truncated)";
    }
    return s == null ? "" : s;
  }
}
