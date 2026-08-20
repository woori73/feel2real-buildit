package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoUploadStorage;
import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

@Component
public class DefaultAcademyVideoRawValidator implements AcademyVideoRawValidator {
  static final long MAX_RAW_BYTES = 209_715_200L;
  static final long MAX_DURATION_MS = 120_000L;
  private static final Set<String> SUPPORTED_CODECS =
      Set.of("h264", "hevc", "vp8", "vp9", "av1", "mpeg4");

  private final AcademyVideoUploadStorage storage;
  private final String ffprobePath;
  private final Path root;
  private final Semaphore materializationPermit;
  private final long maxRawBytes;
  private final long maxDurationMs;

  @Autowired
  public DefaultAcademyVideoRawValidator(
      AcademyVideoUploadStorage storage,
      AcademyVideoUploadProperties properties,
      @Value("${app.video.ffprobe-path:ffprobe}") String ffprobePath) {
    this.storage = storage;
    this.ffprobePath = ffprobePath;
    this.root = canonicalRoot(Path.of(properties.getValidationTempDir()));
    this.materializationPermit =
        new Semaphore(properties.getValidationMaterializationPermits());
    this.maxRawBytes = properties.getMaxRawBytes();
    this.maxDurationMs = properties.getMaxDurationMs();
  }

  DefaultAcademyVideoRawValidator(
      AcademyVideoUploadStorage storage,
      String ffprobePath,
      String tempRoot,
      int permits) {
    this(storage, ffprobePath, tempRoot, permits, MAX_RAW_BYTES, MAX_DURATION_MS);
  }

  DefaultAcademyVideoRawValidator(
      AcademyVideoUploadStorage storage,
      String ffprobePath,
      String tempRoot,
      int permits,
      long maxRawBytes,
      long maxDurationMs) {
    this.storage = storage;
    this.ffprobePath = ffprobePath;
    this.root = canonicalRoot(Path.of(tempRoot));
    this.materializationPermit = new Semaphore(permits);
    this.maxRawBytes = maxRawBytes;
    this.maxDurationMs = maxDurationMs;
  }

  private static Path canonicalRoot(Path requested) {
    Path absolute = requested.toAbsolutePath().normalize();
    try {
      Path parent = absolute.getParent();
      return parent == null
          ? absolute
          : parent.toRealPath().resolve(absolute.getFileName()).normalize();
    } catch (IOException e) {
      throw new IllegalArgumentException("validation root parent is unavailable");
    }
  }

  @Override
  public ValidatedRaw validate(
      String sessionId,
      String token,
      String storageKey,
      long expectedSizeBytes,
      UploadWorkBudget budget,
      BooleanSupplier cancelled) {
    Path directory = root.resolve(sessionId).resolve(token).normalize();
    Path raw = directory.resolve("raw");
    boolean acquired = false;
    try {
      checkIdentity(directory, sessionId, token);
      while (!(acquired = materializationPermit.tryAcquire())) {
        checkActive(budget, cancelled);
        sleep(50);
      }
      checkDiskCapacity();
      createPrivateDirectoryAtomically(root);
      createPrivateDirectoryAtomically(root.resolve(sessionId));
      createPrivateDirectoryAtomically(directory);
      if (Files.isSymbolicLink(directory) || Files.exists(raw, LinkOption.NOFOLLOW_LINKS)) {
        throw permanent("UNSAFE_VALIDATION_PATH");
      }
      Files.createFile(raw, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
          java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
      long copied = materialize(storageKey, raw, budget, cancelled);
      if (copied != expectedSizeBytes) {
        throw permanent("COMPLETED_SIZE_MISMATCH");
      }
      Map<String, String> fields = probe(raw, budget, cancelled);
      String codec = fields.getOrDefault("codec_name", "").toLowerCase(Locale.ROOT);
      Integer width = integer(fields.get("width"));
      Integer height = integer(fields.get("height"));
      if (width == null || height == null || codec.isBlank()) {
        throw permanent("VIDEO_STREAM_REQUIRED");
      }
      if (!SUPPORTED_CODECS.contains(codec)) {
        throw permanent("UNSUPPORTED_CODEC");
      }
      long durationMs = durationMillis(fields.get("duration"));
      if (durationMs < 1) {
        throw permanent("MALFORMED_DURATION");
      }
      if (durationMs > maxDurationMs) {
        throw permanent("DURATION_TOO_LONG");
      }
      return new ValidatedRaw(
          canonicalMediaType(fields.get("format_name"), fields.get("TAG:major_brand"), codec),
          copied, durationMs, width, height);
    } catch (AcademyVideoRawValidationException e) {
      throw e;
    } catch (IOException e) {
      throw transientFailure("VALIDATION_IO_FAILED");
    } finally {
      if (acquired) materializationPermit.release();
      deleteTreeQuietly(directory);
    }
  }

  @Override
  public void sweepStale(Predicate<ValidationIdentity> stillLive) {
    try {
      createPrivateDirectoryAtomically(root);
      try (var sessions = Files.list(root)) {
        for (Path session : sessions.toList()) {
          if (!Files.isDirectory(session, LinkOption.NOFOLLOW_LINKS)
              || Files.isSymbolicLink(session)) {
            deleteTreeQuietly(session);
            continue;
          }
          try (var tokens = Files.list(session)) {
            for (Path token : tokens.toList()) {
              ValidationIdentity identity =
                  new ValidationIdentity(session.getFileName().toString(),
                      token.getFileName().toString());
              if (!stillLive.test(identity)) {
                deleteTreeQuietly(token);
              }
            }
          }
        }
      }
    } catch (IOException ignored) {
    }
  }

  private long materialize(
      String storageKey, Path raw, UploadWorkBudget budget, BooleanSupplier cancelled)
      throws IOException {
    long total = 0;
    byte[] buffer = new byte[64 * 1024];
    try (InputStream in = storage.open(storageKey, budget.remaining());
         var out = Files.newOutputStream(raw)) {
      AtomicBoolean finished = new AtomicBoolean();
      Thread abortWatcher = Thread.ofVirtual().name("academy-video-validation-cancel").start(() -> {
        while (!finished.get()) {
          if (cancelled.getAsBoolean() || budget.expired()) {
            try {
              in.close();
            } catch (IOException ignored) {
            }
            return;
          }
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            return;
          }
        }
      });
      int read;
      try {
        while ((read = in.read(buffer)) >= 0) {
          checkActive(budget, cancelled);
          total += read;
          if (total > maxRawBytes) {
            throw permanent("RAW_TOO_LARGE");
          }
          out.write(buffer, 0, read);
        }
      } finally {
        finished.set(true);
        abortWatcher.interrupt();
      }
    }
    return total;
  }

