package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoUploadStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Default raw validator — private bounded materialization")
class DefaultAcademyVideoRawValidatorSecurityTest {
  @TempDir Path temp;

  @Test
  @DisplayName("given validator materializes raw when ffprobe runs then directory/file are 0700/0600")
  void private_permissions_and_finally_cleanup() throws Exception {
    // given
    Path script = permissionCheckingProbeScript();
    Path root = temp.resolve("validation");
    DefaultAcademyVideoRawValidator validator =
        new DefaultAcademyVideoRawValidator(fakeStorage("raw".getBytes()),
            script.toString(), root.toString(), 1);
    String sessionId = UUID.randomUUID().toString();
    String token = UUID.randomUUID().toString();

    // when
    var raw = validator.validate(sessionId, token, "opaque-key", 3,
        UploadWorkBudget.of(Duration.ofSeconds(5)), () -> false);

    // then
    assertThat(raw.durationMs()).isEqualTo(120_000);
    assertThat(raw.mediaType()).isEqualTo("video/mp4");
    assertThat(root.resolve(sessionId).resolve(token)).doesNotExist();
  }

  @Test
  @DisplayName("given attacker substitutes session directory symlink when validating then fail closed")
  void symlink_substitution_fails_closed() throws Exception {
    // given
    Path root = temp.resolve("validation");
    Files.createDirectories(root);
    String sessionId = UUID.randomUUID().toString();
    String token = UUID.randomUUID().toString();
    Path outside = temp.resolve("outside");
    Files.createDirectories(outside);
    Files.createSymbolicLink(root.resolve(sessionId), outside);
    DefaultAcademyVideoRawValidator validator =
        new DefaultAcademyVideoRawValidator(fakeStorage("raw".getBytes()),
            permissionCheckingProbeScript().toString(), root.toString(), 1);

    // when / then
    assertThatThrownBy(() -> validator.validate(
        sessionId, token, "opaque-key", 3,
        UploadWorkBudget.of(Duration.ofSeconds(5)), () -> false))
        .isInstanceOf(AcademyVideoRawValidationException.class);
    assertThat(outside.resolve(token)).doesNotExist();
  }

  @Test
  @DisplayName("given stream metadata lies when byte max plus one arrives then counter rejects")
  void streamed_max_plus_one_is_rejected() throws Exception {
    // given
    DefaultAcademyVideoRawValidator validator = new DefaultAcademyVideoRawValidator(
        fakeStorage(new byte[11]), permissionCheckingProbeScript().toString(),
        temp.resolve("validation-over").toString(), 1, 10, 120_000);

    // when / then
    assertThatThrownBy(() -> validator.validate(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "opaque", 11,
        UploadWorkBudget.of(Duration.ofSeconds(5)), () -> false))
        .isInstanceOf(AcademyVideoRawValidationException.class)
        .extracting("errorCode").isEqualTo("RAW_TOO_LARGE");
  }

