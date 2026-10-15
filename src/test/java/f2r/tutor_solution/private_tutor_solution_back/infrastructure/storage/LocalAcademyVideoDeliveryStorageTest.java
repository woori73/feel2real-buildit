package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAcademyVideoDeliveryStorageTest {
  @TempDir Path tempDir;

  @Test
  void resolvesAsyncStagingObjectAsSeekableChannel() throws Exception {
    Path asyncRoot = tempDir.resolve("async");
    Path object = asyncRoot.resolve("objects/11111111-1111-1111-1111-111111111111/raw");
    Files.createDirectories(object.getParent());
    Files.writeString(object, "0123456789");
    var storage = new LocalAcademyVideoDeliveryStorage(
        asyncRoot.toString(), tempDir.resolve("legacy").toString());

    var target = (AcademyVideoDeliveryStorage.LocalTarget) storage.resolve(
        "staging/11111111-1111-1111-1111-111111111111/raw",
        "video/webm", Duration.ofSeconds(30));

    try (var channel = target.channel()) {
      assertThat(target.length()).isEqualTo(10);
      assertThat(channel.position(4).position()).isEqualTo(4);
    }
  }

  @Test
  void resolvesLegacyAbsoluteObjectOnlyInsideConfiguredRoot() throws Exception {
    Path legacyRoot = tempDir.resolve("legacy");
    Files.createDirectories(legacyRoot);
    Path object = Files.writeString(legacyRoot.resolve("video.mp4"), "mp4");
    var storage = new LocalAcademyVideoDeliveryStorage(
        tempDir.resolve("async").toString(), legacyRoot.toString());

    var target = (AcademyVideoDeliveryStorage.LocalTarget) storage.resolve(
        object.toString(), "video/mp4", Duration.ofSeconds(30));

    target.channel().close();
    assertThat(target.length()).isEqualTo(3);
    assertThatThrownBy(() -> storage.resolve(
        tempDir.resolve("outside.mp4").toString(), "video/mp4", Duration.ofSeconds(30)))
        .isInstanceOf(java.io.IOException.class);
  }
}
