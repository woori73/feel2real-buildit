package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * LMS 로컬 영상 404 재발 방지 테스트.
 *
 * <p>근본 원인(CASE A): 쓰기 경로({@code LocalFileStorage}, 구 {@code app.file.upload-dir} =
 * {@code ./raw_datas})와 읽기 경로({@code LocalAcademyVideoDeliveryStorage}, {@code
 * app.video.local-upload-dir}/objects)가 서로 다른 로컬 루트를 써서, 방금 업로드한 파일을 delivery가
 * 찾지 못하고 404({@code VIDEO_OBJECT_MISSING})를 냈다.
 *
 * <p>수정 계약: {@code application-local.yaml}에서 {@code app.file.upload-dir}을 {@code
 * ${app.video.local-upload-dir}/objects}로 통일하고, {@code LocalFileStorage}는 basePath 기준
 * 상대 키(portable logical key)만 반환한다. 이 테스트는 그 계약을 고정한다.
 */
class LmsLocalVideoDeliveryRoundTripTest {

  @TempDir Path tempDir;

  private static LocalFileStorage fileStorageWithBase(Path base) {
    LocalFileStorage storage = new LocalFileStorage();
    ReflectionTestUtils.setField(storage, "basePath", base.toString());
    return storage;
  }

  @Test
  @DisplayName("LMS 업로드→재생 round-trip: FileStorage 저장 키를 DeliveryStorage가 resolve한다")
  void uploadThenDeliveryResolves() throws Exception {
    // given — 수정된 application-local.yaml과 같은 구성: upload-dir = {asyncRoot}/objects
    Path asyncRoot = tempDir.resolve("academy-video-uploads");
    LocalFileStorage files = fileStorageWithBase(asyncRoot.resolve("objects"));
    byte[] payload = "fake-mp4-bytes".getBytes();
    String key = files.saveRaw(
        7L, "lms-content.mp4", new ByteArrayInputStream(payload), payload.length);

    // when — storage_key는 machine-specific 절대경로가 아닌 portable 상대 키다
    assertThat(Path.of(key).isAbsolute()).as("storage_key 절대경로 금지").isFalse();

    var delivery = new LocalAcademyVideoDeliveryStorage(
        asyncRoot.toString(), tempDir.resolve("legacy").toString());
    var target = (AcademyVideoDeliveryStorage.LocalTarget) delivery.resolve(
        key, "video/mp4", Duration.ofSeconds(30));

    // then — delivery가 같은 파일을 찾아 seek 가능한 채널로 연다 (player Range/206의 전제)
    assertThat(target.length()).isEqualTo(payload.length);
    byte[] actual = new byte[payload.length];
    try (var channel = target.channel()) {
      channel.position(4);
      assertThat(channel.position()).isEqualTo(4);
      channel.position(0);
      ByteBuffer buf = ByteBuffer.wrap(actual);
      while (buf.hasRemaining() && channel.read(buf) >= 0) {
        // drain
      }
    }
    assertThat(actual).containsExactly(payload);
  }

  @Test
  @DisplayName("구 local 데이터 호환: legacyRoot 아래 절대 키는 여전히 resolve된다")
  void legacyAbsoluteKeyStillResolves() throws Exception {
    // given
    Path legacyRoot = tempDir.resolve("legacy");
    Path asyncRoot = tempDir.resolve("academy-video-uploads");
    byte[] payload = {1, 2, 3};
    java.nio.file.Files.createDirectories(legacyRoot);
    Path object = java.nio.file.Files.write(legacyRoot.resolve("old-video.mp4"), payload);

    // when
    var delivery = new LocalAcademyVideoDeliveryStorage(
        asyncRoot.toString(), legacyRoot.toString());
    var target = (AcademyVideoDeliveryStorage.LocalTarget) delivery.resolve(
        object.toString(), "video/mp4", Duration.ofSeconds(30));

    // then
    target.channel().close();
    assertThat(target.length()).isEqualTo(payload.length);
  }

  @Test
  @DisplayName("근본원인 고정: 쓰기/읽기 루트가 다르면 delivery가 IOException(= 404 계층)을 낸다")
  void mismatchedRootsReproduceMissingObject() throws Exception {
    // given — 수정 전 구성 재현: 쓰기는 ./raw_datas 상당, 읽기는 {tmp}/academy-video-uploads 상당
    LocalFileStorage files = fileStorageWithBase(tempDir.resolve("raw_datas"));
    byte[] payload = "fake-mp4-bytes".getBytes();
    String key = files.saveRaw(
        7L, "lms-content.mp4", new ByteArrayInputStream(payload), payload.length);

    var delivery = new LocalAcademyVideoDeliveryStorage(
        tempDir.resolve("academy-video-uploads").toString(),
        tempDir.resolve("raw_datas").toString());

    // when / then — LmsContentDeliveryService는 이 IOException을 404로 변환한다
    assertThatThrownBy(() -> delivery.resolve(key, "video/mp4", Duration.ofSeconds(30)))
        .isInstanceOf(java.io.IOException.class);
  }
}
