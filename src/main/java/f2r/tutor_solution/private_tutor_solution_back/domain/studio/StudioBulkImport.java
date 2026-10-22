package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Bounded in-memory ZIP reading only; never writes archive paths or overwrites originals. */
@Service
@RequiredArgsConstructor
public class StudioBulkImport {
  private final StudioService studio;
  private final JdbcTemplate db;
  private final ObjectMapper json;
  static final int MANIFEST_LIMIT = 2 * 1024 * 1024, EXPANDED_LIMIT = 20 * 1024 * 1024;

  @Transactional(rollbackFor = Exception.class)
  public Object importEp01(AuthenticatedPrincipal p, String fileId) throws IOException {
    studio.require(p, true, false);
    var source = studio.file(p, fileId);
    String ext = (String) source.get("extension");
    if (!Set.of("json", "zip").contains(ext))
      throw StudioService.error(400, "68컷 JSON 또는 manifest.json이 포함된 ZIP을 선택하세요.");
    if (((Number) source.get("size_bytes")).longValue() > EXPANDED_LIMIT)
      throw StudioService.error(413, "일괄 등록 원본은 20 MiB 이하입니다.");
    byte[] bytes = studio.bytes(p, fileId);
    var assets = new LinkedHashMap<String, byte[]>();
    byte[] manifest = bytes;
    if (ext.equals("zip")) {
      manifest = null;
      int total = 0, count = 0;
      try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          if (++count > 100) throw StudioService.error(413, "ZIP 항목은 100개 이하입니다.");
          String name = entry.getName();
          if (name.startsWith("/")
              || name.contains("\\")
              || name.contains(":")
              || Arrays.asList(name.split("/", -1)).contains(".."))
            throw StudioService.error(400, "ZIP 경로가 올바르지 않습니다.");
          byte[] value = zip.readNBytes(EXPANDED_LIMIT - total + 1);
          total += value.length;
          if (total > EXPANDED_LIMIT) throw StudioService.error(413, "ZIP 해제 크기는 총 20 MiB 이하입니다.");
          // Even a directory entry can carry compressed bytes. Count them before skipping it.
          if (entry.isDirectory()) continue;
          if (assets.putIfAbsent(name, value) != null)
            throw StudioService.error(400, "ZIP에 같은 이름이 중복되었습니다.");
          if (name.equals("manifest.json")) manifest = value;
        }
      }
      if (manifest == null) throw StudioService.error(400, "ZIP에 manifest.json이 필요합니다.");
    }
    if (manifest.length > MANIFEST_LIMIT) throw StudioService.error(413, "manifest는 2 MiB 이하입니다.");
    JsonNode root;
    try {
      root = json.readTree(manifest);
    } catch (Exception e) {
      throw StudioService.error(400, "JSON 형식을 확인하세요.");
    }
    if (!root.path("episode").canConvertToInt()
        || !root.path("episode").isIntegralNumber()
        || root.path("episode").asInt() != 1
        || !root.path("cuts").isArray()
        || root.path("cuts").size() != 68)
      throw StudioService.error(400, "EP01 1~68컷을 빠짐없이 입력하세요.");
    var revisions = new ArrayList<StudioService.Revision>();
    var numbers = new HashSet<Integer>();
    String label = "import-" + fileId.replace("-", "");
    for (var cut : root.path("cuts")) {
      int number = cut.path("cutNumber").asInt(-1);
      if (!cut.path("cutNumber").canConvertToInt()
          || !cut.path("cutNumber").isIntegralNumber()
          || number < 1
          || number > 68
          || !numbers.add(number)) throw StudioService.error(400, "컷 번호는 중복 없이 1~68입니다.");
      for (var field : cut.properties()) {
        if (!Set.of("cutNumber", "title", "content", "dialogue", "direction", "file")
                .contains(field.getKey())
            || (!field.getKey().equals("cutNumber") && !field.getValue().isTextual()))
          throw StudioService.error(400, "지원하는 컷 항목과 텍스트 형식을 확인하세요.");
      }
      String asset = cut.path("file").asText("");
      if (!asset.isBlank() && (!assets.containsKey(asset) || asset.equals("manifest.json")))
        throw StudioService.error(400, "ZIP에 연결할 원본 파일이 없습니다.");
      var r =
          new StudioService.Revision(
              "ep01-cut-" + String.format("%02d", number),
              null,
              label,
              "STORYBOARD",
              1,
              number,
              "ORIGINAL",
              cut.path("title").asText("EP01 " + number + "컷"),
              cut.path("content").asText(""),
              cut.path("dialogue").asText(""),
              cut.path("direction").asText(""),
              "REVIEW",
              fileId);
      StudioService.validate(r);
      revisions.add(r);
    }
    for (var r : revisions) {
      if (db.queryForObject(
              "SELECT COUNT(*) FROM studio_revisions WHERE project_id=? AND item_key=? AND label=?",
              Long.class,
              StudioService.PROJECT,
              r.itemKey(),
              r.label())
          > 0) throw StudioService.error(409, "이미 등록한 원본입니다. 새 원본 또는 새 버전 작성 기능을 사용하세요.");
    }
    for (var cut : root.path("cuts")) {
      String name = cut.path("file").asText("");
      if (name.isBlank()) continue;
      String suffix =
          name.contains(".")
              ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
              : "";
      if (!Set.of("pdf", "html", "json", "md", "zip", "png", "mp4").contains(suffix))
        throw StudioService.error(400, "연결 파일 형식을 확인하세요.");
      var temp = java.nio.file.Files.createTempFile("studio-validate-", ".bin");
      try {
        java.nio.file.Files.write(temp, assets.get(name));
        StudioService.validateType(temp, suffix);
      } finally {
        java.nio.file.Files.deleteIfExists(temp);
      }
    }
    // Validate all content before any append. One DB transaction covers all 68 revisions.
    db.queryForList("SELECT id FROM studio_projects WHERE id=? FOR UPDATE", StudioService.PROJECT);
    var result = new ArrayList<Object>();
    for (int i = 0; i < revisions.size(); i++) {
      var r = revisions.get(i);
      String asset = root.path("cuts").get(i).path("file").asText("");
      String linked = fileId;
      if (!asset.isBlank()) {
        var uploaded = studio.upload(p, new MemoryFile(asset, assets.get(asset)));
        linked = (String) ((Map<?, ?>) uploaded.get("file")).get("id");
      }
      var heads =
          db.queryForList(
              "SELECT head_id FROM studio_items WHERE project_id=? AND item_key=?",
              StudioService.PROJECT,
              r.itemKey());
      var saved =
          studio.save(
              p,
              new StudioService.Revision(
                  r.itemKey(),
                  heads.isEmpty() ? null : (String) heads.getFirst().get("head_id"),
                  r.label(),
                  r.kind(),
                  r.episode(),
                  r.cutNumber(),
                  r.origin(),
                  r.title(),
                  r.content(),
                  r.dialogue(),
                  r.direction(),
                  r.workStatus(),
                  linked));
      result.add(Map.of("id", saved.get("id"), "cutNumber", r.cutNumber()));
    }
    db.update(
        "INSERT INTO studio_events(project_id,action,note,actor_id) VALUES (?,?,?,?)",
        StudioService.PROJECT,
        "BULK_IMPORT",
        "source=" + fileId + ";cuts=68",
        p.userId());
    return Map.of(
        "sourceFileId",
        fileId,
        "sha256",
        source.get("sha256"),
        "registered",
        68,
        "approved",
        0,
        "revisions",
        result);
  }

  record MemoryFile(String filename, byte[] bytes) implements MultipartFile {
    public String getName() {
      return "file";
    }

    public String getOriginalFilename() {
      return filename;
    }

    public String getContentType() {
      return "application/octet-stream";
    }

    public boolean isEmpty() {
      return bytes.length == 0;
    }

    public long getSize() {
      return bytes.length;
    }

    public byte[] getBytes() {
      return bytes;
    }

    public InputStream getInputStream() {
      return new ByteArrayInputStream(bytes);
    }

    public void transferTo(java.io.File destination) throws IOException {
      java.nio.file.Files.write(destination.toPath(), bytes);
    }
  }
}
