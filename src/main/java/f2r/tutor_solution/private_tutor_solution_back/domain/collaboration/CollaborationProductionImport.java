package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static f2r.tutor_solution.private_tutor_solution_back.domain.collaboration.CollaborationService.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit local-only pinned source import. No startup job, HTTP endpoint or production switch. */
@Service
@Profile("local")
public class CollaborationProductionImport {
  public static final String REF = "09d62dee982e2e89e3653c90b1ee42f05bff4f9d";
  private static final Map<String, String> HANDOFF_SHA =
      Map.of(
          "APPROVAL_HISTORY.md",
          "763f2f84c6a865401e751d523191fd1289149d363c472dfa5856a2ed1ac2eff3",
          "CURRENT_STATE.md",
          "fde084731e1e9933a688b8fc2a0cff930d098a8ae91685aae93942d1c98b7461",
          "MANIFEST.md",
          "02627fc0d0c819500687c9973b586fade20ca3feec98b09569a901154047169f",
          "MISSING_FILES.md",
          "2202c349fc40e2f6ec37ac3376180718a058d77422b7c15ab71b873d690127e8");
  private final JdbcTemplate db;
  private final CollaborationWorkspaces spaces;
  private final CollaborationFiles files;
  private final FileStorage storage;
  private final DataSource dataSource;
  private final Environment env;
  private final TransactionTemplate tx;

  @Value("${app.collaboration.production-import.enabled:false}")
  private boolean enabled;

  private final com.fasterxml.jackson.databind.ObjectMapper json =
      new com.fasterxml.jackson.databind.ObjectMapper();

  public CollaborationProductionImport(
      JdbcTemplate db,
      CollaborationWorkspaces spaces,
      CollaborationFiles files,
      FileStorage storage,
      DataSource dataSource,
      Environment env,
      PlatformTransactionManager manager) {
    this.db = db;
    this.spaces = spaces;
    this.files = files;
    this.storage = storage;
    this.dataSource = dataSource;
    this.env = env;
    tx = new TransactionTemplate(manager);
    tx.setTimeout(180);
  }

  public record Source(
      String path,
      long size,
      String sha256,
      String version,
      String category,
      String sourceApproval) {}

  public record Plan(
      String sourceRef,
      String manifestSha,
      String inventorySha,
      int files,
      int materials,
      int handoffDocs,
      int uniqueSha,
      int duplicateSha,
      int checksumMetadata,
      int approvedReferences,
      int roughPanels,
      long bytes,
      List<Source> entries) {}

  private void isolated() {
    if (!enabled
        || !Arrays.asList(env.getActiveProfiles()).contains("local")
        || !(storage instanceof LocalFileStorage)) throw error(403, "명시적으로 허용된 로컬 격리 임포트만 가능합니다.");
    try (var connection = dataSource.getConnection()) {
      if (!connection
          .getMetaData()
          .getURL()
          .matches(
              "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/(?:f2r_cluster_test|f2r_collaboration_import_test)(?:\\?.*)?"))
        throw error(403, "격리 테스트 DB만 허용합니다.");
    } catch (java.sql.SQLException e) {
      throw error(503, "격리 DB 확인 실패");
    }
  }

  private static Path safe(Path root, String relative) throws java.io.IOException {
    Path path = root.resolve(relative).normalize();
    if (!path.startsWith(root)
        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
        || !path.toRealPath().equals(path)) throw error(400, "일반 원본 파일만 허용합니다: " + relative);
    return path;
  }

  private static String version(String path) {
    if (path.startsWith("01_")) return "r14";
    var m = Pattern.compile("/deliver(_r[0-9]+(?:_rev[0-9]+)?)?(?:/|\\.md5$)").matcher(path);
    return m.find()
        ? (m.group(1) == null ? "r1" : m.group(1).substring(1))
        : path.contains("panels_rough_68/") ? "r1" : "handoff";
  }

