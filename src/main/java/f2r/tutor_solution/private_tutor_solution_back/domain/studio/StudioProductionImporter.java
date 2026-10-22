package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Import an explicitly approved, immutable Git snapshot. No network or AI calls. */
@Service
@RequiredArgsConstructor
public class StudioProductionImporter {
  public static final String COMMIT = "09d62dee982e2e89e3653c90b1ee42f05bff4f9d";
  static final String PREFIX = "source-09d62dee-";
  private final StudioService studio;
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public record Source(String path, long size, String sha256) {}

  public record Catalog(
      String repository, String commit, List<Source> files, List<String> approvedSha256) {}

  public record Request(String commit, Map<String, String> files) {}

  public Catalog catalog() throws IOException {
    try (var in = new ClassPathResource("studio/production-09d62dee.json").getInputStream()) {
      return json.readValue(in, Catalog.class);
    }
  }

  static void admin(AuthenticatedPrincipal p) {
    if (p == null || !"ADMIN".equals(p.role()))
      throw StudioService.error(403, "관리자가 승인한 Git 자료 적재 전용입니다.");
  }

  static String key(String path) {
    return PREFIX
        + HexFormat.of()
            .formatHex(StudioService.digest().digest(path.getBytes(StandardCharsets.UTF_8)))
            .substring(0, 32);
  }

  public Object status(AuthenticatedPrincipal p) throws IOException {
    studio.require(p, false, false);
    var catalog = catalog();
    return Map.of(
        "commit",
        COMMIT,
        "expectedSourcePaths",
        536,
        "expectedUniqueFiles",
        316,
        "registeredSourcePaths",
        db.queryForObject(
            "SELECT COUNT(*) FROM studio_items WHERE project_id=? AND item_key LIKE ?",
            Long.class,
            StudioService.PROJECT,
            PREFIX + "%"),
        "originalComparedCuts",
        0,
        "confirmedPassCuts",
        0,
        "representativeImagesCreated",
        0,
        "handoffDocuments",
        catalog.files().stream().filter(f -> !f.path().contains("/")).toList());
  }

