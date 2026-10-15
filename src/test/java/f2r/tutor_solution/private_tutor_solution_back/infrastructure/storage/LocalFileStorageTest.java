package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocalFileStorage 단위 테스트.
 * 로컬 디스크 저장/조회 동작이 SourceService에서 옮겨진 후에도 동일하게 보장되는지 검증.
 */
class LocalFileStorageTest {

  private LocalFileStorage storage;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    storage = new LocalFileStorage();
    ReflectionTestUtils.setField(storage, "basePath", tempDir.toString());
  }

  @Test
  @DisplayName("saveImage — 사용자별 디렉터리 아래에 UUID + .jpg로 저장되고 bytes가 일치한다")
  void saveImage_writesToUserDir() throws Exception {
    // given
    byte[] payload = {1, 2, 3, 4, 5};

    // when
    String key = storage.saveImage(42L, "test.png", payload);

    // then
    Path saved = Path.of(key);
    assertThat(saved.getParent()).isEqualTo(tempDir.resolve("42"));
    assertThat(saved.getFileName().toString()).endsWith("_test.jpg");
    assertThat(Files.readAllBytes(saved)).containsExactly(payload);
  }

  @Test
  @DisplayName("savePdfPages — pageDir 아래에 page_01.jpg부터 순서대로 저장된다")
  void savePdfPages_writesNumberedPages() throws Exception {
    // given
    byte[] p1 = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
    byte[] p2 = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 2};
    byte[] p3 = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 3};

    // when
    String pageDir = storage.savePdfPages(7L, List.of(p1, p2, p3));

    // then
    Path dir = Path.of(pageDir);
    assertThat(Files.exists(dir.resolve("page_01.jpg"))).isTrue();
    assertThat(Files.exists(dir.resolve("page_02.jpg"))).isTrue();
    assertThat(Files.exists(dir.resolve("page_03.jpg"))).isTrue();
    assertThat(Files.readAllBytes(dir.resolve("page_02.jpg"))).containsExactly(p2);
  }

  @Test
  @DisplayName("pageCount — PDF는 page_*.jpg 개수, IMAGE/UNKNOWN은 1")
  void pageCount_returnsCorrectCount() throws Exception {
    // given
    String pdfKey = storage.savePdfPages(1L, List.of(new byte[]{1}, new byte[]{2}, new byte[]{3}));
    String imageKey = storage.saveImage(1L, "x.jpg", new byte[]{1});

    // when / then
    assertThat(storage.pageCount(pdfKey, "PDF")).isEqualTo(3);
    assertThat(storage.pageCount(imageKey, "IMAGE")).isEqualTo(1);
    assertThat(storage.pageCount("/nonexistent", "PDF")).isEqualTo(1);  // 폴백
  }

  @Test
  @DisplayName("readPage — PDF는 1-based page 번호로, IMAGE는 page 인자 무시하고 단일 파일")
  void readPage_returnsCorrectBytes() throws Exception {
    // given
    byte[] p1 = {1, 1, 1};
    byte[] p2 = {2, 2, 2};
    String pdfKey = storage.savePdfPages(1L, List.of(p1, p2));
    String imageKey = storage.saveImage(1L, "a.jpg", new byte[]{9, 9, 9});

    // when / then
    assertThat(storage.readPage(pdfKey, "PDF", 1)).containsExactly(p1);
    assertThat(storage.readPage(pdfKey, "PDF", 2)).containsExactly(p2);
    assertThat(storage.readPage(imageKey, "IMAGE", 1)).containsExactly(9, 9, 9);
  }

  @Test
  @DisplayName("readAllPages — PDF는 정렬된 모든 페이지, IMAGE는 길이 1 리스트")
  void readAllPages_returnsAllPagesInOrder() throws Exception {
    // given
    byte[] p1 = {1}; byte[] p2 = {2}; byte[] p3 = {3};
    String pdfKey = storage.savePdfPages(1L, List.of(p1, p2, p3));
    String imageKey = storage.saveImage(1L, "single.jpg", new byte[]{42});

    // when
    List<byte[]> pdfPages = storage.readAllPages(pdfKey, "PDF");
    List<byte[]> imgPages = storage.readAllPages(imageKey, "IMAGE");

    // then
    assertThat(pdfPages).hasSize(3);
    assertThat(pdfPages.get(0)).containsExactly(p1);
    assertThat(pdfPages.get(1)).containsExactly(p2);
    assertThat(pdfPages.get(2)).containsExactly(p3);
    assertThat(imgPages).hasSize(1);
    assertThat(imgPages.get(0)).containsExactly(42);
  }

  @Test
  @DisplayName("readPage — 존재하지 않는 PDF 페이지는 IOException")
  void readPage_missingPage_throws() throws Exception {
    // given
    String pdfKey = storage.savePdfPages(1L, List.of(new byte[]{1}));

    // when / then
    try {
      storage.readPage(pdfKey, "PDF", 99);
      assertThat(false).as("IOException 예상").isTrue();
    } catch (IOException expected) {
      assertThat(expected.getMessage()).contains("페이지를 찾을 수 없습니다");
    }
  }

  @Test
  @DisplayName("saveRaw — InputStream 내용 그대로 저장")
  void saveRaw_preservesBytes() throws Exception {
    // given
    byte[] payload = "hello world".getBytes();

    // when
    String key = storage.saveRaw(1L, "doc.unknown", new ByteArrayInputStream(payload), payload.length);

    // then
    assertThat(Files.readAllBytes(Path.of(key))).containsExactly(payload);
  }

  @Test
  @DisplayName("saveRaw→readSingleLimited — 업로드한 단일 객체를 동일 bytes로 읽는다")
  void saveRawThenReadSingleLimited_roundTripsUploadedPayload() throws Exception {
    // given
    byte[] payload = {1, 2, 3, 4};
    String key = storage.saveRaw(1L, "video.mp4", new ByteArrayInputStream(payload), payload.length);

    // when
    byte[] result = storage.readSingleLimited(key, "UNKNOWN", payload.length);

    // then
    assertThat(result).containsExactly(payload);
  }

  @Test
  @DisplayName("readSingleLimited — metadata 크기가 상한을 넘으면 제한 예외를 던진다")
  void readSingleLimited_rejectsObjectOverLimit() throws Exception {
    // given
    byte[] payload = {1, 2, 3, 4};
    String key = storage.saveRaw(1L, "video.mp4", new ByteArrayInputStream(payload), payload.length);

    // when / then
    assertThatThrownBy(() -> storage.readSingleLimited(key, "UNKNOWN", payload.length - 1L))
        .isInstanceOf(StorageReadLimitExceededException.class)
        .hasMessageNotContaining(key)
        .hasMessageNotContaining(tempDir.toString());
  }

  @Test
  @DisplayName("deleteReported — null/blank/미존재 및 정상 삭제는 원하는 부재 상태 달성으로 보고한다")
  void deleteReported_isIdempotent() throws Exception {
    // given
    String key = storage.saveRaw(1L, "video.mp4", new ByteArrayInputStream(new byte[]{1}), 1);

    // when / then
    assertThat(storage.deleteReported(null, "UNKNOWN")).isTrue();
    assertThat(storage.deleteReported(" ", "UNKNOWN")).isTrue();
    assertThat(storage.deleteReported(tempDir.resolve("missing").toString(), "UNKNOWN")).isTrue();
    assertThat(storage.deleteReported(key, "UNKNOWN")).isTrue();
    assertThat(storage.deleteReported(key, "UNKNOWN")).isTrue();
  }

  @Test
  @DisplayName("deleteReported — PDF 재귀 삭제가 부분 실패하면 false를 보고한다")
  void deleteReported_reportsPartialRecursiveDelete() throws Exception {
    // given
    Path pageDir = tempDir.resolve("pdf-pages");
    Path nested = pageDir.resolve("unexpected-directory");
    Files.createDirectories(nested);
    Files.write(nested.resolve("still-present"), new byte[]{1});

    // when
    boolean deleted = storage.deleteReported(pageDir.toString(), "PDF");

    // then
    assertThat(deleted).isFalse();
    assertThat(Files.exists(pageDir)).isTrue();
  }
}