  private Map<String, String> probe(
      Path raw, UploadWorkBudget budget, BooleanSupplier cancelled) throws IOException {
    Process process;
    try {
      process = new ProcessBuilder(
          ffprobePath,
          "-v", "error",
          "-select_streams", "v:0",
          "-show_entries",
          "stream=width,height,codec_name:format=format_name,duration:format_tags=major_brand",
          "-of", "default=noprint_wrappers=1:nokey=0",
          raw.toString())
          .redirectErrorStream(true)
          .start();
    } catch (IOException e) {
      throw transientFailure("FFPROBE_UNAVAILABLE");
    }
    try {
      while (process.isAlive()) {
        checkActive(budget, cancelled);
        sleep(100);
      }
      byte[] output = readBounded(process.getInputStream(), 32 * 1024);
      if (process.exitValue() != 0) {
        throw permanent("MALFORMED_MEDIA");
      }
      Map<String, String> fields = new HashMap<>();
      for (String line : new String(output).split("\\R")) {
        int separator = line.indexOf('=');
        if (separator > 0) {
          fields.putIfAbsent(line.substring(0, separator).trim(),
              line.substring(separator + 1).trim());
        }
      }
      return fields;
    } finally {
      if (process.isAlive()) {
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
      }
    }
  }

  static long durationMillis(String seconds) {
    try {
      return new BigDecimal(seconds).movePointRight(3)
          .setScale(0, RoundingMode.CEILING).longValueExact();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  static String canonicalMediaType(String format, String majorBrand, String codec) {
    String normalized = format == null ? "" : format.toLowerCase(Locale.ROOT);
    if (normalized.contains("mov") || normalized.contains("mp4")) {
      return majorBrand != null && majorBrand.trim().startsWith("qt")
          ? "video/quicktime" : "video/mp4";
    }
    throw permanent("UNSUPPORTED_CONTAINER");
  }

  private void checkDiskCapacity() throws IOException {
    createPrivateDirectoryAtomically(root);
    FileStore store = Files.getFileStore(root);
    if (store.getUsableSpace() < maxRawBytes + 16L * 1024 * 1024) {
      throw transientFailure("VALIDATION_DISK_FULL");
    }
  }

  private static void checkActive(UploadWorkBudget budget, BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean()) throw transientFailure("FINALIZATION_CANCELLED");
    if (budget.expired()) throw transientFailure("DEADLINE_EXCEEDED");
  }

  private void checkIdentity(Path directory, String sessionId, String token) {
    try {
      java.util.UUID.fromString(sessionId);
      java.util.UUID.fromString(token);
    } catch (RuntimeException e) {
      throw permanent("UNSAFE_VALIDATION_PATH");
    }
    if (!directory.startsWith(root)) throw permanent("UNSAFE_VALIDATION_PATH");
  }

  private static void createPrivateDirectoryAtomically(Path path) throws IOException {
    assertNoSymlinkAncestor(path);
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      Path parent = path.getParent();
      if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("validation parent does not exist");
      }
      try {
        Files.createDirectory(path,
            java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
      } catch (UnsupportedOperationException e) {
        Files.createDirectory(path);
      }
    }
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
      throw new IOException("unsafe validation directory");
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

  private static byte[] readBounded(InputStream input, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    input.transferTo(new java.io.OutputStream() {
      @Override public void write(int b) throws IOException {
        if (out.size() >= max) throw new IOException("probe output exceeded limit");
        out.write(b);
      }
    });
    return out.toByteArray();
  }

  private static Integer integer(String value) {
    try {
      return value == null ? null : Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw transientFailure("FINALIZATION_INTERRUPTED");
    }
  }

  private static void deleteTreeQuietly(Path path) {
    try {
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
    } catch (IOException ignored) {
    }
  }

  private static AcademyVideoRawValidationException permanent(String code) {
    return new AcademyVideoRawValidationException(code, false);
  }

  private static AcademyVideoRawValidationException transientFailure(String code) {
    return new AcademyVideoRawValidationException(code, true);
  }
}