  @Transactional(rollbackFor = Exception.class)
  public Object register(AuthenticatedPrincipal p, Request request) throws IOException {
    admin(p);
    studio.require(p, true, true);
    if (request == null || !COMMIT.equals(request.commit()) || request.files() == null)
      throw StudioService.error(400, "고정된 #863 커밋과 검증한 파일 매핑이 필요합니다.");
    var catalog = catalog();
    var expected = new HashSet<String>();
    for (var f : catalog.files()) expected.add(f.sha256());
    if (!request.files().keySet().equals(expected))
      throw StudioService.error(400, "536개 경로의 고유 SHA 316개를 빠짐없이 연결하세요.");
    db.queryForList("SELECT id FROM studio_projects WHERE id=? FOR UPDATE", StudioService.PROJECT);
    var bytes = new HashMap<String, byte[]>();
    for (var f : catalog.files()) {
      var stored = studio.file(p, request.files().get(f.sha256()));
      if (!f.sha256().equals(stored.get("sha256"))
          || ((Number) stored.get("size_bytes")).longValue() != f.size())
        throw StudioService.error(409, "원본 SHA·크기 불일치: " + f.path());
      if (!bytes.containsKey(f.sha256())) {
        var data = studio.bytes(p, (String) stored.get("id"));
        if (!f.sha256().equals(HexFormat.of().formatHex(StudioService.digest().digest(data))))
          throw StudioService.error(409, "저장소 원본 SHA 불일치: " + f.path());
        bytes.put(f.sha256(), data);
      }
    }
    int created = 0;
    for (var f : catalog.files()) {
      String fileId = request.files().get(f.sha256());
      String version = version(f.path());
      String body =
          json.writeValueAsString(
              Map.of(
                  "repository",
                  catalog.repository(),
                  "commit",
                  COMMIT,
                  "sourcePath",
                  f.path(),
                  "sourceSha256",
                  f.sha256(),
                  "version",
                  version,
                  "sourceBytes",
                  f.size(),
                  "approvalEvidence",
                  "APPROVAL_HISTORY.md · MANIFEST.md",
                  "originalComparedCuts",
                  0,
                  "confirmedPassCuts",
                  0));
      if (append(
          p, key(f.path()), "git-09d62dee", "SCRIPT", null, null, f.path(), body, "", "", fileId))
        created++;
    }
    // Preserve each r14/r5 cut as a separate revision; rough panels remain unapproved references.
    var r14 = source(catalog, "01_r14_handoff_source/AI_AGENT_EP01_68컷_전체콘티_r14.html");
    var doc = org.jsoup.Jsoup.parse(new String(bytes.get(r14.sha256()), StandardCharsets.UTF_8));
    var sections = doc.select("section.cut");
    if (sections.size() != 68) throw StudioService.error(422, "r14 원본 68컷 구조가 아닙니다.");
    var r5 = source(catalog, "02_storyboard_deliverables/deliver_r5/cuts_r5.json");
    JsonNode cuts = json.readTree(bytes.get(r5.sha256()));
    if (!cuts.isArray() || cuts.size() != 68) throw StudioService.error(422, "r5 원본 68컷 구조가 아닙니다.");
    for (int n = 1; n <= 68; n++) {
      var panel =
          source(
              catalog,
              "02_storyboard_deliverables/panels_rough_68/cut_"
                  + String.format("%02d", n)
                  + ".png");
      String fileId = request.files().get(panel.sha256()),
          item = "ep01-cut-" + String.format("%02d", n);
      var section = sections.get(n - 1);
      if (!section.selectFirst("h2").text().startsWith("컷 " + String.format("%02d", n)))
        throw StudioService.error(422, "r14 컷 순서 불일치");
      if (append(
          p,
          item,
          "source-r14",
          "STORYBOARD",
          1,
          n,
          "EP01 " + n + "컷 · r14",
          section.text()
              + "\n\n[출처] "
              + r14.path()
              + "\n텍스트 SHA "
              + r14.sha256()
              + "\n러프 패널 SHA "
              + panel.sha256()
              + "\n정식 원작 대조 0/68 · PASS 확정 0 · 러프는 승인 이미지가 아닙니다.",
          section.select(".dialogue").text(),
          "r14 원문 보존 · 변경 승인 근거는 APPROVAL_HISTORY.md 참조",
          fileId)) created++;
      var cut = cuts.get(n - 1);
      if (cut.path("cut").asInt() != n) throw StudioService.error(422, "r5 컷 순서 불일치");
      String content =
          cut.path("composition_v3_screen_text").asText()
              + "\n\n동작 · "
              + cut.path("action_expression_r5").asText(cut.path("action_expression_r14").asText())
              + "\n\n[출처] "
              + r5.path()
              + "\n텍스트 SHA "
              + r5.sha256()
              + "\n러프 r1 SHA "
              + panel.sha256()
              + "\n정식 원작 대조 0/68 · PASS 확정 0 · 대표 10컷 영화 이미지 미생성";
      if (append(
          p,
          item,
          "source-r5",
          "STORYBOARD",
          1,
          n,
          "EP01 " + n + "컷 · r5",
          content,
          cut.path("dialogue_r5").isMissingNode()
              ? cut.path("dialogue_r14_field").asText()
              : cut.path("dialogue_r5").toString(),
          cut.path("camera_move").asText()
              + "\n"
              + cut.path("continuity_note_r5").asText(cut.path("continuity_note_r14").asText()),
          fileId)) created++;
    }
    String[] names = {
      "서수니 · 기준 얼굴 시트", "서수니 · S1·S2", "서수니 · S3·S4", "서수니 · S5·S6 v2", "용역 리더 · v2"
    };
    for (int i = 0; i < catalog.approvedSha256().size(); i++) {
      String sha = catalog.approvedSha256().get(i),
          file = request.files().get(sha),
          item = "character-approved-" + sha.substring(0, 12);
      if (append(
          p,
          item,
          "source-approved",
          "CHARACTER",
          null,
          null,
          names[i],
          "기존 사용자 승인 원본 · SHA "
              + sha
              + "\n출처 #863 "
              + COMMIT
              + "\n승인 근거 APPROVAL_HISTORY.md. 새 승인이나 컷 PASS 판정이 아닙니다. 원본·크롭·좌우·부상·의상 조건 보존.",
          "",
          "",
          file)) created++;
      var head = head(item);
      if (head.get("approved_id") == null)
        studio.review(
            p,
            (String) head.get("head_id"),
            "APPROVE",
            "기존 사용자 승인 이관 · #863 APPROVAL_HISTORY.md · SHA " + sha);
      else if (!head.get("approved_id").equals(head.get("head_id")))
        throw StudioService.error(409, "기존 승인 정본이 달라졌습니다. 자동 덮어쓰지 않습니다.");
    }
    // These are actual shared records, not an external agent's private session memory.
    var memory = new LinkedHashMap<String, Object>();
    memory.put("lastDecision", "#863 자료 536개 원본 보존. 정식 원작 대조 0/68·PASS 확정 0. 기존 승인 원본 5종만 승인 이관.");
    memory.put("currentAi", "Codex 개발 · Claude 독립 검수 대기");
    memory.put(
        "openIssues",
        "14화 바이블·시놉시스·설정 원문 미확보. S4 어깨 끈·붕대·인물/소품 연출 미확정. 대표 10컷 영화 이미지 미생성. 실제 ChatGPT·Claude 연결"
            + " NOT TESTED.");
    memory.put(
        "nextWork",
        "CURRENT_STATE.md·APPROVAL_HISTORY.md·MISSING_FILES.md 확인 후 사용자 판단. 신규 사건·대사·이미지 임의 생성/승격"
            + " 금지.");
    memory.put("commitSha", COMMIT);
    memory.put("pr", 863);
    memory.put(
        "sourceFileIds",
        catalog.files().stream()
            .filter(f -> !f.path().contains("/"))
            .map(f -> request.files().get(f.sha256()))
            .toList());
    if (head("project-memory").isEmpty())
      if (append(
          p,
          "project-memory",
          "source-09d62dee",
          "JOB",
          null,
          null,
          "프로젝트 공동 작업 기억",
          json.writeValueAsString(memory),
          "",
          "",
          null)) created++;
    return Map.of(
        "commit",
        COMMIT,
        "sourcePaths",
        536,
        "uniqueFiles",
        316,
        "duplicatePaths",
        220,
        "cuts",
        68,
        "approvedOriginals",
        5,
        "createdRevisions",
        created,
        "originalComparedCuts",
        0,
        "confirmedPassCuts",
        0,
        "representativeImagesCreated",
        0);
  }

