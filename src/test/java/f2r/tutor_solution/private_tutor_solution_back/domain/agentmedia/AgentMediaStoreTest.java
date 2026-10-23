package f2r.tutor_solution.private_tutor_solution_back.domain.agentmedia;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> agent(Map<String, Object> manifest, String key) {
    return (Map<String, Object>) ((Map<String, Object>) manifest.get("agents")).get(key);
  }
}
