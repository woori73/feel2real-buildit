package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadedFileIngestor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SourceService 파일 sanitizing 단위 테스트.
 * 실제 파일 저장 없이 재인코딩/PDF 변환 로직만 검증 (DB/디스크/FileStorage 의존성 불필요).
 */
class SourceSanitizeTest {

  // FileStorage / repositories는 sanitize 로직과 무관하므로 null 주입. reencode/renderPdfPages 메서드만 검증.
  private final ImageSanitizer imageSanitizer = new ImageSanitizer();
  private final UploadedFileIngestor ingestor = new UploadedFileIngestor(
      null, imageSanitizer, new UploadSizeGuard(20L * 1024 * 1024));
  private final SourceService service = new SourceService(
      null, null, null, null, null, null, null, null, imageSanitizer, ingestor);

  // ── 이미지 ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("JPEG 이미지는 재인코딩 후 JPEG magic bytes(FF D8 FF)를 가진다")
  void jpeg_image_reencoded_returns_jpeg() throws Exception {
    // given
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.jpg", "image/jpeg", createImageBytes("jpeg"));

    // when
    byte[] result = service.reencodeImageForTest(file);

    // then
    assertThat(result).isNotEmpty();
    assertThat(isJpeg(result)).isTrue();
  }

  @Test
  @DisplayName("PNG 이미지도 재인코딩 후 JPEG로 변환된다")
  void png_image_reencoded_to_jpeg() throws Exception {
    // given
    MockMultipartFile file = new MockMultipartFile(
        "file", "photo.png", "image/png", createImageBytes("png"));

    // when
    byte[] result = service.reencodeImageForTest(file);

    // then
    assertThat(isJpeg(result)).isTrue();
  }

  @Test
  @DisplayName("알파 채널이 있는 PNG(투명 배경 스크린샷 등)도 평탄화 후 JPEG로 인코딩된다")
  void png_with_alpha_channel_reencoded_to_jpeg() throws Exception {
    // given — TYPE_INT_ARGB로 알파를 가진 PNG를 만든다 (예: 투명 배경 스크린샷)
    BufferedImage argb = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    // (0,0)을 반투명 빨강으로 — 알파 0x80
    argb.setRGB(0, 0, 0x80FF0000);
    ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
    ImageIO.write(argb, "png", pngBytes);
    MockMultipartFile file = new MockMultipartFile(
        "file", "screenshot.png", "image/png", pngBytes.toByteArray());

    // when
    byte[] result = service.reencodeImageForTest(file);

    // then — 회귀 방지: 알파 채널이 있어도 JPEG로 변환되어야 한다
    assertThat(isJpeg(result)).isTrue();
  }

  @Test
  @DisplayName("빈 바이트 이미지는 IOException 발생 — 디코딩 실패 메시지 포함")
  void empty_image_throws_IOException() {
    // given
    MockMultipartFile file = new MockMultipartFile(
        "file", "broken.jpg", "image/jpeg", new byte[0]);

    // when / then
    assertThatThrownBy(() -> service.reencodeImageForTest(file))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("디코딩할 수 없습니다");
  }

  @Test
  @DisplayName("확장자는 이미지지만 내용이 손상된 파일은 IOException 발생")
  void corrupted_image_bytes_throws_IOException() {
    // given
    MockMultipartFile file = new MockMultipartFile(
        "file", "broken.png", "image/png", new byte[]{0x00, 0x01, 0x02, 0x03});

    // when / then
    assertThatThrownBy(() -> service.reencodeImageForTest(file))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("디코딩할 수 없습니다");
  }

  @Test
  @DisplayName("경로 순회 파일명(../../../etc/passwd)이 들어와도 재인코딩은 정상 완료된다")
  void path_traversal_filename_does_not_affect_reencoding() throws Exception {
    // given
    MockMultipartFile file = new MockMultipartFile(
        "file", "../../../etc/passwd.jpg", "image/jpeg", createImageBytes("jpeg"));

    // when
    byte[] result = service.reencodeImageForTest(file);

    // then: 재인코딩 자체는 성공 (파일명 sanitize는 store() 단에서 처리)
    assertThat(isJpeg(result)).isTrue();
  }

  // ── EXIF orientation ────────────────────────────────────────────────────

  @Test
  @DisplayName("orientation=1(정상)이면 원본 이미지를 그대로 반환한다")
  void applyExifOrientation_orientation1_returnsOriginal() {
    // given
    BufferedImage src = new BufferedImage(10, 20, BufferedImage.TYPE_INT_RGB);

    // when
    BufferedImage dst = SourceService.applyExifOrientation(src, 1);

    // then
    assertThat(dst).isSameAs(src);
  }

  @Test
  @DisplayName("orientation=6(시계방향 90°)은 width/height를 swap하고 좌상단 픽셀이 우상단으로 이동")
  void applyExifOrientation_orientation6_rotates90Clockwise() {
    // given — 100x50 이미지의 (0,0)에 빨강
    BufferedImage src = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
    src.setRGB(0, 0, 0xFF0000);

    // when
    BufferedImage dst = SourceService.applyExifOrientation(src, 6);

    // then — 회전 후 50x100, 좌상단(0,0)은 우상단(49,0)으로 이동
    assertThat(dst.getWidth()).isEqualTo(50);
    assertThat(dst.getHeight()).isEqualTo(100);
    assertThat(dst.getRGB(49, 0) & 0xFFFFFF).isEqualTo(0xFF0000);
  }

