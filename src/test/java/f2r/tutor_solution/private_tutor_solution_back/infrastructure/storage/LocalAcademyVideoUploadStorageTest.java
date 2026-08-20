package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Local Academy multipart storage — same contract as S3")
class LocalAcademyVideoUploadStorageTest {
  @TempDir Path temp;
  private LocalAcademyVideoUploadStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    storage = new LocalAcademyVideoUploadStorage(temp.resolve("uploads").toString());
    storage.initialize();
  }

  @Test
  @DisplayName("given ordered parts when completed then bytes assemble once with matching ETags")
  void completes_ordered_parts() throws Exception {
    // given
    String sessionId = UUID.randomUUID().toString();
    var initiated = storage.initiate(sessionId, 6, 2);
    String first = storage.uploadLocalPart(sessionId, initiated.uploadId(), 1,
        new ByteArrayInputStream("abc".getBytes()), 3);
    String second = storage.uploadLocalPart(sessionId, initiated.uploadId(), 2,
        new ByteArrayInputStream("def".getBytes()), 3);

    // when
    var completed = storage.complete(initiated.stagingKey(), initiated.uploadId(),
        List.of(new AcademyVideoUploadStorage.Part(1, first, null),
            new AcademyVideoUploadStorage.Part(2, second, null)), Duration.ofSeconds(10));

    // then
    assertThat(completed.sizeBytes()).isEqualTo(6);
    assertThat(storage.open(initiated.stagingKey(), Duration.ofSeconds(10)).readAllBytes())
        .isEqualTo("abcdef".getBytes());
    assertThat(storage.inspect(initiated.stagingKey(), initiated.uploadId(),
        Duration.ofSeconds(10))).isEqualTo(
            new AcademyVideoUploadStorage.StorageState(false, true));
  }

  @Test
  @DisplayName("given substituted ETag when complete then fail closed without producing object")
  void rejects_substituted_etag() throws Exception {
    // given
    String sessionId = UUID.randomUUID().toString();
    var initiated = storage.initiate(sessionId, 3, 1);
    storage.uploadLocalPart(sessionId, initiated.uploadId(), 1,
        new ByteArrayInputStream("abc".getBytes()), 3);

    // when / then
    assertThatThrownBy(() -> storage.complete(
        initiated.stagingKey(), initiated.uploadId(),
        List.of(new AcademyVideoUploadStorage.Part(1, "attacker", null)),
        Duration.ofSeconds(10)))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("MANIFEST_MISMATCH");
  }

  @Test
  @DisplayName("given traversal identity when addressed then cannot escape storage root")
  void rejects_path_traversal() {
    // given / when / then
    assertThatThrownBy(() -> storage.inspect(
        "staging/../../raw", UUID.randomUUID().toString(), Duration.ofSeconds(1)))
        .isInstanceOf(AcademyVideoStorageException.class)
        .hasMessage("INVALID_STORAGE_IDENTITY");
  }

  @Test
  @DisplayName("given local session when initiated then its directory is owner-only where POSIX exists")
  void session_directory_is_private() throws Exception {
    // given
    String sessionId = UUID.randomUUID().toString();
    var initiated = storage.initiate(sessionId, 1, 1);

    // when
    Path directory = temp.resolve("uploads/multipart")
        .resolve(sessionId).resolve(initiated.uploadId());

    // then
    if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
      assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)))
          .isEqualTo("rwx------");
    }
  }
}
