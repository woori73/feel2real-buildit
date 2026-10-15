package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(
    name = "app.file.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalAcademyVideoDeliveryStorage implements AcademyVideoDeliveryStorage {
  private final Path asyncRoot;
  private final Path legacyRoot;

  @Autowired
  public LocalAcademyVideoDeliveryStorage(
      AcademyVideoUploadProperties properties,
      @Value("${app.file.upload-dir}") String legacyUploadDir) {
    this(properties.getLocalUploadDir(), legacyUploadDir);
  }

  LocalAcademyVideoDeliveryStorage(String asyncRoot, String legacyRoot) {
    this.asyncRoot = Path.of(asyncRoot).toAbsolutePath().normalize();
    this.legacyRoot = Path.of(legacyRoot).toAbsolutePath().normalize();
  }

  @Override
  public DeliveryTarget resolve(String key, String mediaType, Duration signedUrlTtl)
      throws IOException {
    Path path = resolvePath(key);
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
      throw new IOException("VIDEO_OBJECT_MISSING");
    }
    Path allowedRoot = Path.of(key).isAbsolute() ? legacyRoot : asyncRoot;
    if (!path.toRealPath().startsWith(allowedRoot.toRealPath())) {
      throw new IOException("INVALID_VIDEO_KEY");
    }
    SeekableByteChannel channel = Files.newByteChannel(
        path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    return new LocalTarget(channel, channel.size());
  }

  private Path resolvePath(String key) throws IOException {
    if (key == null || key.isBlank() || key.contains("..")) {
      throw new IOException("INVALID_VIDEO_KEY");
    }
    Path supplied = Path.of(key);
    if (supplied.isAbsolute()) {
      Path normalized = supplied.normalize();
      if (!normalized.startsWith(legacyRoot)) {
        throw new IOException("INVALID_VIDEO_KEY");
      }
      return normalized;
    }
    if (key.startsWith("staging/")) {
      String[] segments = key.split("/");
      if (segments.length != 3 || !"raw".equals(segments[2])) {
        throw new IOException("INVALID_VIDEO_KEY");
      }
      try {
        UUID.fromString(segments[1]);
      } catch (IllegalArgumentException e) {
        throw new IOException("INVALID_VIDEO_KEY", e);
      }
      return checkedAsyncPath(asyncRoot.resolve("objects").resolve(segments[1]).resolve("raw"));
    }
    return checkedAsyncPath(asyncRoot.resolve("objects").resolve(key));
  }

  private Path checkedAsyncPath(Path candidate) throws IOException {
    Path normalized = candidate.toAbsolutePath().normalize();
    Path objects = asyncRoot.resolve("objects").toAbsolutePath().normalize();
    if (!normalized.startsWith(objects)) {
      throw new IOException("INVALID_VIDEO_KEY");
    }
    return normalized;
  }
}
