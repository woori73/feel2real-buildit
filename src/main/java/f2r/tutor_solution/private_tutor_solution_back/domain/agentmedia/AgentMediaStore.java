package f2r.tutor_solution.private_tutor_solution_back.domain.agentmedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * F2R AI 운영 대시보드의 캐릭터 영상·이미지(세븐·서수니)를 저장소 밖 비공개 폴더에서 읽는다.
 *
 * <p>캐릭터 얼굴 자료는 공개 GitHub 저장소에 올리지 않는다. 운영 서버의 {@code app.agent-media.dir}
 * 폴더에 두고, 로그인한 ADMIN·TUTOR에게만 이 서비스를 거쳐 제공한다. 폴더 구조:
 * <pre>
 *   {dir}/manifest.json        {"seven":{"status":"CANDIDATE"},"suni":{"status":"REFERENCE_APPROVED"}}
 *   {dir}/seven/idle.mp4 ...   허용 파일명만 읽는다(FILES).
 * </pre>
 * 승인 상태는 manifest가 정한다. manifest에 없거나 알 수 없는 값이면 CANDIDATE로 본다
 * (승인 전 자료를 최종 캐릭터로 표시하지 않기 위해 가장 보수적인 값을 쓴다).
 *
 * <p>로그인 전 홈페이지에는 {@code app.agent-media.public-agents}에 적은 캐릭터만 공개한다(기본: 없음).
 */
@Service
public class AgentMediaStore {

  public static final List<String> AGENTS = List.of("seven", "suni");
  /** 파일명 → Content-Type. 이 목록 밖의 이름은 경로 조작 여부와 관계없이 거부한다. */
  public static final Map<String, String> FILES = Map.of(
      "idle.mp4", "video/mp4",
      "think.mp4", "video/mp4",
      "speak.mp4", "video/mp4",
      "poster.jpg", "image/jpeg",
      "front.webp", "image/webp",
      "half.webp", "image/webp");
  static final Set<String> STATUSES = Set.of("APPROVED", "REFERENCE_APPROVED", "CANDIDATE");
  /** 자료 묶음 한도: 파일 하나 16MB, 풀었을 때 합계 64MB(압축 폭탄 차단). */
  static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
  static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
  static final int MAX_MANIFEST_BYTES = 16 * 1024;

  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path root;
  private final Set<String> publicAgents;

  public AgentMediaStore(String dir) {
    this(dir, "");
  }

  @Autowired
  public AgentMediaStore(
      @Value("${app.agent-media.dir:}") String dir,
      @Value("${app.agent-media.public-agents:}") String publicAgents) {
    this.root = dir == null || dir.isBlank() ? null : Paths.get(dir).toAbsolutePath().normalize();
    this.publicAgents = Set.copyOf(Arrays.stream(publicAgents == null ? new String[0] : publicAgents.split(","))
        .map(String::trim).filter(AGENTS::contains).toList());
  }

  /** 대시보드가 첫 화면에서 읽는 캐릭터별 자료 상태. 파일 내용은 포함하지 않는다. */
  public Map<String, Object> manifest() {
    return manifestOf(AGENTS);
  }

  /** 로그인 전 홈페이지용 상태. 공개 허용한 캐릭터만 담고, 나머지는 목록에 없다. */
  public Map<String, Object> publicManifest() {
    return manifestOf(AGENTS.stream().filter(publicAgents::contains).toList());
  }

  private Map<String, Object> manifestOf(List<String> agentKeys) {
    JsonNode manifest = readManifest();
    Map<String, Object> agents = new LinkedHashMap<>();
    for (String agent : agentKeys) {
      List<String> files = FILES.keySet().stream().sorted().filter(f -> find(agent, f).isPresent()).toList();
      String status = files.isEmpty() ? "MISSING" : statusOf(manifest.path(agent).path("status").asText(""));
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("status", status);
      row.put("files", files);
      agents.put(agent, row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("configured", root != null);
    out.put("agents", agents);
    return out;
  }

  /** 허용된 캐릭터·파일명이고 폴더 안에 실제로 있을 때만 경로를 돌려준다. */
  public Optional<Path> find(String agent, String file) {
    if (root == null || !AGENTS.contains(agent) || !FILES.containsKey(file)) return Optional.empty();
    Path path = root.resolve(agent).resolve(file).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path) || !Files.isReadable(path)) return Optional.empty();
    return Optional.of(path);
  }

  /** 공개 허용한 캐릭터의 자료만 돌려준다. 공개 목록이 비어 있으면 항상 비어 있다. */
  public Optional<Path> findPublic(String agent, String file) {
    return publicAgents.contains(agent) ? find(agent, file) : Optional.empty();
  }

  public boolean configured() {
    return root != null;
  }