  @Test
  @DisplayName("orientation=3(180°)은 크기 유지하고 좌상단 픽셀이 우하단으로 이동")
  void applyExifOrientation_orientation3_rotates180() {
    // given
    BufferedImage src = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
    src.setRGB(0, 0, 0xFF0000);

    // when
    BufferedImage dst = SourceService.applyExifOrientation(src, 3);

    // then
    assertThat(dst.getWidth()).isEqualTo(100);
    assertThat(dst.getHeight()).isEqualTo(50);
    assertThat(dst.getRGB(99, 49) & 0xFFFFFF).isEqualTo(0xFF0000);
  }

  @Test
  @DisplayName("orientation=8(반시계 90°)은 width/height를 swap하고 좌상단 픽셀이 좌하단으로 이동")
  void applyExifOrientation_orientation8_rotates270() {
    // given
    BufferedImage src = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
    src.setRGB(0, 0, 0xFF0000);

    // when
    BufferedImage dst = SourceService.applyExifOrientation(src, 8);

    // then
    assertThat(dst.getWidth()).isEqualTo(50);
    assertThat(dst.getHeight()).isEqualTo(100);
    assertThat(dst.getRGB(0, 99) & 0xFFFFFF).isEqualTo(0xFF0000);
  }

  @Test
  @DisplayName("알 수 없는 orientation 값은 원본을 그대로 반환 (no-op)")
  void applyExifOrientation_unknownValue_returnsOriginal() {
    // given
    BufferedImage src = new BufferedImage(10, 20, BufferedImage.TYPE_INT_RGB);

    // when / then
    assertThat(SourceService.applyExifOrientation(src, 0)).isSameAs(src);
    assertThat(SourceService.applyExifOrientation(src, 99)).isSameAs(src);
  }

  @Test
  @DisplayName("readExifOrientation: 메타데이터가 없는 임의의 바이트는 기본값 1을 반환")
  void readExifOrientation_invalidBytes_returns1() {
    // given
    byte[] notImage = "this is not an image".getBytes();

    // when
    int orientation = SourceService.readExifOrientation(notImage);

    // then
    assertThat(orientation).isEqualTo(1);
  }

  // ── PDF ─────────────────────────────────────────────────────────────────
  // renderPdfPages는 이제 디스크에 안 쓰고 페이지별 JPEG bytes 리스트를 반환한다.
  // 디스크 저장은 LocalFileStorage의 책임 (별도 unit test).

  @Test
  @DisplayName("단일 페이지 PDF는 JPEG bytes 1개로 변환된다")
  void single_page_pdf_produces_one_jpeg() throws Exception {
    // given
    MockMultipartFile file = new MockMultipartFile(
        "file", "exam.pdf", "application/pdf", createPdfBytes(1));

    // when
    List<byte[]> pages = service.renderPdfPagesForTest(file);

    // then
    assertThat(pages).hasSize(1);
    assertThat(isJpeg(pages.get(0))).isTrue();
  }

  @Test
  @DisplayName("3페이지 PDF는 JPEG bytes 3개로 분리되며 모두 JPEG 매직바이트를 가진다")
  void multi_page_pdf_splits_into_jpegs() throws Exception {
    // given
    MockMultipartFile file = new MockMultipartFile(
        "file", "answer.pdf", "application/pdf", createPdfBytes(3));

    // when
    List<byte[]> pages = service.renderPdfPagesForTest(file);

    // then
    assertThat(pages).hasSize(3);
    pages.forEach(p -> assertThat(isJpeg(p)).isTrue());
  }

  @Test
  @DisplayName("PDF_MAX_PAGE(20)를 초과하는 PDF는 앞 20페이지만 반환된다 (예외 없음)")
  void pdf_exceeding_max_page_truncates_to_limit() throws Exception {
    // given — PDF_MAX_PAGE = 20, 22페이지 PDF
    MockMultipartFile file = new MockMultipartFile(
        "file", "huge.pdf", "application/pdf", createPdfBytes(22));

    // when
    List<byte[]> pages = service.renderPdfPagesForTest(file);

    // then
    assertThat(pages).hasSize(20);
  }

  @Test
  @DisplayName("손상된 PDF는 IOException 발생")
  void corrupted_pdf_throws_IOException() {
    // given — "%PDF" 헤더만 있는 불완전한 PDF
    byte[] brokenPdf = {0x25, 0x50, 0x44, 0x46};
    MockMultipartFile file = new MockMultipartFile(
        "file", "broken.pdf", "application/pdf", brokenPdf);

    // when / then
    assertThatThrownBy(() -> service.renderPdfPagesForTest(file))
        .isInstanceOf(IOException.class);
  }

  // ── 헬퍼 ────────────────────────────────────────────────────────────────

  private byte[] createImageBytes(String format) throws IOException {
    BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    img.createGraphics().setColor(Color.RED);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, format, out);
    return out.toByteArray();
  }

  private byte[] createPdfBytes(int pageCount) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      for (int i = 0; i < pageCount; i++) {
        doc.addPage(new PDPage());
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  /** JPEG magic bytes: FF D8 FF */
  private boolean isJpeg(byte[] data) {
    return data.length >= 3
        && (data[0] & 0xFF) == 0xFF
        && (data[1] & 0xFF) == 0xD8
        && (data[2] & 0xFF) == 0xFF;
  }
}