  static String version(String path) {
    if (path.startsWith("01_")) return "r14";
    String[] parts = path.split("/");
    return parts.length > 1 ? parts[1] : "handoff";
  }

  private Source source(Catalog c, String path) {
    return c.files().stream()
        .filter(f -> f.path().equals(path))
        .findFirst()
        .orElseThrow(() -> StudioService.error(422, "필수 원본 경로 누락"));
  }

  private Map<String, Object> head(String key) {
    var rows =
        db.queryForList(
            "SELECT * FROM studio_items WHERE project_id=? AND item_key=?",
            StudioService.PROJECT,
            key);
    return rows.isEmpty() ? Map.of() : rows.getFirst();
  }

  private boolean append(
      AuthenticatedPrincipal p,
      String key,
      String label,
      String kind,
      Integer episode,
      Integer cut,
      String title,
      String content,
      String dialogue,
      String direction,
      String fileId) {
    var existing =
        db.queryForList(
            "SELECT file_id FROM studio_revisions WHERE project_id=? AND item_key=? AND label=?",
            StudioService.PROJECT,
            key,
            label);
    if (!existing.isEmpty()) {
      if (!Objects.equals(existing.getFirst().get("file_id"), fileId))
        throw StudioService.error(409, "기존 출처 버전의 파일이 다릅니다.");
      return false;
    }
    var current = head(key);
    // Never move a human-edited head during a later import retry.
    if (!current.isEmpty()
        && !key.startsWith(PREFIX)
        && !key.startsWith("character-approved-")
        && !label.equals("source-r5")) {
      var last = studio.revision(p, (String) current.get("head_id"));
      if (!((String) last.get("label")).startsWith("source-"))
        throw StudioService.error(409, "사용자가 변경한 자료는 자동 적재로 덮어쓰지 않습니다.");
    }
    studio.save(
        p,
        new StudioService.Revision(
            key,
            (String) current.get("head_id"),
            label,
            kind,
            episode,
            cut,
            "ORIGINAL",
            title.length() > 150 ? title.substring(title.length() - 150) : title,
            content,
            dialogue,
            direction,
            "REVIEW",
            fileId));
    return true;
  }
}
