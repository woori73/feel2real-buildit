package f2r.tutor_solution.private_tutor_solution_back.domain.agentmedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path root;

  public AgentMediaStore(@Value("${app.agent-media.dir:}") String dir) {
    this.root = dir == null || dir.isBlank() ? null : Paths.get(dir).toAbsolutePath().normalize();
  }

  /** 대시보드가 첫 화면에서 읽는 캐릭터별 자료 상태. 파일 내용은 포함하지 않는다. */
  public Map<String, Object> manifest() {
    JsonNode manifest = readManifest();
    Map<String, Object> agents = new LinkedHashMap<>();
    for (String agent : AGENTS) {
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
