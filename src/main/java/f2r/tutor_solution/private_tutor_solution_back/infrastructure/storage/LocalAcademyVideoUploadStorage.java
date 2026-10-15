package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(
    name = "app.file.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalAcademyVideoUploadStorage
    implements AcademyVideoUploadStorage, AcademyVideoWorkerStorage {
  static final long PART_SIZE_BYTES = AcademyVideoUploadProperties.REQUIRED_PART_SIZE_BYTES;
  private static final long MAX_RAW_BYTES = AcademyVideoUploadProperties.REQUIRED_MAX_RAW_BYTES;

  private final Path root;
  private final long partSizeBytes;
  private final long maxRawBytes;

  @Autowired
  public LocalAcademyVideoUploadStorage(AcademyVideoUploadProperties properties) {
    this(properties.getLocalUploadDir(), properties.getUploadPartSizeBytes(),
        properties.getMaxRawBytes());
  }

  LocalAcademyVideoUploadStorage(String root) {
    this(root, PART_SIZE_BYTES, MAX_RAW_BYTES);
  }

  private LocalAcademyVideoUploadStorage(String root, long partSizeBytes, long maxRawBytes) {
    this.root = canonicalRoot(Path.of(root));
    this.partSizeBytes = partSizeBytes;
    this.maxRawBytes = maxRawBytes;
  }

  private static Path canonicalRoot(Path requested) {
    Path absolute = requested.toAbsolutePath().normalize();
    try {
      Path parent = absolute.getParent();
      return parent == null
          ? absolute
          : parent.toRealPath().resolve(absolute.getFileName()).normalize();
    } catch (IOException e) {
      throw new IllegalArgumentException("upload root parent is unavailable");
    }
  }

  @PostConstruct
  void initialize() throws IOException {
    createPrivateDirectoryAtomically(root);
  }

  @Override
  public UploadSession initiate(String sessionId, long declaredSizeBytes, int expectedPartCount) {
    String uploadId = UUID.randomUUID().toString();
    String stagingKey = "staging/" + sessionId + "/raw";
    try {
      createPrivateDirectoryAtomically(root.resolve("multipart"));
      createPrivateDirectoryAtomically(root.resolve("multipart").resolve(sessionId));
      createPrivateDirectoryAtomically(uploadDirectory(sessionId, uploadId));
    } catch (IOException e) {
      throw new AcademyVideoStorageException("LOCAL_INIT_FAILED");
    }
    List<String> urls = new ArrayList<>(expectedPartCount);
    for (int part = 1; part <= expectedPartCount; part++) {
      urls.add("/api/tutor/academy/videos/uploads/" + sessionId
          + "/parts/" + part + "?capability=" + uploadId);
    }
    return new UploadSession(stagingKey, uploadId, List.copyOf(urls));
  }

  @Override
  public String uploadLocalPart(
      String sessionId, String uploadId, int partNumber, InputStream body, long contentLength)
      throws IOException {
    if (partNumber < 1 || partNumber > 10 || contentLength < 1
        || contentLength > partSizeBytes) {
      throw new AcademyVideoStorageException("INVALID_PART");
    }
    Path directory = uploadDirectory(sessionId, uploadId);
    requirePrivateRealDirectory(directory);
    Path target = directory.resolve(String.format("part-%02d", partNumber));
    Path temporary = directory.resolve(String.format(".part-%02d-%s", partNumber, UUID.randomUUID()));
    long copied = copyBounded(body, temporary, partSizeBytes, Long.MAX_VALUE);
    if (copied != contentLength) {
      Files.deleteIfExists(temporary);
      throw new AcademyVideoStorageException("PART_LENGTH_MISMATCH");
    }
    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
    return localEtag(target);
  }

  @Override
  public StorageState inspect(String stagingKey, String uploadId, Duration remaining) {
    requireRemaining(deadlineNanos(remaining));
    String sessionId = sessionIdFromKey(stagingKey);
    return new StorageState(
        Files.isDirectory(uploadDirectory(sessionId, uploadId), LinkOption.NOFOLLOW_LINKS),
        Files.isRegularFile(objectPath(stagingKey), LinkOption.NOFOLLOW_LINKS));
  }

  @Override
  public CompletedObject complete(
      String stagingKey, String uploadId, List<Part> orderedParts, Duration remaining) {
    String sessionId = sessionIdFromKey(stagingKey);
    Path directory = uploadDirectory(sessionId, uploadId);
    Path object = objectPath(stagingKey);
    long deadlineNanos = deadlineNanos(remaining);
    try {
      requireRemaining(deadlineNanos);
      requirePrivateRealDirectory(directory);
      createPrivateDirectoryAtomically(root.resolve("objects"));
      createPrivateDirectoryAtomically(root.resolve("objects").resolve(sessionId));
      createPrivateDirectoryAtomically(object.getParent());
      Path temporary = object.resolveSibling(".raw-" + UUID.randomUUID());
      long total = 0;
      try (OutputStream out = privateOutputStream(temporary)) {
        int expected = 1;
        for (Part part : orderedParts) {
          requireRemaining(deadlineNanos);
          if (part.partNumber() != expected++) {
            throw new AcademyVideoStorageException("MANIFEST_INVALID");
          }
          Path partPath = directory.resolve(String.format("part-%02d", part.partNumber()));
          if (!Files.isRegularFile(partPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new AcademyVideoStorageException("PART_MISSING");
          }
          if (!normalizeEtag(localEtag(partPath)).equals(normalizeEtag(part.eTag()))) {
            throw new AcademyVideoStorageException("MANIFEST_MISMATCH");
          }
          try (InputStream in = Files.newInputStream(partPath)) {
            total += copyBounded(in, out, maxRawBytes - total, deadlineNanos);
          }
          if (total > maxRawBytes) {
            throw new AcademyVideoStorageException("RAW_TOO_LARGE");
          }
        }
      } catch (RuntimeException | IOException e) {
        Files.deleteIfExists(temporary);
        throw e;
      }
      requireRemaining(deadlineNanos);
      Files.move(temporary, object, StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      deleteTree(directory);
      return new CompletedObject(localEtag(object), total);
    } catch (AcademyVideoStorageException e) {
      throw e;
    } catch (IOException e) {
      throw new AcademyVideoStorageException("LOCAL_COMPLETE_FAILED");
    }
  }

  @Override
  public ObjectMetadata head(String stagingKey, Duration remaining) {
    requireRemaining(deadlineNanos(remaining));
    Path object = objectPath(stagingKey);
    try {
      if (!Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS)) {
        throw new AcademyVideoStorageException("OBJECT_MISSING");
      }
      return new ObjectMetadata(localEtag(object), Files.size(object));
    } catch (IOException e) {
      throw new AcademyVideoStorageException("LOCAL_HEAD_FAILED");
    }
  }

  @Override
  public InputStream open(String stagingKey, Duration remaining) throws IOException {
    requireRemaining(deadlineNanos(remaining));
    Path object = objectPath(stagingKey);
    if (!Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS)) {
      throw new AcademyVideoStorageException("OBJECT_MISSING");
    }
    return Files.newInputStream(object);
  }

  @Override
  public void abort(String stagingKey, String uploadId) {
    try {
      abortForCleanup(stagingKey, uploadId);
    } catch (AcademyVideoStorageException ignored) {
      // Request-path compensation is best effort; the durable cleanup outbox retries strictly.
    }
  }

  @Override
  public void abortForCleanup(String stagingKey, String uploadId) {
    try {
      deleteTree(uploadDirectory(sessionIdFromKey(stagingKey), uploadId));
      Files.deleteIfExists(objectPath(stagingKey));
    } catch (IOException e) {
      throw new AcademyVideoStorageException("LOCAL_ABORT_FAILED");
    }
  }

  @Override
  public void materialize(String key, Path target, Duration remaining) throws IOException {
    long deadlineNanos = deadlineNanos(remaining);
    Path source = workerObjectPath(key);
    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
      throw new AcademyVideoStorageException("OBJECT_MISSING");
    }
    Files.createDirectories(target.toAbsolutePath().normalize().getParent());
    try (InputStream in = Files.newInputStream(source);
         OutputStream out = Files.newOutputStream(
             target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      copyBounded(in, out, maxRawBytes, deadlineNanos);
    }
  }

  @Override
  public StoredObject upload(
      String key, Path source, String mediaType, Duration remaining) throws IOException {
    long deadlineNanos = deadlineNanos(remaining);
    Path target = workerObjectPath(key);
    createPrivateDirectoryAtomically(root.resolve("objects"));
    Path relative = root.resolve("objects").relativize(target);
    Path current = root.resolve("objects");
    for (int i = 0; i < relative.getNameCount() - 1; i++) {
      current = current.resolve(relative.getName(i));
      createPrivateDirectoryAtomically(current);
    }
    Path temporary = target.resolveSibling("." + target.getFileName() + "-" + UUID.randomUUID());
    try (InputStream in = Files.newInputStream(source);
         OutputStream out = privateOutputStream(temporary)) {
      copyBounded(in, out, maxRawBytes, deadlineNanos);
    }
    requireRemaining(deadlineNanos);
    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
    return new StoredObject(Files.size(target));
  }

  @Override
  public StoredObject inspectWorkerObject(String key, Duration remaining) {
    requireRemaining(deadlineNanos(remaining));
    Path object = workerObjectPath(key);
    try {
      if (!Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS)) {
        throw new AcademyVideoStorageException("OBJECT_MISSING");
      }
      return new StoredObject(Files.size(object));
    } catch (IOException e) {
      throw new AcademyVideoStorageException("LOCAL_HEAD_FAILED");
    }
  }

  @Override
  public void deleteWorkerObject(String key) {
    try {
      Files.deleteIfExists(workerObjectPath(key));
    } catch (IOException e) {
      throw new AcademyVideoStorageException("LOCAL_DELETE_FAILED");
    }
  }

  @Override
  public List<StoredKey> listWorkerObjects(
      String deterministicPrefix, Instant olderThan, int limit) {
    if (olderThan == null || limit < 1 || limit > 1_000) {
      throw new AcademyVideoStorageException("INVALID_RECONCILIATION_REQUEST");
    }
    Path objects = root.resolve("objects");
    if (!Files.isDirectory(objects, LinkOption.NOFOLLOW_LINKS)) return List.of();
    try (var paths = Files.walk(objects)) {
      return paths
          .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .map(path -> toStoredKey(objects, path))
          .filter(found -> found != null
              && found.key().startsWith(deterministicPrefix)
              && found.lastModified().isBefore(olderThan))
          .sorted(Comparator.comparing(StoredKey::key))
          .limit(limit)
          .toList();
    } catch (IOException e) {
      throw new AcademyVideoStorageException("LOCAL_LIST_FAILED");
    }
  }

  private StoredKey toStoredKey(Path objects, Path path) {
    try {
      String relative = objects.relativize(path).toString().replace('\\', '/');
      String key = relative.matches("[0-9a-fA-F\\-]{36}/raw")
          ? "staging/" + relative
          : relative;
      return new StoredKey(key, Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
          .toInstant());
    } catch (IOException e) {
      return null;
    }
  }

  private Path uploadDirectory(String sessionId, String uploadId) {
    requireUuid(sessionId);
    requireUuid(uploadId);
    return root.resolve("multipart").resolve(sessionId).resolve(uploadId).normalize();
  }

  private Path objectPath(String stagingKey) {
    String sessionId = sessionIdFromKey(stagingKey);
    Path path = root.resolve("objects").resolve(sessionId).resolve("raw").normalize();
    if (!path.startsWith(root)) throw new AcademyVideoStorageException("INVALID_STORAGE_IDENTITY");
    return path;
  }

  private Path workerObjectPath(String key) {
    if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
      throw new AcademyVideoStorageException("INVALID_STORAGE_IDENTITY");
    }
    if (key.startsWith("staging/")) {
      return objectPath(key);
    }
    Path path = root.resolve("objects").resolve(key).normalize();
    if (!path.startsWith(root.resolve("objects"))) {
      throw new AcademyVideoStorageException("INVALID_STORAGE_IDENTITY");
    }
    return path;
  }

  private String sessionIdFromKey(String stagingKey) {
    String[] segments = stagingKey == null ? new String[0] : stagingKey.split("/");
    if (segments.length != 3 || !"staging".equals(segments[0]) || !"raw".equals(segments[2])) {
      throw new AcademyVideoStorageException("INVALID_STORAGE_IDENTITY");
    }
    requireUuid(segments[1]);
    return segments[1];
  }

  private static void requireUuid(String value) {
    try {
      UUID.fromString(value);
    } catch (RuntimeException e) {
      throw new AcademyVideoStorageException("INVALID_STORAGE_IDENTITY");
    }
  }

  private static long copyBounded(
      InputStream in, Path target, long max, long deadlineNanos) throws IOException {
    try (OutputStream out = privateOutputStream(target)) {
      return copyBounded(in, out, max, deadlineNanos);
    }
  }

  private static long copyBounded(
      InputStream in, OutputStream out, long max, long deadlineNanos) throws IOException {
    long total = 0;
    byte[] buffer = new byte[64 * 1024];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      requireRemaining(deadlineNanos);
      total += read;
      if (total > max) throw new AcademyVideoStorageException("PART_TOO_LARGE");
      out.write(buffer, 0, read);
    }
    return total;
  }

  private static OutputStream privateOutputStream(Path path) throws IOException {
    return java.nio.channels.Channels.newOutputStream(Files.newByteChannel(
        path,
        java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
        PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rw-------"))));
  }

  private static void createPrivateDirectoryAtomically(Path path) throws IOException {
    assertNoSymlinkAncestor(path);
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      Path parent = path.getParent();
      if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("upload parent does not exist");
      }
      try {
        Files.createDirectory(path, PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rwx------")));
      } catch (UnsupportedOperationException e) {
        Files.createDirectory(path);
      }
    }
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
      throw new IOException("unsafe upload directory");
    }
  }

  private static void assertNoSymlinkAncestor(Path path) throws IOException {
    Path absolute = path.toAbsolutePath().normalize();
    Path current = absolute.getRoot();
    for (Path segment : absolute) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
        throw new IOException("symlink ancestor rejected");
      }
    }
  }

  private static void requirePrivateRealDirectory(Path path) {
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
      throw new AcademyVideoStorageException("UNSAFE_LOCAL_PATH");
    }
  }

  private static String localEtag(Path path) throws IOException {
    return Long.toHexString(Files.size(path)) + "-" + Files.getLastModifiedTime(path).toMillis();
  }

  private static String normalizeEtag(String value) {
    return value == null ? "" : value.replace("\"", "").trim();
  }

  private static void deleteTree(Path path) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
    if (Files.isSymbolicLink(path)) {
      Files.deleteIfExists(path);
      return;
    }
    try (var paths = Files.walk(path)) {
      for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(child);
      }
    }
  }

  private static long deadlineNanos(Duration remaining) {
    if (remaining == null || remaining.isNegative() || remaining.isZero()) {
      throw new AcademyVideoStorageException("DEADLINE_EXCEEDED");
    }
    try {
      return Math.addExact(System.nanoTime(), remaining.toNanos());
    } catch (ArithmeticException e) {
      return Long.MAX_VALUE;
    }
  }

  private static Duration requireRemaining(long deadlineNanos) {
    long remaining = deadlineNanos - System.nanoTime();
    if (remaining <= 0) throw new AcademyVideoStorageException("DEADLINE_EXCEEDED");
    return Duration.ofNanos(remaining);
  }
}
