package f2r.tutor_solution.private_tutor_solution_back.domain.agentmedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentMediaStoreTest {

  @TempDir Path dir;

  @Test
  void givenNoConfiguredFolder_whenReadingManifest_thenEveryAgentIsMissingAndNoFileIsServed() {
    // given
    AgentMediaStore store = new AgentMediaStore("");
    // when
    Map<String, Object> manifest = store.manifest();
    // then
    assertThat(manifest.get("configured")).isEqualTo(false);
    assertThat(agent(manifest, "seven").get("status")).isEqualTo("MISSING");
    assertThat(agent(manifest, "suni").get("status")).isEqualTo("MISSING");
    assertThat(store.find("suni", "idle.mp4")).isEmpty();
  }

  @Test
  void givenManifestStatuses_whenReadingManifest_thenUnknownOrAbsentStatusFallsBackToCandidate() throws Exception {
    // given
    Files.createDirectories(dir.resolve("seven"));
    Files.createDirectories(dir.resolve("suni"));
    Files.writeString(dir.resolve("seven/idle.mp4"), "v");
    Files.writeString(dir.resolve("suni/idle.mp4"), "v");
    Files.writeString(dir.resolve("suni/poster.jpg"), "p");
    Files.writeString(dir.resolve("manifest.json"), "{\"suni\":{\"status\":\"REFERENCE_APPROVED\"},\"seven\":{\"status\":\"FINAL!!\"}}");
    AgentMediaStore store = new AgentMediaStore(dir.toString());
    // when
    Map<String, Object> manifest = store.manifest();
    // then
    assertThat(manifest.get("configured")).isEqualTo(true);
    assertThat(agent(manifest, "suni").get("status")).isEqualTo("REFERENCE_APPROVED");
    assertThat(agent(manifest, "suni").get("files")).isEqualTo(List.of("idle.mp4", "poster.jpg"));
    assertThat(agent(manifest, "seven").get("status")).isEqualTo("CANDIDATE");
  }

  @Test
  void givenBrokenManifest_whenReadingManifest_thenFilesStillListedAsCandidate() throws Exception {
    // given
    Files.createDirectories(dir.resolve("seven"));
    Files.writeString(dir.resolve("seven/think.mp4"), "v");
    Files.writeString(dir.resolve("manifest.json"), "{not json");
    // when
    Map<String, Object> manifest = new AgentMediaStore(dir.toString()).manifest();
    // then
    assertThat(agent(manifest, "seven").get("status")).isEqualTo("CANDIDATE");
    assertThat(agent(manifest, "seven").get("files")).isEqualTo(List.of("think.mp4"));
  }

  @Test
  void givenFilesInsideAndOutsideFolder_whenFinding_thenOnlyAllowedNamesInsideFolderAreServed() throws Exception {
    // given
    Files.createDirectories(dir.resolve("suni"));
    Files.writeString(dir.resolve("suni/idle.mp4"), "v");
    Files.writeString(dir.resolve("suni/secret.txt"), "s");
    Files.writeString(dir.resolve("manifest.json"), "{}");
    AgentMediaStore store = new AgentMediaStore(dir.toString());
    // when / then
    assertThat(store.find("suni", "idle.mp4")).contains(dir.resolve("suni/idle.mp4").toAbsolutePath().normalize());
    assertThat(store.find("suni", "secret.txt")).isEmpty();
    assertThat(store.find("suni", "../manifest.json")).isEmpty();
    assertThat(store.find("..", "idle.mp4")).isEmpty();
    assertThat(store.find("doHyunwoo", "idle.mp4")).isEmpty();
    assertThat(store.find("suni", "think.mp4")).isEmpty();
  }

  @Test
  void givenRawStatus_whenNormalizing_thenOnlyKnownValuesPass() {
    // given / when / then
    assertThat(AgentMediaStore.statusOf("APPROVED")).isEqualTo("APPROVED");
    assertThat(AgentMediaStore.statusOf("REFERENCE_APPROVED")).isEqualTo("REFERENCE_APPROVED");
    assertThat(AgentMediaStore.statusOf("approved")).isEqualTo("CANDIDATE");
    assertThat(AgentMediaStore.statusOf("")).isEqualTo("CANDIDATE");
  }

  private static final byte[] MP4 = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0};
  private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
  private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

  private static byte[] zip(Map<String, byte[]> entries) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(out)) {
      for (Map.Entry<String, byte[]> e : entries.entrySet()) {
        z.putNextEntry(new ZipEntry(e.getKey()));
        z.write(e.getValue());
        z.closeEntry();
      }
    }
    return out.toByteArray();
  }

  @Test
  void givenPackageZip_whenInstalling_thenOnlyAllowedMediaAndCleanManifestAreWritten() throws Exception {
    // given: 제작 패키지와 같은 구조(최상위 폴더 + agent-media-private + 화면 캡처)
    byte[] pkg = zip(new java.util.LinkedHashMap<>(Map.of(
        "F2R_AGENT_DASHBOARD_V2/agent-media-private/seven/idle.mp4", MP4,
        "F2R_AGENT_DASHBOARD_V2/agent-media-private/suni/poster.jpg", JPG,
        "F2R_AGENT_DASHBOARD_V2/agent-media-private/suni/front.webp", WEBP,
        "F2R_AGENT_DASHBOARD_V2/agent-media-private/manifest.json",
        "{\"seven\":{\"status\":\"CANDIDATE\",\"note\":\"후보\"},\"suni\":{\"status\":\"REFERENCE_APPROVED\",\"x\":1},\"evil\":{}}".getBytes(StandardCharsets.UTF_8),
        "F2R_AGENT_DASHBOARD_V2/screens/m390-01-main.png", new byte[] {1, 2, 3},
        "F2R_AGENT_DASHBOARD_V2/agent-media-private/seven/secret.txt", new byte[] {1})));
    AgentMediaStore store = new AgentMediaStore(dir.toString());
    // when
    Map<String, Object> result = store.install(new ByteArrayInputStream(pkg));
    // then
    assertThat(agent(result, "seven").get("status")).isEqualTo("CANDIDATE");
    assertThat(agent(result, "suni").get("status")).isEqualTo("REFERENCE_APPROVED");
    assertThat(agent(result, "suni").get("files")).isEqualTo(List.of("front.webp", "poster.jpg"));
    assertThat(Files.exists(dir.resolve("seven/secret.txt"))).isFalse();
    assertThat(Files.exists(dir.resolve("screens"))).isFalse();
    String manifest = Files.readString(dir.resolve("manifest.json"));
    assertThat(manifest).contains("후보").doesNotContain("evil").doesNotContain("\"x\"");
  }

  @Test
  void givenFileWhoseContentDoesNotMatchName_whenInstalling_thenNothingIsWritten() throws Exception {
    // given
    byte[] pkg = zip(Map.of("seven/idle.mp4", "<html>".getBytes(StandardCharsets.UTF_8)));
    AgentMediaStore store = new AgentMediaStore(dir.toString());
    // when / then
    assertThatThrownBy(() -> store.install(new ByteArrayInputStream(pkg))).isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.exists(dir.resolve("seven/idle.mp4"))).isFalse();
  }

  @Test
  void givenManifestThatRaisesUnknownStatus_whenInstalling_thenStatusIsLoweredToCandidate() throws Exception {
    // given
    byte[] pkg = zip(Map.of("seven/idle.mp4", MP4, "manifest.json", "{\"seven\":{\"status\":\"FINAL\"}}".getBytes(StandardCharsets.UTF_8)));
    // when
    Map<String, Object> result = new AgentMediaStore(dir.toString()).install(new ByteArrayInputStream(pkg));
    // then
    assertThat(agent(result, "seven").get("status")).isEqualTo("CANDIDATE");
  }

  @Test
  void givenZipWithoutCharacterMedia_whenInstalling_thenRejected() throws Exception {
    // given
    byte[] pkg = zip(Map.of("README.txt", new byte[] {1}));
    // when / then
    assertThatThrownBy(() -> new AgentMediaStore(dir.toString()).install(new ByteArrayInputStream(pkg))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void givenNoConfiguredFolder_whenInstalling_thenNotConfiguredError() {
    // given / when / then
    assertThatThrownBy(() -> new AgentMediaStore("").install(new ByteArrayInputStream(new byte[0]))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void givenPublicAgents_whenReadingPublicManifest_thenOnlyListedAgentsAreExposed() throws Exception {
    // given
    Files.createDirectories(dir.resolve("seven"));
    Files.createDirectories(dir.resolve("suni"));
    Files.write(dir.resolve("seven/poster.jpg"), JPG);
    Files.write(dir.resolve("suni/poster.jpg"), JPG);
    // when
    AgentMediaStore none = new AgentMediaStore(dir.toString(), "");
    AgentMediaStore suniOnly = new AgentMediaStore(dir.toString(), " suni , unknown");
    // then
    assertThat((Map<?, ?>) none.publicManifest().get("agents")).isEmpty();
    assertThat(none.findPublic("suni", "poster.jpg")).isEmpty();
    assertThat(((Map<?, ?>) suniOnly.publicManifest().get("agents")).keySet()).containsExactly("suni");
    assertThat(suniOnly.findPublic("suni", "poster.jpg")).isPresent();
    assertThat(suniOnly.findPublic("seven", "poster.jpg")).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> agent(Map<String, Object> manifest, String key) {
    return (Map<String, Object>) ((Map<String, Object>) manifest.get("agents")).get(key);
  }
}