  /**
   * 관리자가 올린 자료 묶음(ZIP)을 비공개 폴더에 설치한다.
   *
   * <p>ZIP 안의 위치와 관계없이 마지막 두 단계가 {@code {seven|suni}/{허용 파일명}}인 항목과
   * 맨 위 또는 {@code agent-media-private/} 바로 아래의 manifest.json만 쓴다. 그 밖의 항목(화면 캡처·설명서 등)은 무시한다.
   * 파일 내용이 확장자와 맞지 않으면 아무것도 바꾸지 않고 거부한다. manifest가 없으면 기존 승인 상태를 그대로 둔다.
   *
   * @return 설치 후 manifest()와 같은 상태 정보
   */
  public Map<String, Object> install(InputStream zip) throws IOException {
    if (root == null) throw new IllegalStateException("서버의 캐릭터 자료 폴더가 설정되지 않았습니다.");
    Map<String, byte[]> media = new LinkedHashMap<>();
    byte[] manifest = null;
    long total = 0;
    try (ZipInputStream in = new ZipInputStream(zip, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        String[] parts = Arrays.stream(entry.getName().replace('\\', '/').split("/")).filter(p -> !p.isEmpty()).toArray(String[]::new);
        if (parts.length == 0) continue;
        String name = parts[parts.length - 1];
        String parent = parts.length >= 2 ? parts[parts.length - 2] : "";
        if (name.equals("manifest.json") && (parts.length == 1 || parent.equals("agent-media-private"))) {
          manifest = read(in, MAX_MANIFEST_BYTES);
          total += manifest.length;
        } else if (AGENTS.contains(parent) && FILES.containsKey(name)) {
          byte[] bytes = read(in, MAX_FILE_BYTES);
          if (!matchesType(name, bytes)) throw new IllegalArgumentException(parent + "/" + name + " 파일 형식이 이름과 맞지 않습니다.");
          media.put(parent + "/" + name, bytes);
          total += bytes.length;
        }
        if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("자료 묶음이 너무 큽니다.");
      }
    }
    if (media.isEmpty()) throw new IllegalArgumentException("세븐·서수니 영상·이미지가 들어 있는 자료 묶음(ZIP)을 올려 주세요.");
    byte[] cleanManifest = manifest == null ? null : cleanManifest(manifest);
    for (Map.Entry<String, byte[]> file : media.entrySet()) writeAtomically(root.resolve(file.getKey()).normalize(), file.getValue());
    if (cleanManifest != null) writeAtomically(root.resolve("manifest.json"), cleanManifest);
    return manifest();
  }

  /** manifest는 캐릭터별 status·note만 남긴다. 알 수 없는 상태값은 CANDIDATE로 낮춘다(승인 상태를 올리지 않는다). */
  static byte[] cleanManifest(byte[] raw) {
    JsonNode node;
    try {
      node = JSON.readTree(raw);
    } catch (IOException e) {
      throw new IllegalArgumentException("manifest.json을 읽을 수 없습니다.");
    }
    if (node == null || !node.isObject()) throw new IllegalArgumentException("manifest.json 형식이 올바르지 않습니다.");
    ObjectNode out = JSON.createObjectNode();
    for (String agent : AGENTS) {
      JsonNode row = node.path(agent);
      if (!row.isObject()) continue;
      ObjectNode clean = out.putObject(agent);
      clean.put("status", statusOf(row.path("status").asText("")));
      if (row.path("note").isTextual()) clean.put("note", row.path("note").asText().strip());
    }
    try {
      return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(out);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 파일 앞부분으로 형식을 확인한다: MP4(ftyp), JPEG(FFD8FF), WebP(RIFF....WEBP). */
  static boolean matchesType(String name, byte[] b) {
    if (name.endsWith(".mp4")) return b.length > 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
    if (name.endsWith(".jpg")) return b.length > 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF;
    if (name.endsWith(".webp")) return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    return false;
  }

  private static byte[] read(InputStream in, long limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long n = 0;
    int r;
    while ((r = in.read(buf)) > 0) {
      n += r;
      if (n > limit) throw new IllegalArgumentException("자료 파일 하나가 너무 큽니다.");
      out.write(buf, 0, r);
    }
    return out.toByteArray();
  }

  private void writeAtomically(Path target, byte[] bytes) throws IOException {
    if (!target.startsWith(root)) throw new IllegalArgumentException("허용되지 않은 위치입니다.");
    Files.createDirectories(target.getParent());
    Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
    try {
      Files.write(tmp, bytes);
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  static String statusOf(String raw) {
    return STATUSES.contains(raw) ? raw : "CANDIDATE";
  }

  private JsonNode readManifest() {
    if (root == null) return JSON.createObjectNode();
    Path file = root.resolve("manifest.json");
    if (!Files.isRegularFile(file)) return JSON.createObjectNode();
    try {
      return JSON.readTree(Files.readString(file));
    } catch (IOException | RuntimeException e) {
      return JSON.createObjectNode();
    }
  }
}
