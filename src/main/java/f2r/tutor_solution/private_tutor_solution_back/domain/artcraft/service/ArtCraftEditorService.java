package f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.config.ArtCraftEditorProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.CreateProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditCommandRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.EditProjectResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.ProjectEvent;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.ProjectResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.RevertProjectRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.SnapshotResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.dto.SourceUploadResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftNotFoundException;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftSourceMissingException;
import f2r.tutor_solution.private_tutor_solution_back.domain.artcraft.exception.ArtCraftVersionConflictException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ArtCraft 웹 편집기 제어 서버의 비즈니스 로직.
 *
 * <p>설계 문서({@code ai-control-architecture.md})의 서버 CLI 경유 구조를 구현한다:
 * <ul>
 *   <li><b>학원별 격리</b> — 프로젝트는 {@code {storage-root}/artcraft-projects/{academyId}/{projectId}/}
 *       아래에 저장된다. academyId는 JWT 인증 주체에서 해석하며(TUTOR=소속 학원, ADMIN=명시),
 *       다른 학원의 경로로는 절대 접근할 수 없다 (path traversal 검증 포함).
 *   <li><b>낙관적 잠금</b> — 프로젝트 JSON의 {@code version} 카운터로 동시 편집을 막는다.
 *       같은 프로젝트의 읽기-수정-쓰기는 프로젝트별 락으로 직렬화한다.
 *   <li><b>스냅샷</b> — 편집/되돌리기 전에 {@code snapshots/v{n}.json}에 버전 n 상태를 보존한다.
 *   <li><b>소스 임포트</b> — 편집 입력 PDF({@code source.pdf})를 multipart로 등록한다.
 *       HTML 업로드 시 원본은 {@code source.html}에 보존하고 headless Chromium
 *       {@code --print-to-pdf}로 {@code source.pdf}를 생성한다 (정본=HTML 정책).
 *       {@code edit}은 {@code source.pdf}가 있을 때만 허용된다.
 *   <li><b>SSE</b> — 프로젝트별 구독자에게 변경 알림을 브로드캐스트한다.
 * </ul>
 *
 * <p>권한: 생성·편집·되돌리기는 ADMIN/TUTOR만 가능. 조회·스냅샷 목록·SSE·PDF 다운로드는
 * 인증된 사용자 중 학원이 해석되는 주체에게 허용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtCraftEditorService {

  /** CLI 명령 화이트리스트 — 이 목록에 없는 도구는 400으로 거부된다 (보안). */
  private static final Set<String> ALLOWED_TOOLS = Set.of(
      "doc_open", "page_add_text", "content_update",
      "doc_save", "page_render", "doc_info", "edit_undo");

  /** 프로젝트 ID 허용 형식 — path traversal 방지의 1차 방어선. */
  private static final Pattern PROJECT_ID_PATTERN =
      Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

  private static final String PROJECT_FILE = "project.json";
  private static final String SNAPSHOT_DIR = "snapshots";
  private static final String WORK_DIR = "work";

  /**
   * 편집 소스 PDF. {@code edit}의 선행조건 — 존재하지 않으면 409.
   * HTML 업로드 시 headless Chromium이 생성하고, PDF 업로드 시 그대로 저장된다.
   */
  private static final String SOURCE_PDF = "source.pdf";

  /**
   * 정본 HTML 원본. HTML 업로드 시에만 존재한다.
   * 정본 정책: HTML이 정본, PDF는 산출물 (PDF→HTML 역변환을 원본 복원으로 사용 금지).
   */
  private static final String SOURCE_HTML = "source.html";

  /**
   * 소스 업로드 크기 상한 (20MB).
   * 앱 전역 문서 업로드 상한({@code upload.max-document-bytes})과 동일한 값이다.
   */
  private static final long MAX_SOURCE_BYTES = 20 * 1024 * 1024;

  private final AcademyRepository academyRepository;
  private final ArtCraftEditorProperties props;
  private final ArtCraftCliExecutor cliExecutor;
  private final ArtCraftHtmlToPdfConverter htmlToPdfConverter;
  private final ObjectMapper objectMapper;

  /** 프로젝트별 직렬화 락 (key = academyId + "/" + projectId). */
  private final ConcurrentHashMap<String, Object> projectLocks = new ConcurrentHashMap<>();

  /** 프로젝트별 SSE 구독자 (key = academyId + "/" + projectId). */
  private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers =
      new ConcurrentHashMap<>();

  // -------------------------------------------------------------------------
  // 프로젝트 CRUD
  // -------------------------------------------------------------------------

  /**
   * 프로젝트를 생성한다 (ADMIN/TUTOR).
   *
   * @return 생성된 프로젝트 (version=1)
   */
  public ProjectResponse createProject(AuthenticatedPrincipal principal,
      CreateProjectRequest request) {
    requireEditorRole(principal);
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("프로젝트 이름(name)은 필수입니다.");
    }
    long academyId = resolveAcademyId(principal, request.academyId());
    String projectId = request.projectId() == null || request.projectId().isBlank()
        ? UUID.randomUUID().toString()
        : validateProjectId(request.projectId());

    Object lock = lockFor(academyId, projectId);
    synchronized (lock) {
      Path dir = projectDir(academyId, projectId);
      if (Files.exists(dir.resolve(PROJECT_FILE))) {
        throw new IllegalArgumentException("이미 존재하는 프로젝트입니다: " + projectId);
      }
      try {
        Files.createDirectories(dir.resolve(SNAPSHOT_DIR));
      } catch (IOException e) {
        throw new UncheckedIOException("프로젝트 디렉토리 생성 실패", e);
      }
      String now = nowIso();
      ObjectNode project = objectMapper.createObjectNode();
      project.put("version", 1);
      project.put("project_id", projectId);
      project.put("academy_id", academyId);
      project.put("name", request.name());
      project.put("created_by", principal.loginId());
      project.put("created_at", now);
      project.put("updated_at", now);
      project.put("last_editor", principal.loginId());
      project.putNull("pdf_path");
      ArrayNode items = project.putArray("items");
      JsonNode requestedItems = request.items();
      if (requestedItems != null && requestedItems.isArray()) {
        items.addAll((ArrayNode) requestedItems);
      }
      project.putArray("history");

      saveProject(dir, project);
      snapshot(dir, project, principal.loginId(), "생성");
      publishEvent(academyId, projectId,
          new ProjectEvent("created", projectId, 1, now, principal.loginId(), request.name()));
      log.info("ArtCraft 프로젝트 생성: academy={} project={} by={}", academyId, projectId,
          principal.loginId());
      return toResponse(project);
    }
  }

  /** 프로젝트를 조회한다 (버전 포함). */
  public ProjectResponse getProject(AuthenticatedPrincipal principal,
      String projectId, Long requestedAcademyId) {
    long academyId = resolveAcademyId(principal, requestedAcademyId);
    ObjectNode project = loadProject(academyId, validateProjectId(projectId));
    return toResponse(project);
  }

  // -------------------------------------------------------------------------
  // 편집 소스 업로드 (P0-④ HTML→PDF 입력 경로)
  // -------------------------------------------------------------------------

  /**
   * 편집 소스를 업로드한다 (ADMIN/TUTOR, multipart).
   *
   * <ul>
   *   <li><b>PDF</b> — {@code source.pdf}로 그대로 저장한다. 이 경우 정본은 PDF
   *       자체이므로, 이전에 남아 있던 {@code source.html}(내용 불일치 가능)은
   *       삭제한다.
   *   <li><b>HTML</b> — 원본을 {@code source.html}에 보존한 뒤 headless Chromium
   *       {@code --print-to-pdf}로 {@code source.pdf}를 생성한다.
   *       Chromium이 없으면 503.
   * </ul>
   *
   * <p>기존 {@code source.pdf}가 있으면 덮어쓴다. 소스 업로드로 프로젝트 버전은
   * 증가하지 않는다 (버전은 {@code edit}의 낙관적 잠금 카운터 전용).
   * 업로드 후 SSE {@code source_uploaded} 이벤트를 브로드캐스트한다.
   *
   * @return 업로드 결과 (소스 종류·변환 여부 포함)
   */
  public SourceUploadResponse uploadSource(AuthenticatedPrincipal principal,
      String projectId, Long requestedAcademyId, MultipartFile file) {
    requireEditorRole(principal);
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("업로드할 파일(file)은 필수입니다.");
    }
    if (file.getSize() > MAX_SOURCE_BYTES) {
      throw new IllegalArgumentException(
          "파일 크기가 상한(20MB)을 초과했습니다: " + file.getSize() + " bytes");
    }
    String sourceType = detectSourceType(file);
    long academyId = resolveAcademyId(principal, requestedAcademyId);

    Object lock = lockFor(academyId, validateProjectId(projectId));
    synchronized (lock) {
      Path dir = projectDir(academyId, projectId);
      ObjectNode project = loadProject(dir); // 없으면 404
      boolean converted = false;
      if ("html".equals(sourceType)) {
        // 정본 HTML 원본 보존
        Path htmlFile = dir.resolve(SOURCE_HTML);
        try {
          Files.write(htmlFile, file.getBytes());
        } catch (IOException e) {
          throw new UncheckedIOException("소스 HTML 저장 실패", e);
        }
        // headless Chromium으로 source.pdf 생성 (없으면 503)
        htmlToPdfConverter.convert(htmlFile, dir.resolve(SOURCE_PDF));
        converted = true;
      } else {
        Path pdfFile = dir.resolve(SOURCE_PDF);
        try {
          Files.write(pdfFile, file.getBytes());
        } catch (IOException e) {
          throw new UncheckedIOException("소스 PDF 저장 실패", e);
        }
        // PDF 직접 업로드 시 정본은 PDF 자체 — 불일치 가능한 이전 source.html 제거
        try {
          Files.deleteIfExists(dir.resolve(SOURCE_HTML));
        } catch (IOException e) {
          throw new UncheckedIOException("이전 소스 HTML 정리 실패", e);
        }
      }

      String now = nowIso();
      publishEvent(academyId, projectId,
          new ProjectEvent("source_uploaded", projectId,
              project.get("version").asInt(), now, principal.loginId(),
              "소스 등록 (" + sourceType + ")"));
      log.info("ArtCraft 소스 업로드: academy={} project={} type={} converted={} by={}",
          academyId, projectId, sourceType, converted, principal.loginId());
      return new SourceUploadResponse(projectId, academyId,
          project.get("version").asInt(), sourceType, SOURCE_PDF, converted);
    }
  }

  /**
   * 업로드 파일의 소스 종류를 판정한다.
   *
   * @return "html" 또는 "pdf"
   * @throws IllegalArgumentException 지원하지 않는 형식이면 400
   */
  private String detectSourceType(MultipartFile file) {
    String contentType = file.getContentType();
    String name = file.getOriginalFilename();
    String ext = "";
    if (name != null) {
      int dot = name.lastIndexOf('.');
      if (dot >= 0 && dot < name.length() - 1) {
        ext = name.substring(dot + 1).toLowerCase();
      }
    }
    if ("pdf".equals(ext) || "application/pdf".equalsIgnoreCase(contentType)) {
      return "pdf";
    }
    if ("html".equals(ext) || "htm".equals(ext)
        || "text/html".equalsIgnoreCase(contentType)
        || "application/xhtml+xml".equalsIgnoreCase(contentType)) {
      return "html";
    }
    throw new IllegalArgumentException(
        "지원하지 않는 파일 형식입니다. HTML(.html) 또는 PDF(.pdf)만 업로드할 수 있습니다.");
  }

  // -------------------------------------------------------------------------
  // AI 편집 (낙관적 잠금 + CLI 실행)
  // -------------------------------------------------------------------------

  /**
   * AI 편집 명령을 실행한다 (ADMIN/TUTOR).
   *
   * <ol>
   *   <li>{@code source.pdf} 존재 확인 — 없으면 409 (소스를 먼저 업로드해야 함)
   *   <li>{@code expectedVersion}과 현재 버전 비교 — 불일치 시 409 (편집 미수행)
   *   <li>명령 화이트리스트 검증 — 허용되지 않은 도구는 400
   *   <li>편집 전 스냅샷 저장 ({@code snapshots/v{current}.json})
   *   <li>{@code pdfcraft-cli run --script steps.json} 실행
   *   <li>버전+1, 히스토리 기록, SSE 알림
   * </ol>
   */
  public EditProjectResponse editProject(AuthenticatedPrincipal principal,
      String projectId, Long requestedAcademyId, EditProjectRequest request) {
    requireEditorRole(principal);
    if (request.expectedVersion() == null) {
      throw new IllegalArgumentException("expectedVersion은 필수입니다 (낙관적 잠금).");
    }
    if (request.commands() == null || request.commands().isEmpty()) {
      throw new IllegalArgumentException("commands는 1건 이상 필수입니다.");
    }
    for (EditCommandRequest cmd : request.commands()) {
      if (cmd.tool() == null || cmd.tool().isBlank()) {
        throw new IllegalArgumentException("명령의 tool은 필수입니다.");
      }
      if (!ALLOWED_TOOLS.contains(cmd.tool())) {
        throw new IllegalArgumentException(
            "허용되지 않은 도구입니다: " + cmd.tool()
                + " (허용: " + String.join(", ", ALLOWED_TOOLS) + ")");
      }
    }
    long academyId = resolveAcademyId(principal, requestedAcademyId);
    String actor = request.actor() == null || request.actor().isBlank()
        ? principal.loginId() : request.actor();

    Object lock = lockFor(academyId, validateProjectId(projectId));
    synchronized (lock) {
      Path dir = projectDir(academyId, projectId);
      ObjectNode project = loadProject(dir);
      // 편집 선행조건: 소스 PDF가 등록되어 있어야 한다 (P0-④)
      if (!Files.isRegularFile(dir.resolve(SOURCE_PDF))) {
        throw new ArtCraftSourceMissingException(projectId);
      }
      int current = project.get("version").asInt();
      String lastEditor = project.path("last_editor").asText("");
      if (current != request.expectedVersion()) {
        throw new ArtCraftVersionConflictException(request.expectedVersion(), current, lastEditor);
      }

      List<String> warnings = new ArrayList<>();
      if ("user".equals(lastEditor) && actor.startsWith("ai")) {
        warnings.add("사용자가 마지막으로 직접 편집한 프로젝트입니다. 자동 덮어쓰기 전 내용을 확인하세요.");
      }

      // 편집 전 스냅샷 (버전 n 상태 보존)
      snapshot(dir, project, actor,
          request.desc() == null ? "편집 전 스냅샷" : request.desc());

      // CLI 실행 (격리된 작업 디렉토리, 프로젝트 디렉토리를 프로세스 작업 디렉토리로)
      // - executor가 doc_open/doc 기본값/doc_save path를 자동 주입한다 (P0-③)
      // - step 실패는 예외가 아니라 CliResult.warnings로 반환된다
      Path workDir = dir.resolve(WORK_DIR).resolve(UUID.randomUUID().toString());
      ArtCraftCliExecutor.CliResult cliResult;
      try {
        cliResult = cliExecutor.execute(request.commands(), workDir, dir);
      } finally {
        deleteQuietly(workDir);
      }
      warnings.addAll(cliResult.warnings());

      String now = nowIso();
      int next = current + 1;
      project.put("version", next);
      project.put("updated_at", now);
      project.put("last_editor", actor);
      String pdfPath = detectPdfPath(dir, request.commands());
      if (pdfPath != null) {
        project.put("pdf_path", pdfPath);
      }
      ObjectNode historyEntry = objectMapper.createObjectNode();
      historyEntry.put("version", next);
      historyEntry.put("at", now);
      historyEntry.put("by", actor);
      historyEntry.put("desc", request.desc() == null ? "" : request.desc());
      ArrayNode tools = historyEntry.putArray("tools");
      for (EditCommandRequest cmd : request.commands()) {
        tools.add(cmd.tool());
      }
      ((ArrayNode) project.get("history")).add(historyEntry);
      saveProject(dir, project);

      publishEvent(academyId, projectId,
          new ProjectEvent("edited", projectId, next, now, actor,
              request.desc() == null ? "" : request.desc()));
      log.info("ArtCraft 프로젝트 편집: academy={} project={} v{}->v{} by={}",
          academyId, projectId, current, next, actor);
      return new EditProjectResponse(projectId, next, now,
          truncateCliOutput(cliResult.output()), warnings);
    }
  }

  // -------------------------------------------------------------------------
  // 스냅샷 / 되돌리기
  // -------------------------------------------------------------------------

  /** 버전 목록을 조회한다. */
  public List<SnapshotResponse> listSnapshots(AuthenticatedPrincipal principal,
      String projectId, Long requestedAcademyId) {
    long academyId = resolveAcademyId(principal, requestedAcademyId);
    Path snapDir = projectDir(academyId, validateProjectId(projectId)).resolve(SNAPSHOT_DIR);
    List<SnapshotResponse> result = new ArrayList<>();
    if (!Files.isDirectory(snapDir)) {
      return result;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(snapDir, "v*.json")) {
      for (Path snap : stream) {
        String name = snap.getFileName().toString(); // v{n}.json
        int version;
        try {
          version = Integer.parseInt(name.substring(1, name.length() - ".json".length()));
        } catch (NumberFormatException e) {
          continue;
        }
        ObjectNode node = (ObjectNode) objectMapper.readTree(snap.toFile());
        result.add(new SnapshotResponse(
            version,
            node.path("snapshot_by").asText(""),
            node.path("snapshot_desc").asText(""),
            node.path("snapshot_at").asText("")));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("스냅샷 목록 조회 실패", e);
    }
    result.sort(Comparator.comparingInt(SnapshotResponse::version));
    return result;
  }

  /**
   * 특정 버전으로 되돌린다 (ADMIN/TUTOR).
   *
   * <p>되돌리기 전 현재 상태를 {@code snapshots/v{current}.json}에 보존하므로
   * 되돌리기를 되돌리는 것도 가능하다. 되돌린 결과는 새 버전({@code current+1})이 된다.
   */
  public ProjectResponse revertProject(AuthenticatedPrincipal principal,
      String projectId, Long requestedAcademyId, RevertProjectRequest request) {
    requireEditorRole(principal);
    if (request.targetVersion() == null) {
      throw new IllegalArgumentException("targetVersion은 필수입니다.");
    }
    long academyId = resolveAcademyId(principal, requestedAcademyId);
    String actor = request.actor() == null || request.actor().isBlank()
        ? principal.loginId() : request.actor();

    Object lock = lockFor(academyId, validateProjectId(projectId));
    synchronized (lock) {
      Path dir = projectDir(academyId, projectId);
      ObjectNode current = loadProject(dir);
      int currentVersion = current.get("version").asInt();

      Path snapFile = dir.resolve(SNAPSHOT_DIR).resolve("v" + request.targetVersion() + ".json");
      if (!Files.isRegularFile(snapFile)) {
        throw new ArtCraftNotFoundException(
            "스냅샷을 찾을 수 없습니다: v" + request.targetVersion());
      }

      // 되돌리기 전 현재 상태 보존
      snapshot(dir, current, actor, "v" + request.targetVersion() + "으로 되돌리기 전 상태");

      ObjectNode restored;
      try {
        restored = (ObjectNode) objectMapper.readTree(snapFile.toFile());
      } catch (IOException e) {
        throw new UncheckedIOException("스냅샷 읽기 실패", e);
      }
      // 스냅샷 메타데이터 제거, 버전/추적 필드 갱신
      restored.remove(List.of("snapshot_by", "snapshot_desc", "snapshot_at"));
      String now = nowIso();
      int next = currentVersion + 1;
      restored.put("version", next);
      restored.put("updated_at", now);
      restored.put("last_editor", actor);
      ObjectNode historyEntry = objectMapper.createObjectNode();
      historyEntry.put("version", next);
      historyEntry.put("at", now);
      historyEntry.put("by", actor);
      historyEntry.put("desc",
          request.desc() == null ? "v" + request.targetVersion() + "으로 되돌리기" : request.desc());
      historyEntry.put("reverted_from", currentVersion);
      historyEntry.put("reverted_to", request.targetVersion());
      ArrayNode history = (ArrayNode) restored.get("history");
      if (history == null) {
        history = objectMapper.createArrayNode();
        restored.set("history", history);
      }
      history.add(historyEntry);
      saveProject(dir, restored);
      // 되돌린 상태도 스냅샷으로 보존 (버전 n = 상태 n 불변식 유지)
      snapshot(dir, restored, actor, "v" + request.targetVersion() + "으로 되돌린 상태");

      publishEvent(academyId, projectId,
          new ProjectEvent("reverted", projectId, next, now, actor,
              "v" + request.targetVersion() + "으로 되돌리기"));
      log.info("ArtCraft 프로젝트 되돌리기: academy={} project={} v{} -> v{} (기준 v{}) by={}",
          academyId, projectId, currentVersion, next, request.targetVersion(), actor);
      return toResponse(restored);
    }
  }

  // -------------------------------------------------------------------------
  // SSE / PDF
  // -------------------------------------------------------------------------

  /**
   * 프로젝트 변경 알림 SSE 스트림을 구독한다. 타임아웃은 설정값(기본 30분).
   * 구독 직후 {@code subscribed} 이벤트로 현재 버전을 전달한다.
   */
  public SseEmitter subscribe(AuthenticatedPrincipal principal,
      String projectId, Long requestedAcademyId) {
    long academyId = resolveAcademyId(principal, requestedAcademyId);
    ObjectNode project = loadProject(academyId, validateProjectId(projectId));
    String key = subscriptionKey(academyId, projectId);

    SseEmitter emitter = new SseEmitter(props.getSseTimeoutMillis());
    CopyOnWriteArrayList<SseEmitter> list =
        subscribers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
    list.add(emitter);
    Runnable cleanup = () -> {
      list.remove(emitter);
      if (list.isEmpty()) {
        subscribers.remove(key, list);
      }
    };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());
    try {
      emitter.send(SseEmitter.event()
          .name("project")
          .data(new ProjectEvent("subscribed", projectId,
              project.get("version").asInt(), nowIso(), "", "구독 시작")));
    } catch (IOException e) {
      cleanup.run();
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "SSE 초기 이벤트 전송 실패: " + e.getMessage(), e);
    }
    return emitter;
  }

  /** 프로젝트의 최신 PDF를 다운로드한다. */
  public PdfFile downloadPdf(AuthenticatedPrincipal principal,
      String projectId, Long requestedAcademyId) {
    long academyId = resolveAcademyId(principal, requestedAcademyId);
    Path dir = projectDir(academyId, validateProjectId(projectId));
    ObjectNode project = loadProject(dir);
    String pdfPath = project.path("pdf_path").asText(null);
    if (pdfPath == null || pdfPath.isBlank()) {
      throw new ArtCraftNotFoundException("생성된 PDF가 없습니다. 먼저 편집(edit)으로 PDF를 생성하세요.");
    }
    Path pdf = dir.resolve(pdfPath).normalize();
    if (!pdf.startsWith(dir.normalize()) || !Files.isRegularFile(pdf)) {
      throw new ArtCraftNotFoundException("PDF 파일을 찾을 수 없습니다: " + pdfPath);
    }
    try {
      return new PdfFile(Files.readAllBytes(pdf), projectId + ".pdf");
    } catch (IOException e) {
      throw new UncheckedIOException("PDF 읽기 실패", e);
    }
  }

  /** PDF 다운로드 결과. */
  public record PdfFile(byte[] bytes, String fileName) {
  }

  // -------------------------------------------------------------------------
  // 내부 헬퍼
  // -------------------------------------------------------------------------

  private void requireEditorRole(AuthenticatedPrincipal principal) {
    String role = principal.role();
    if (!"ADMIN".equals(role) && !"TUTOR".equals(role)) {
      throw new AccessDeniedException(
          "프로젝트 생성·편집·되돌리기는 ADMIN/TUTOR만 가능합니다 (현재 역할: " + role + ").");
    }
  }

  /**
   * JWT 인증 주체에서 학원 ID를 해석한다.
   *
   * <ul>
   *   <li>TUTOR — 본인 소유 학원 ({@code AcademyRepository.findByTutor_UserId}).
   *       요청이 다른 academyId를 지정하면 403.
   *   <li>ADMIN — 학원이 없으므로 {@code requestedAcademyId} 명시 필수.
   *   <li>그 외 — 소속 학원이 없어 403.
   * </ul>
   */
  private long resolveAcademyId(AuthenticatedPrincipal principal, Long requestedAcademyId) {
    if ("ADMIN".equals(principal.role())) {
      if (requestedAcademyId == null) {
        throw new IllegalArgumentException(
            "ADMIN은 대상 학원을 academyId 파라미터로 명시해야 합니다.");
      }
      return requestedAcademyId;
    }
    Long own = academyRepository.findByTutor_UserId(principal.userId())
        .map(Academy::getAcademyId)
        .orElse(null);
    if (own == null) {
      throw new AccessDeniedException("소속 학원을 찾을 수 없습니다.");
    }
    if (requestedAcademyId != null && !requestedAcademyId.equals(own)) {
      throw new AccessDeniedException("다른 학원의 프로젝트에는 접근할 수 없습니다.");
    }
    return own;
  }

  private String validateProjectId(String projectId) {
    if (projectId == null || !PROJECT_ID_PATTERN.matcher(projectId).matches()) {
      throw new IllegalArgumentException(
          "프로젝트 ID 형식이 올바르지 않습니다: " + projectId
              + " (허용: 영문/숫자로 시작, 영문·숫자·-·_ , 최대 64자)");
    }
    return projectId;
  }

  /**
   * 프로젝트 디렉토리를 반환한다. 정규화 후 storage-root 하위임을 검증하여
   * path traversal을 차단한다.
   */
  private Path projectDir(long academyId, String projectId) {
    Path root = Path.of(props.getStorageRoot()).toAbsolutePath().normalize();
    Path dir = root.resolve("artcraft-projects")
        .resolve(String.valueOf(academyId))
        .resolve(projectId)
        .normalize();
    if (!dir.startsWith(root)) {
      throw new AccessDeniedException("허용되지 않은 경로 접근입니다.");
    }
    return dir;
  }

  private ObjectNode loadProject(long academyId, String projectId) {
    return loadProject(projectDir(academyId, projectId));
  }

  private ObjectNode loadProject(Path dir) {
    Path file = dir.resolve(PROJECT_FILE);
    if (!Files.isRegularFile(file)) {
      throw new ArtCraftNotFoundException("프로젝트를 찾을 수 없습니다: " + dir.getFileName());
    }
    try {
      JsonNode node = objectMapper.readTree(file.toFile());
      if (!node.isObject()) {
        throw new ArtCraftNotFoundException("프로젝트 파일이 손상되었습니다: " + dir.getFileName());
      }
      return (ObjectNode) node;
    } catch (IOException e) {
      throw new UncheckedIOException("프로젝트 읽기 실패", e);
    }
  }

  /** 프로젝트 JSON을 원자적으로 저장한다 (tmp 기록 후 atomic move). */
  private void saveProject(Path dir, ObjectNode project) {
    Path target = dir.resolve(PROJECT_FILE);
    Path tmp = dir.resolve(PROJECT_FILE + ".tmp");
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), project);
      Files.move(tmp, target,
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException("프로젝트 저장 실패", e);
    }
  }

  /** 편집 전 프로젝트 JSON을 {@code snapshots/v{n}.json}에 보존한다. */
  private void snapshot(Path dir, ObjectNode project, String by, String desc) {
    int version = project.get("version").asInt();
    ObjectNode snap = project.deepCopy();
    snap.put("snapshot_by", by);
    snap.put("snapshot_desc", desc);
    snap.put("snapshot_at", nowIso());
    Path snapFile = dir.resolve(SNAPSHOT_DIR).resolve("v" + version + ".json");
    try {
      Files.createDirectories(snapFile.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(snapFile.toFile(), snap);
    } catch (IOException e) {
      throw new UncheckedIOException("스냅샷 저장 실패", e);
    }
  }

  /**
   * {@code doc_save} 명령의 출력 경로를 찾아 프로젝트 디렉토리 기준 상대 경로로 변환한다.
   * 프로젝트 디렉토리를 벗어나는 절대 경로는 무시된다 (격리 유지).
   *
   * <p>path를 생략한 {@code doc_save}는 {@link ArtCraftCliExecutor}가
   * {@code "output.pdf"}로 고정 주입하므로, 여기서도 같은 기본값을 적용한다.
   */
  private String detectPdfPath(Path dir, List<EditCommandRequest> commands) {
    for (EditCommandRequest cmd : commands) {
      if (!"doc_save".equals(cmd.tool()) || cmd.args() == null) {
        continue;
      }
      Object pathArg = cmd.args().get("path");
      String pathStr = (pathArg instanceof String s) && !s.isBlank() ? s : "output.pdf";
      Path resolved = dir.resolve(pathStr).normalize();
      if (resolved.startsWith(dir.normalize()) && Files.isRegularFile(resolved)) {
        return dir.relativize(resolved).toString();
      }
    }
    return null;
  }

  private void publishEvent(long academyId, String projectId, ProjectEvent event) {
    List<SseEmitter> list = subscribers.get(subscriptionKey(academyId, projectId));
    if (list == null || list.isEmpty()) {
      return;
    }
    for (SseEmitter emitter : list) {
      try {
        emitter.send(SseEmitter.event().name("project").data(event));
      } catch (Exception e) {
        list.remove(emitter);
        log.debug("ArtCraft SSE 구독자 제거 (전송 실패): {}", e.getMessage());
      }
    }
  }

  private ProjectResponse toResponse(ObjectNode project) {
    JsonNode items = project.get("items");
    return new ProjectResponse(
        project.get("project_id").asText(),
        project.get("academy_id").asLong(),
        project.path("name").asText(""),
        project.get("version").asInt(),
        project.path("created_by").asText(""),
        project.path("created_at").asText(""),
        project.path("updated_at").asText(""),
        project.path("last_editor").asText(""),
        items != null && items.isArray() ? items.size() : 0,
        project.path("pdf_path").isNull() ? null : project.path("pdf_path").asText(null));
  }

  private Object lockFor(long academyId, String projectId) {
    return projectLocks.computeIfAbsent(academyId + "/" + projectId, k -> new Object());
  }

  private String subscriptionKey(long academyId, String projectId) {
    return academyId + "/" + projectId;
  }

  private static String nowIso() {
    return Instant.now().toString();
  }

  private static String truncateCliOutput(String output) {
    if (output != null && output.length() > 4096) {
      return output.substring(0, 4096) + "...(truncated)";
    }
    return output == null ? "" : output;
  }

  private static void deleteQuietly(Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return;
    }
    try (var stream = Files.walk(dir)) {
      stream.sorted(Comparator.reverseOrder())
          .forEach(p -> {
            try {
              Files.deleteIfExists(p);
            } catch (IOException ignored) {
              // 작업 디렉토리 정리는 best-effort
            }
          });
    } catch (IOException ignored) {
      // 작업 디렉토리 정리는 best-effort
    }
  }
}