  public Plan dryRun(Path directory) {
    isolated();
    try {
      Path root = directory.toRealPath();
      var expected = new TreeMap<String, Map.Entry<Long, String>>();
      String manifest = Files.readString(safe(root, "MANIFEST.md"));
      var m =
          Pattern.compile("(?m)^\\| `([^`]+)` \\| ([0-9]+) \\| ([a-f0-9]{64}) \\|")
              .matcher(manifest);
      while (m.find())
        if (expected.put(m.group(1), Map.entry(Long.parseLong(m.group(2)), m.group(3))) != null)
          throw error(400, "중복 출처 경로");
      if (expected.size() != 532) throw error(400, "자료532개가 필요합니다.");
      for (var handoff : HANDOFF_SHA.entrySet())
        expected.put(
            handoff.getKey(),
            Map.entry(Files.size(safe(root, handoff.getKey())), handoff.getValue()));
      var actual = new TreeSet<String>();
      try (var stream = Files.walk(root)) {
        for (var p : stream.filter(p -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList())
          actual.add(root.relativize(p).toString().replace('\\', '/'));
      }
      if (!actual.equals(expected.keySet())) throw error(400, "누락 또는 추가 파일이 있습니다.");
      var approved = new HashSet<String>();
      var ref = Pattern.compile("(?m)^\\| ([a-f0-9]{64}) \\|").matcher(manifest);
      while (ref.find()) approved.add(ref.group(1));
      if (approved.size() != 5) throw error(400, "승인 참조5종을 확인하세요.");
      var entries = new ArrayList<Source>();
      var unique = new HashSet<String>();
      var found = new HashSet<String>();
      long total = 0;
      int checksum = 0, panels = 0;
      StringBuilder inventory = new StringBuilder();
      for (var item : expected.entrySet()) {
        String path = item.getKey();
        byte[] bytes = Files.readAllBytes(safe(root, path));
        String sha = CollaborationFiles.sha(bytes);
        if (bytes.length != item.getValue().getKey() || !sha.equals(item.getValue().getValue()))
          throw error(422, "원본 SHA/크기 불일치: " + path);
        String ext = CollaborationFiles.extension(path);
        String category =
            HANDOFF_SHA.containsKey(path)
                ? "HANDOFF"
                : ext.equals("md5")
                    ? "CHECKSUM_METADATA"
                    : path.contains("panels_rough_68/")
                        ? "STORYBOARD_PANEL"
                        : ext.equals("png")
                            ? "CHARACTER_IMAGE"
                            : ext.equals("zip") ? "PACKAGE" : "PRODUCTION_DOCUMENT";
        if (ext.equals("md5")) checksum++;
        if (path.contains("panels_rough_68/") && ext.equals("png")) panels++;
        if (approved.contains(sha)) found.add(sha);
        entries.add(
            new Source(
                path,
                bytes.length,
                sha,
                version(path),
                category,
                approved.contains(sha) ? "APPROVED_REFERENCE" : "SOURCE_UNREVIEWED"));
        unique.add(sha);
        total += bytes.length;
        inventory
            .append(path)
            .append('\0')
            .append(bytes.length)
            .append('\0')
            .append(sha)
            .append('\n');
      }
      if (found.size() != 5 || panels != 68 || checksum != 12)
        throw error(422, "참조/68컷/메타자료가 불완전합니다.");
      return new Plan(
          REF,
          CollaborationFiles.sha(manifest.getBytes(StandardCharsets.UTF_8)),
          CollaborationFiles.sha(inventory.toString().getBytes(StandardCharsets.UTF_8)),
          536,
          532,
          4,
          unique.size(),
          536 - unique.size(),
          checksum,
          found.size(),
          panels,
          total,
          List.copyOf(entries));
    } catch (java.io.IOException e) {
      throw error(422, "원본 디렉터리를 읽을 수 없습니다.");
    }
  }

  public Map<String, Object> apply(AuthenticatedPrincipal p, String workspace, Path root) {
    isolated();
    spaces.require(p, workspace, true, true);
    Plan plan = dryRun(root);
    return tx.execute(
        t -> {
          db.queryForList(
              "SELECT id FROM ai_collaboration_workspaces WHERE id=? FOR UPDATE", workspace);
          var prior =
              db.queryForList(
                  "SELECT id FROM ai_collaboration_imports WHERE workspace_id=? AND source_ref=?"
                      + " AND inventory_sha=? AND status='COMPLETED'",
                  workspace,
                  REF,
                  plan.inventorySha());
          if (!prior.isEmpty()) return verify(p, prior.getFirst().get("id").toString(), plan, true);
          String batch = UUID.randomUUID().toString();
          db.update(
              "INSERT INTO"
                  + " ai_collaboration_imports(id,workspace_id,source_ref,manifest_sha,inventory_sha,expected_files,registered_by,status)"
                  + " VALUES(?,?,?,?,?,536,?,'IMPORTING')",
              batch,
              workspace,
              REF,
              plan.manifestSha(),
              plan.inventorySha(),
              p.userId());
          var ids = new HashMap<String, String>();
          for (var entry : plan.entries()) {
            byte[] bytes;
            try {
              bytes = Files.readAllBytes(safe(root.toRealPath(), entry.path()));
            } catch (java.io.IOException e) {
              throw error(422, "원본 읽기 실패");
            }
            if (!CollaborationFiles.sha(bytes).equals(entry.sha256()))
              throw error(422, "임포트 중 원본이 변경되었습니다.");
            var file =
                files.store(
                    p,
                    workspace,
                    Path.of(entry.path()).getFileName().toString(),
                    bytes,
                    null,
                    "CODEX",
                    null);
            String id = file.get("id").toString();
            ids.put(entry.path(), id);
            db.update(
                "INSERT INTO"
                    + " ai_collaboration_sources(id,import_id,file_id,source_path,version_label,category,source_approval)"
                    + " VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                batch,
                id,
                entry.path(),
                entry.version(),
                entry.category(),
                entry.sourceApproval());
          }
          try {
            var cuts =
                json.readTree(
                    Files.readAllBytes(
                        safe(
                            root.toRealPath(),
                            "02_storyboard_deliverables/deliver_r5/cuts_r5.json")));
            var numbers = new HashSet<Integer>();
            for (var cut : cuts) {
              int n = cut.path("cut").asInt();
              if (n < 1 || n > 68 || !numbers.add(n)) throw error(422, "컷 번호 오류");
              String panel =
                  ids.get(
                      String.format("02_storyboard_deliverables/panels_rough_68/cut_%02d.png", n));
              if (panel == null) throw error(422, "컷 패널 누락");
              db.update(
                  "INSERT INTO"
                      + " ai_collaboration_storyboard_cuts(import_id,cut_number,panel_file_id,record_json,comparison_status,pass_confirmed)"
                      + " VALUES(?,?,?,?,'NOT_COMPARED',FALSE)",
                  batch,
                  n,
                  panel,
                  json.writeValueAsString(cut));
            }
            if (numbers.size() != 68) throw error(422, "68컷이 필요합니다.");
            for (var handoff :
                List.of("CURRENT_STATE.md", "APPROVAL_HISTORY.md", "MISSING_FILES.md")) {
              String content = Files.readString(safe(root.toRealPath(), handoff));
              if (content.length() > 3800) throw error(422, "인계 기록 길이 확인 필요");
              spaces.memory(
                  p,
                  workspace,
                  handoff.startsWith("APPROVAL")
                      ? "DECISION"
                      : handoff.startsWith("CURRENT") ? "MILESTONE" : "NOTE",
                  "[출처 인계 문서 / " + handoff + " / 파일 " + ids.get(handoff) + "]\n" + content,
                  "CODEX");
            }
          } catch (java.io.IOException e) {
            throw error(422, "컷/인계 문서 읽기 실패");
          }
          spaces.memory(
              p,
              workspace,
              "MILESTONE",
              "실자료536개 등록 / 출처 #863 "
                  + REF
                  + " / 모두 DRAFT / 기존 승인 참조5종은 출처 정보 보존 / 정식 원작 대조0/68·PASS0 / 다음 작업:"
                  + " CURRENT_STATE의 미해결 기본안 확인. 모델 생성·사용자 승인·운영 배포는 미실행.",
              "CODEX");
          db.update(
              "UPDATE ai_collaboration_imports SET registered_files=536,status='COMPLETED' WHERE"
                  + " id=?",
              batch);
          return verify(p, batch, plan, false);
        });
  }

  private Map<String, Object> verify(
      AuthenticatedPrincipal p, String batch, Plan plan, boolean repeated) {
    var rows =
        db.queryForList(
            "SELECT"
                + " s.source_path,s.version_label,s.category,s.source_approval,f.id,f.sha256,f.size_bytes,f.status"
                + " FROM ai_collaboration_sources s JOIN ai_collaboration_files f ON f.id=s.file_id"
                + " WHERE s.import_id=? ORDER BY s.source_path",
            batch);
    if (rows.size() != plan.files()) throw error(409, "등록 자료 수가 다릅니다.");
    var expectedByPath = new HashMap<String, Source>();
    for (var source : plan.entries()) expectedByPath.put(source.path(), source);
    for (var row : rows) {
      var expected = expectedByPath.get(row.get("source_path").toString());
      if (expected == null) throw error(409, "등록 출처 경로가 다릅니다.");
      if (!row.get("source_path").equals(expected.path())
          || !row.get("sha256").equals(expected.sha256())
          || number(row, "size_bytes") != expected.size()
          || !row.get("version_label").equals(expected.version())
          || !row.get("category").equals(expected.category())
          || !row.get("source_approval").equals(expected.sourceApproval()))
        throw error(409, "등록 자료 무결성 오류");
      if (!CollaborationFiles.sha(files.bytes(p, row.get("id").toString()))
          .equals(expected.sha256())) throw error(409, "저장 원본 무결성 오류");
    }
    return Map.of(
        "importId",
        batch,
        "sourceRef",
        REF,
        "registered",
        rows.size(),
        "uniqueSha",
        plan.uniqueSha(),
        "duplicateSha",
        plan.duplicateSha(),
        "shaVerified",
        rows.size(),
        "idempotentReplay",
        repeated,
        "canonicalComparedCuts",
        0,
        "canonicalPass",
        0);
  }
}
