package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UploadedFileIngestorTest {

  @Mock FileStorage fileStorage;
  @Mock ImageSanitizer imageSanitizer;
  @Mock UploadSizeGuard uploadSizeGuard;

  @Test
  @DisplayName("given 이미지 when store then 용량 가드와 sanitizer를 거쳐 IMAGE로 저장한다")
  void store_image_uses_guard_and_sanitizer() throws Exception {
    // given
    UploadedFileIngestor ingestor = new UploadedFileIngestor(fileStorage, imageSanitizer, uploadSizeGuard);
    MockMultipartFile file = new MockMultipartFile("files", "exam.png", "image/png", new byte[]{1, 2, 3});
    when(imageSanitizer.sanitize(file)).thenReturn(
        new ImageSanitizer.SanitizedImage(new byte[]{9, 8}, ImageSanitizer.JPEG_CONTENT_TYPE, "jpg"));
    when(fileStorage.saveImage(eq(10L), eq("exam.png"), any(byte[].class), eq("jpg")))
        .thenReturn("users/10/exam.jpg");

    // when
    UploadedFileIngestor.StoredFile stored = ingestor.store(file, 10L);

    // then
    verify(uploadSizeGuard).requireWithinDocumentLimit(file);
    assertThat(stored.storageKey()).isEqualTo("users/10/exam.jpg");
    assertThat(stored.fileType()).isEqualTo("IMAGE");
    assertThat(stored.fileSize()).isEqualTo(2);
  }

  @Test
  @DisplayName("given UNKNOWN 파일 when store then 원본 스트림 그대로 저장한다")
  void store_unknown_saves_raw() throws Exception {
    // given
    UploadedFileIngestor ingestor = new UploadedFileIngestor(fileStorage, imageSanitizer, uploadSizeGuard);
    MockMultipartFile file = new MockMultipartFile("files", "memo.txt", "text/plain", new byte[]{1, 2, 3, 4});
    when(fileStorage.saveRaw(eq(7L), eq("memo.txt"), any(), eq(4L))).thenReturn("raw-key");

    // when
    UploadedFileIngestor.StoredFile stored = ingestor.store(file, 7L);

    // then
    verify(uploadSizeGuard).requireWithinDocumentLimit(file);
    verifyNoInteractions(imageSanitizer);
    assertThat(stored.fileType()).isEqualTo("UNKNOWN");
    assertThat(stored.storageKey()).isEqualTo("raw-key");
  }

  @Test
  @DisplayName("given storage IOException when store then UncheckedIOException으로 정규화한다")
  void store_io_exception_wraps() throws Exception {
    // given
    UploadedFileIngestor ingestor = new UploadedFileIngestor(fileStorage, imageSanitizer, uploadSizeGuard);
    MockMultipartFile file = new MockMultipartFile("files", "memo.txt", "text/plain", new byte[]{1});
    when(fileStorage.saveRaw(eq(1L), eq("memo.txt"), any(), eq(1L))).thenThrow(new IOException("disk"));

    // when / then
    assertThatThrownBy(() -> ingestor.store(file, 1L))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("파일 저장 실패");
  }

  @Test
  @DisplayName("given content type when resolveFileType then IMAGE/PDF/UNKNOWN으로 분류한다")
  void resolve_file_type() {
    // given
    UploadedFileIngestor ingestor = new UploadedFileIngestor(fileStorage, imageSanitizer, uploadSizeGuard);

    // when / then
    assertThat(ingestor.resolveFileType(new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[]{1}))).isEqualTo("IMAGE");
    assertThat(ingestor.resolveFileType(new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[]{1}))).isEqualTo("PDF");
    assertThat(ingestor.resolveFileType(new MockMultipartFile("f", "a.bin", null, new byte[]{1}))).isEqualTo("UNKNOWN");
    assertThat(ingestor.resolveFileType(new MockMultipartFile("f", "a.txt", "text/plain", new byte[]{1}))).isEqualTo("UNKNOWN");
  }
}