  @Test
  @DisplayName("given materialization blocks when abort is signalled then stream closes within two seconds")
  void abort_closes_blocked_materialization_within_two_seconds() throws Exception {
    // given
    AtomicBoolean closed = new AtomicBoolean();
    CountDownLatch entered = new CountDownLatch(1);
    InputStream blocking = new InputStream() {
      @Override public int read() throws java.io.IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0];
      }
      @Override public synchronized int read(byte[] b, int off, int len)
          throws java.io.IOException {
        entered.countDown();
        while (!closed.get()) {
          try {
            wait();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException("interrupted");
          }
        }
        throw new java.io.IOException("closed");
      }
      @Override public synchronized void close() {
        closed.set(true);
        notifyAll();
      }
    };
    AcademyVideoUploadStorage blockingStorage = fakeStorage(new byte[0], blocking);
    DefaultAcademyVideoRawValidator validator = new DefaultAcademyVideoRawValidator(
        blockingStorage, permissionCheckingProbeScript().toString(),
        temp.resolve("validation-abort").toString(), 1, 100, 120_000);
    AtomicBoolean cancelled = new AtomicBoolean();
    var executor = Executors.newSingleThreadExecutor();
    long started = System.nanoTime();
    var future = executor.submit(() -> validator.validate(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "opaque", 1,
        UploadWorkBudget.of(Duration.ofSeconds(10)), cancelled::get));
    assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

    // when
    cancelled.set(true);

    // then
    assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
        .isInstanceOf(java.util.concurrent.ExecutionException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    assertThat(closed).isTrue();
    executor.shutdownNow();
  }

  @Test
  @DisplayName("given two validators when materializing then one permit prevents overlap")
  void one_permit_serializes_materialization() throws Exception {
    // given
    AtomicInteger active = new AtomicInteger();
    AtomicInteger maximum = new AtomicInteger();
    AcademyVideoUploadStorage tracked = new AcademyVideoUploadStorage() {
      @Override public UploadSession initiate(String s, long size, int parts) {
        throw new UnsupportedOperationException();
      }
      @Override public String uploadLocalPart(
          String s, String u, int p, InputStream body, long length) {
        throw new UnsupportedOperationException();
      }
      @Override public StorageState inspect(String key, String upload, Duration remaining) {
        throw new UnsupportedOperationException();
      }
      @Override public CompletedObject complete(
          String key, String upload, List<Part> parts, Duration remaining) {
        throw new UnsupportedOperationException();
      }
      @Override public ObjectMetadata head(String key, Duration remaining) {
        return new ObjectMetadata("etag", 3);
      }
      @Override public InputStream open(String key, Duration remaining) {
        int now = active.incrementAndGet();
        maximum.accumulateAndGet(now, Math::max);
        return new ByteArrayInputStream("raw".getBytes()) {
          @Override public synchronized int read(byte[] b, int off, int len) {
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            return super.read(b, off, len);
          }
          @Override public void close() throws java.io.IOException {
            active.decrementAndGet();
            super.close();
          }
        };
      }
      @Override public void abort(String key, String upload) {}
    };
    DefaultAcademyVideoRawValidator validator = new DefaultAcademyVideoRawValidator(
        tracked, permissionCheckingProbeScript().toString(),
        temp.resolve("validation-permit").toString(), 1);
    var executor = Executors.newFixedThreadPool(2);

    // when
    var first = executor.submit(() -> validator.validate(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "one", 3,
        UploadWorkBudget.of(Duration.ofSeconds(5)), () -> false));
    var second = executor.submit(() -> validator.validate(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "two", 3,
        UploadWorkBudget.of(Duration.ofSeconds(5)), () -> false));
    first.get(5, TimeUnit.SECONDS);
    second.get(5, TimeUnit.SECONDS);

    // then
    assertThat(maximum).hasValue(1);
    executor.shutdownNow();
  }

  @Test
  @DisplayName("given ffprobe hangs when abort is signalled then process tree is killed within two seconds")
  void abort_kills_ffprobe_within_two_seconds() throws Exception {
    // given
    Path marker = temp.resolve("probe-started");
    Path script = temp.resolve("hanging-probe.sh");
    Files.writeString(script, "#!/bin/sh\ntouch '" + marker + "'\nsleep 30\n");
    Files.setPosixFilePermissions(script, EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE));
    DefaultAcademyVideoRawValidator validator = new DefaultAcademyVideoRawValidator(
        fakeStorage("raw".getBytes()), script.toString(),
        temp.resolve("validation-probe-abort").toString(), 1);
    AtomicBoolean cancelled = new AtomicBoolean();
    var executor = Executors.newSingleThreadExecutor();
    var future = executor.submit(() -> validator.validate(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "opaque", 3,
        UploadWorkBudget.of(Duration.ofSeconds(10)), cancelled::get));
    long waitDeadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
    while (!Files.exists(marker) && System.nanoTime() < waitDeadline) Thread.sleep(10);
    assertThat(marker).exists();
    long started = System.nanoTime();

    // when
    cancelled.set(true);

    // then
    assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
        .isInstanceOf(java.util.concurrent.ExecutionException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    executor.shutdownNow();
  }

  @Test
  @DisplayName("given restart orphan and live token directories when startup sweep runs then only orphan is removed")
  void startup_sweep_removes_only_stale_tokens() throws Exception {
    // given
    Path root = temp.resolve("validation-sweep");
    String sessionId = UUID.randomUUID().toString();
    String stale = UUID.randomUUID().toString();
    String live = UUID.randomUUID().toString();
    Files.createDirectories(root.resolve(sessionId).resolve(stale));
    Files.createDirectories(root.resolve(sessionId).resolve(live));
    DefaultAcademyVideoRawValidator validator = new DefaultAcademyVideoRawValidator(
        fakeStorage("raw".getBytes()), permissionCheckingProbeScript().toString(),
        root.toString(), 1);

    // when
    validator.sweepStale(identity -> identity.token().equals(live));

    // then
    assertThat(root.resolve(sessionId).resolve(stale)).doesNotExist();
    assertThat(root.resolve(sessionId).resolve(live)).exists();
  }

  private Path permissionCheckingProbeScript() throws Exception {
    Path script = temp.resolve("fake-ffprobe-" + UUID.randomUUID() + ".sh");
    Files.writeString(script, """
        #!/bin/sh
        for arg do raw="$arg"; done
        file_mode="$(stat -c %a "$raw" 2>/dev/null || stat -f %Lp "$raw")"
        dir_mode="$(stat -c %a "$(dirname "$raw")" 2>/dev/null || stat -f %Lp "$(dirname "$raw")")"
        [ "$file_mode" = "600" ] || exit 9
        [ "$dir_mode" = "700" ] || exit 10
        printf 'width=1280\\nheight=720\\ncodec_name=h264\\nformat_name=mov,mp4,m4a,3gp,3g2,mj2\\nTAG:major_brand=isom\\nduration=120.000\\n'
        """);
    Files.setPosixFilePermissions(script, EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE));
    return script;
  }

  private AcademyVideoUploadStorage fakeStorage(byte[] bytes) {
    return fakeStorage(bytes, new ByteArrayInputStream(bytes));
  }

  private AcademyVideoUploadStorage fakeStorage(byte[] bytes, InputStream stream) {
    return new AcademyVideoUploadStorage() {
      @Override public UploadSession initiate(String s, long size, int parts) {
        throw new UnsupportedOperationException();
      }
      @Override public String uploadLocalPart(
          String s, String u, int p, InputStream body, long length) {
        throw new UnsupportedOperationException();
      }
      @Override public StorageState inspect(String key, String upload, Duration remaining) {
        throw new UnsupportedOperationException();
      }
      @Override public CompletedObject complete(
          String key, String upload, List<Part> parts, Duration remaining) {
        throw new UnsupportedOperationException();
      }
      @Override public ObjectMetadata head(String key, Duration remaining) {
        return new ObjectMetadata("etag", bytes.length);
      }
      @Override public InputStream open(String key, Duration remaining) {
        return stream;
      }
      @Override public void abort(String key, String upload) {}
    };
  }
}
