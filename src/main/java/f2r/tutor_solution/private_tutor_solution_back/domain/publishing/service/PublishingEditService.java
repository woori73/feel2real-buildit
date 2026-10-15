package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse.Finding;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse.LowPpiFinding;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse.MissingGlyphFinding;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob.Status;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 출판 편집 서비스 (P1).
 *
 * <p>명령 실행·페이지 PNG 렌더링·preflight 검수를 담당한다.
 * 모든 변경 전 스냅샷을 자동 생성한다 (복원 가능).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingEditService {

  private final PublishingJobService jobService;
  private final DesignCraftCliExecutor cliExecutor;
  private final CommandAllowlist allowlist;
  private final StyleParamNormalizer normalizer;
  private final MarkdownStyleService markdownStyleService;
  private final PublishingVersionRepository versionRepository;
  private final ObjectMapper objectMapper;

  /**
   * 편집 명령을 순차 실행한다.
   *
   * <p>실행 전 스냅샷을 생성한다. 빈 명령 목록은 스냅샷만 생성한다 (수동 저장용).
   *
   * @return 생성된 스냅샷 ID
   */
  @Transactional
  public Long executeCommands(AuthenticatedPrincipal principal, Long jobId,
      List<PublishingCommand> commands, String label) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);

    Long snapshotId = createSnapshot(job, label != null ? label : "편집 전");
    if (commands == null || commands.isEmpty()) {
      log.info("빈 명령: 스냅샷만 생성 jobId={}, snapshotId={}", jobId, snapshotId);
      return snapshotId;
    }
    // 직접 편집 API는 AI allowlist와 동일한 제약을 적용 (내부 명령 차단)
    allowlist.validateAiPlan(commands);

    Path workDir = Path.of(job.getWorkDir());

    int i = 0;
    for (PublishingCommand cmd : commands) {
      i++;
      try {
        // 한 명령이 선택 등 여러 단계로 풀릴 수 있다 — 한 script로 실행해 선택 상태를 잇는다
        cliExecutor.executeOnDocument(expand(cmd, job, workDir), workDir);
      } catch (ResponseStatusException e) {
        throw new ResponseStatusException(e.getStatusCode(),
            "명령 " + i + "/" + commands.size() + " 실패 (" + cmd.id() + "): "
                + e.getReason(), e);
      }
    }
    log.info("명령 실행 완료: jobId={}, {}건, snapshotId={}", jobId, commands.size(), snapshotId);
    return snapshotId;
  }

  /**
   * 편집 명령을 순차 실행한다 (라벨 없음).
   */
  @Transactional
  public Long executeCommands(AuthenticatedPrincipal principal, Long jobId,
      List<PublishingCommand> commands) {
    return executeCommands(principal, jobId, commands, null);
  }

  /** 원고 반입 결과. */
  public record ManuscriptImportResult(
      Long snapshotId, int pageCount, String fileName, int styledHeadings) {}

  /**
   * 원고 스토리의 첫 프레임을 주 텍스트 프레임으로 지정한다(Smart Text Reflow).
   * 이후 스타일 변경으로 글 분량이 달라져도 CLI가 페이지를 늘리고 줄여 본문 넘침(overset)이 남지 않는다.
   */
  private void markPrimaryStory(PublishingJob job) {
    Path workDir = Path.of(job.getWorkDir());
    var doc = cliExecutor.inspect(Path.of(job.getDesigncraftFile()), workDir);
    long best = -1;
    long frameId = -1;
    for (var st : doc.path("stories")) {
      if (st.path("length").asLong() > best && st.path("frames").size() > 0) {
        best = st.path("length").asLong();
        frameId = st.path("frames").get(0).asLong();
      }
    }
    if (frameId < 0) {
      return;
    }
    cliExecutor.executeOnDocument(List.of(
        new PublishingCommand("selection.set", Map.of("ids", List.of(frameId))),
        new PublishingCommand("object.primaryTextFrame", Map.of("on", true))), workDir);
  }

  /**
   * 문서의 현재 페이지 수 (작업 전체 상태 조회용, 문서는 변경되지 않는다).
   */
  public int pageCount(AuthenticatedPrincipal principal, Long jobId) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);
    return cliExecutor.pageCount(Path.of(job.getDesigncraftFile()), Path.of(job.getWorkDir()));
  }

  /**
   * 원고 파일을 첨부하고 문서에 반입(file.place, autoflow)한다.
   *
   * <p>반입 전 스냅샷이 자동 생성된다. 허용 형식·크기 검증은 {@link PublishingJobService#attachManuscript}가 한다.
   */
  @Transactional
  public ManuscriptImportResult importManuscript(AuthenticatedPrincipal principal, Long jobId,
      org.springframework.web.multipart.MultipartFile manuscript) {
    String storedName = jobService.attachManuscript(principal, jobId, manuscript);
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);
    String original = manuscript.getOriginalFilename() == null ? storedName : manuscript.getOriginalFilename();
    String label = "원고 반입: " + (original.length() > 60 ? original.substring(0, 60) : original);
    Long snapshotId = createSnapshot(job, label);
    // 서버가 만든 고정 명령이라 allowlist(AI 계획용)를 거치지 않는다. 한 script로 실행해 선택 상태를 잇는다:
    // 반입 → 전체 선택 → 본문 스타일(ko-body, 한글 폰트)로 통일. 기본 스타일은 한글 글리프가 없다.
    try {
      cliExecutor.executeOnDocument(List.of(
          new PublishingCommand("file.place", Map.of("path", storedName, "autoflow", true)),
          new PublishingCommand("edit.selectAll", Map.of()),
          new PublishingCommand("style.paragraph.apply", Map.of("name", "ko-body"))),
          Path.of(job.getWorkDir()));
      markPrimaryStory(job);
    } catch (ResponseStatusException e) {
      throw new ResponseStatusException(e.getStatusCode(), "원고 반입 실패: " + e.getReason(), e);
    }
    // Markdown이면 제목(#, ##, ###)·인용(>)에 제목/인용 스타일을 적용한다 (작업 문서 대상, 한 script)
    int styled = 0;
    if (storedName.toLowerCase().endsWith(".md")) {
      styled = applyMarkdownStyles(principal, jobId);
    }
    return new ManuscriptImportResult(snapshotId, pageCount(principal, jobId), original, styled);
  }

  /**
   * 페이지를 PNG로 렌더링한다.
   *
   * @param page 1-based 페이지 번호
   * @param scale 배율 (기본 2.0, 썸네일은 0.5)
   * @return PNG 바이트
   */
  public byte[] renderPagePng(AuthenticatedPrincipal principal, Long jobId,
      int page, double scale) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);
    if (page < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 번호는 1 이상이어야 합니다.");
    }
    Path workDir = Path.of(job.getWorkDir());
    Path designcraftFile = Path.of(job.getDesigncraftFile());
    Path outPng;
    try {
      outPng = Files.createTempFile(workDir, "page-" + page + "-", ".png");
      cliExecutor.renderPage(designcraftFile, page, scale, outPng, workDir);
      byte[] bytes = Files.readAllBytes(outPng);
      Files.deleteIfExists(outPng);
      return bytes;
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "페이지 렌더링 실패: " + e.getMessage(), e);
    }
  }

  /**
   * preflight 검수를 실행한다.
   */
  public PreflightResponse runPreflight(AuthenticatedPrincipal principal, Long jobId) {
    PublishingJob job = jobService.requireOwned(principal, jobId);
    requireReady(job);
    Path workDir = Path.of(job.getWorkDir());
    Path designcraftFile = Path.of(job.getDesigncraftFile());

    // preflight.run은 내부 전용 명령으로 직접 실행
    PublishingCommand cmd = new PublishingCommand("preflight.run",
        Map.of("minPpi", 150));
    DesignCraftCliExecutor.CliResult result = cliExecutor.execute(cmd, workDir);
    return parsePreflight(result.output());
  }

  /** 스냅샷 생성 (정본 파일 복사). */
  @Transactional
  public Long createSnapshot(PublishingJob job, String label) {
    try {
      Path src = Path.of(job.getDesigncraftFile());
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      Path snapshotsDir = Path.of(job.getWorkDir(), "snapshots");
      Files.createDirectories(snapshotsDir);
      Path dest = snapshotsDir.resolve("snapshot-" + timestamp + ".designcraft");
      Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
      PublishingVersion version = new PublishingVersion(
          job.getId(), dest.toString(), label);
      version = versionRepository.save(version);
      log.info("스냅샷 생성: jobId={}, versionId={}", job.getId(), version.getId());
      return version.getId();
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "스냅샷 생성 실패: " + e.getMessage(), e);
    }
  }

  private void requireReady(PublishingJob job) {
    if (job.getStatus() != Status.RUNNING && job.getStatus() != Status.DONE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "작업이 준비되지 않았습니다: " + job.getStatus());
    }
    if (job.getWorkDir() == null || job.getDesigncraftFile() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "작업 디렉토리가 준비되지 않았습니다.");
    }
  }

  /** style.paragraph.create 파라미터 정규화. */
  private PublishingCommand normalizeCommand(PublishingCommand cmd) {
    if ("style.paragraph.create".equals(cmd.id())) {
      Map<String, Object> normalized = normalizer.normalizeParagraph(cmd.params());
      return new PublishingCommand(cmd.id(), normalized);
    }
    return cmd;
  }

  /**
   * 명령을 CLI 실행 단위로 풀어 쓴다.
   *
   * <p>스타일 적용은 CLI가 <b>선택</b>을 요구한다({@code style.*.apply {name}}는 선택된 텍스트/프레임에 적용).
   * 화면의 {@code {page, style}} 형태는 그 쪽(1-based)의 텍스트 프레임을 선택({@code selection.set})한 뒤
   * {@code {name}}으로 적용하도록 바꾼다. 프레임에 이어진 글 전체(연결된 스토리)에 적용된다는 점이 CLI의 동작이다.
   * {@code page}가 없는 {@code {name}} 형태(AI 계획 등)는 그대로 둔다.
   */
  List<PublishingCommand> expand(PublishingCommand cmd, PublishingJob job, Path workDir) {
    PublishingCommand normalized = normalizeCommand(cmd);
    Map<String, Object> params = normalized.params() == null ? Map.of() : normalized.params();
    boolean styleApply = "style.paragraph.apply".equals(normalized.id())
        || "style.character.apply".equals(normalized.id());
    if (!styleApply || !params.containsKey("page")) {
      return List.of(normalized);
    }
    Object styleName = params.containsKey("style") ? params.get("style") : params.get("name");
    if (!(styleName instanceof String) || ((String) styleName).isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "적용할 스타일 이름이 없습니다.");
    }
    int page;
    try {
      page = Integer.parseInt(String.valueOf(params.get("page")));
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "쪽 번호가 올바르지 않습니다.");
    }
    List<Long> frames = textFramesOnPage(job, workDir, page);
    if (frames.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, page + "쪽에는 텍스트 프레임이 없습니다.");
    }
    return List.of(
        new PublishingCommand("selection.set", Map.of("ids", frames)),
        new PublishingCommand(normalized.id(), Map.of("name", styleName)));
  }

  /** 문서 구조에서 1-based 쪽에 놓인 텍스트 프레임 id. 쪽 범위(스프레드 좌표) 안에 중심이 있는 프레임을 고른다. */
  private List<Long> textFramesOnPage(PublishingJob job, Path workDir, int page) {
    var doc = cliExecutor.inspect(Path.of(job.getDesigncraftFile()), workDir);
    List<Long> ids = new ArrayList<>();
    for (var spread : doc.path("spreads")) {
      for (var pg : spread.path("pages")) {
        if (pg.path("index").asInt(-1) != page - 1) {
          continue;
        }
        double x0 = pg.path("bounds").path(0).asDouble();
        double x1 = pg.path("bounds").path(2).asDouble();
        for (var item : spread.path("items")) {
          if (!"<text frame>".equals(item.path("kind").asText())) {
            continue;
          }
          double cx = (item.path("bounds").path(0).asDouble() + item.path("bounds").path(2).asDouble()) / 2;
          if (cx >= x0 && cx <= x1) {
            ids.add(item.path("id").asLong());
          }
        }
      }
    }
    return ids;
  }

  /** preflight.run JSON 출력을 Qoder §2 스키마로 파싱한다. */
  private PreflightResponse parsePreflight(String output) {
    try {
      String json = extractJson(output);
      Map<String, Object> map = objectMapper.readValue(json,
          new TypeReference<Map<String, Object>>() {});
      List<Finding> overset = new ArrayList<>();
      List<MissingGlyphFinding> missingGlyphs = new ArrayList<>();
      List<LowPpiFinding> lowPpi = new ArrayList<>();
      Object issuesObj = map.get("issues");
      if (issuesObj instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Map<?, ?> m) {
            String kind = str(m.get("kind"));
            String severity = str(m.get("severity"));
            String message = str(m.get("message"));
            Integer page = m.get("page") instanceof Number n ? n.intValue() : null;
            boolean isError = "error".equalsIgnoreCase(severity);
            if ("missingGlyphs".equalsIgnoreCase(kind)) {
              String glyph = str(m.get("glyph"));
              if (glyph.isEmpty()) {
                glyph = str(m.get("item"));
              }
              missingGlyphs.add(new MissingGlyphFinding(page, message, glyph));
            } else if ("lowPpi".equalsIgnoreCase(kind) || "ppi".equalsIgnoreCase(kind)) {
              Integer ppi = m.get("ppi") instanceof Number n ? n.intValue()
                  : m.get("actualPpi") instanceof Number n2 ? n2.intValue() : null;
              lowPpi.add(new LowPpiFinding(page, message, ppi));
            } else if ("overset".equalsIgnoreCase(kind)) {
              overset.add(new Finding(page, message));
            } else if (isError) {
              // 알 수 없는 error는 overset에 포함 (프론트 표시용)
              overset.add(new Finding(page, "[" + kind + "] " + message));
            }
            // warning은 프론트에서 표시하지 않으므로 무시
          }
        }
      }
      boolean passed = overset.isEmpty() && missingGlyphs.isEmpty();
      return new PreflightResponse(passed, overset, missingGlyphs, lowPpi);
    } catch (Exception e) {
      log.warn("preflight 결과 파싱 실패: {}", e.getMessage());
      return new PreflightResponse(false,
          List.of(new Finding(null, "preflight 결과 파싱 실패")),
          List.of(), List.of());
    }
  }

  private String extractJson(String output) {
    int start = output.indexOf('{');
    int end = output.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return output.substring(start, end + 1);
    }
    return output;
  }

  private String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  /**
   * Markdown 원고의 제목 스타일을 자동 적용한다 (P5).
   *
   * <p><b>문단 위치 기반 처리</b>: Markdown 파싱 시 기록한 문단 인덱스를 사용하여
   * 정확한 문단에 스타일을 적용한다. 제목과 본문에 동일한 문구가 있어도
   * Markdown에서 제목으로 파싱된 문단에만 스타일이 적용된다.
   *
   * <p>처리 순서 (역순으로 처리하여 오프셋 변동 영향 제거):
   * <ol>
   *   <li>{@code story.get}으로 스토리 텍스트와 ID 조회</li>
   *   <li>문단별 UTF-8 바이트 오프셋 계산</li>
   *   <li>각 제목 문단(역순)의 마커를 {@code text.select} + {@code edit.clear}로 제거</li>
   *   <li>제목 텍스트에 {@code style.paragraph.apply}로 스타일 적용</li>
   * </ol>
   *
   * @return 적용된 스타일 수
   */
  @Transactional
  public int applyMarkdownStyles(AuthenticatedPrincipal principal, Long jobId) {
    jobService.requireOwned(principal, jobId);
    Path manuscriptPath = jobService.getManuscriptPath(principal, jobId);
    if (manuscriptPath == null || !Files.exists(manuscriptPath)) {
      throw new IllegalArgumentException("원고 파일이 없습니다. 먼저 원고를 업로드하세요.");
    }
    String fileName = manuscriptPath.getFileName().toString().toLowerCase();
    if (!fileName.endsWith(".md") && !fileName.endsWith(".markdown")
        && !fileName.endsWith(".txt")) {
      throw new IllegalArgumentException("Markdown(.md) 또는 텍스트(.txt) 원고만 지원합니다.");
    }

    String markdown;
    try {
      markdown = Files.readString(manuscriptPath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("원고 읽기 실패: " + e.getMessage(), e);
    }

    var parsed = markdownStyleService.parse(markdown);
    if (parsed.headingBlocks().isEmpty()) {
      log.info("Markdown 제목 없음 (jobId={}), 스타일 적용 스킵", jobId);
      return 0;
    }

    var job = jobService.requireOwned(principal, jobId);
    Path workDir = Path.of(job.getWorkDir());

    // 스토리 텍스트 조회: 작업 문서에서 가장 긴 스토리가 원고다 (선택 상태에 의존하지 않는다)
    long storyId = -1;
    String storyText = "";
    try {
      var doc = cliExecutor.inspect(Path.of(job.getDesigncraftFile()), workDir);
      long bestLength = -1;
      for (var st : doc.path("stories")) {
        if (st.path("length").asLong() > bestLength) {
          bestLength = st.path("length").asLong();
          storyId = st.path("id").asLong();
        }
      }
      if (storyId < 0) {
        throw new IllegalStateException("문서에 텍스트 스토리가 없습니다.");
      }
      var storyResult = cliExecutor.execute(
          new PublishingCommand("story.get", Map.of("story", storyId)), workDir);
      storyText = objectMapper.readTree(storyResult.output()).get("text").asText();
    } catch (Exception e) {
      throw new IllegalStateException("스토리 조회 실패: " + e.getMessage(), e);
    }

    // 문단별 바이트 오프셋 계산
    java.util.List<long[]> paraRanges = new java.util.ArrayList<>();
    byte[] textBytes = storyText.getBytes(StandardCharsets.UTF_8);
    long currentStart = 0;
    for (int i = 0; i < textBytes.length; i++) {
      if (textBytes[i] == '\n') {
        paraRanges.add(new long[]{currentStart, i});
        currentStart = i + 1;
      }
    }
    // 마지막 문단 (줄바꿈으로 끝나지 않은 경우)
    if (currentStart < textBytes.length) {
      paraRanges.add(new long[]{currentStart, textBytes.length});
    } else if (currentStart == textBytes.length && !paraRanges.isEmpty()) {
      // 텍스트가 줄바꿈으로 끝나면 빈 마지막 문단 추가하지 않음
    }

    // 제목 블록을 문단 인덱스 역순으로 정렬
    var blocks = new java.util.ArrayList<>(parsed.headingBlocks());
    blocks.sort((a, b) -> Integer.compare(b.paragraphIndex(), a.paragraphIndex()));

    // 역순(뒤 문단부터)으로 명령을 모아 한 script로 실행한다 — 선택 상태가 이어지고, 실패하면 문서는 그대로다
    List<PublishingCommand> batch = new ArrayList<>();
    int applied = 0;
    for (var block : blocks) {
      int paraIdx = block.paragraphIndex();
      if (paraIdx < 0 || paraIdx >= paraRanges.size()) {
        log.warn("문단 인덱스 범위 초과: {} (전체 {}개)", paraIdx, paraRanges.size());
        continue;
      }
      long[] range = paraRanges.get(paraIdx);
      long paraStart = range[0];
      long paraEnd = range[1];
      // 마커 제거 (문단 시작부터 markerBytes만큼)
      if (block.markerBytes() > 0) {
        batch.add(new PublishingCommand("text.select",
            Map.of("story", storyId, "anchor", paraStart, "focus", paraStart + block.markerBytes())));
        batch.add(new PublishingCommand("edit.clear", Map.of()));
        paraEnd = paraEnd - block.markerBytes();
      }
      batch.add(new PublishingCommand("text.select",
          Map.of("story", storyId, "anchor", paraStart, "focus", paraEnd)));
      batch.add(new PublishingCommand("style.paragraph.apply", Map.of("name", block.style())));
      applied++;
    }
    if (!batch.isEmpty()) {
      createSnapshot(job, "Markdown 스타일 적용 전");
      cliExecutor.executeOnDocument(batch, workDir);
    }

    log.info("Markdown 스타일 적용 완료: jobId={}, {}개", jobId, applied);
    return applied;
  }

}
