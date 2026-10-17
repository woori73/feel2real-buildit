package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사진 전처리 — 검증·재인코딩·축소·방향")
class ExamInsightImagePreprocessorTest {

  private final ExamInsightImagePreprocessor pre = new ExamInsightImagePreprocessor(1800, 100_000_000L);

  private static byte[] encode(BufferedImage img, String format) throws IOException {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    ImageIO.write(img, format, bo);
    return bo.toByteArray();
  }

  private static BufferedImage page(int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);
    g.setColor(Color.BLACK);
    g.drawString("1. Choose the best word.", 20, 40);
    g.dispose();
    return img;
  }

  private static BufferedImage decode(byte[] jpeg) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(jpeg));
  }

  @Test
  @DisplayName("JPG와 PNG를 읽어 JPEG로 다시 만든다")
  void jpgAndPng_areReencodedAsJpeg() throws IOException {
    // given
    byte[] jpg = encode(page(800, 1000), "jpg");
    byte[] png = encode(page(800, 1000), "png");

    // when
    ExamInsightImagePreprocessor.Prepared fromJpg = pre.prepare(jpg, "a.jpg");
    ExamInsightImagePreprocessor.Prepared fromPng = pre.prepare(png, "b.png");

    // then: 둘 다 JPEG 시그니처, 크기 유지
    for (var p : new ExamInsightImagePreprocessor.Prepared[] {fromJpg, fromPng}) {
      assertThat(p.jpeg()[0] & 0xFF).isEqualTo(0xFF);
      assertThat(p.jpeg()[1] & 0xFF).isEqualTo(0xD8);
      assertThat(p.width()).isEqualTo(800);
      assertThat(p.height()).isEqualTo(1000);
    }
    assertThat(fromJpg.sha256()).isNotEqualTo(fromPng.sha256());
  }

  @Test
  @DisplayName("긴 변이 상한(1800px)보다 크면 비율을 유지해 줄인다 — 비용 통제")
  void largeImage_isDownscaledKeepingAspect() throws IOException {
    // given
    byte[] big = encode(page(4000, 3000), "jpg");

    // when
    ExamInsightImagePreprocessor.Prepared p = pre.prepare(big, "big.jpg");

    // then
    assertThat(p.width()).isEqualTo(1800);
    assertThat(p.height()).isEqualTo(1350);
    assertThat(p.jpeg().length).isLessThan(big.length);
  }

  @Test
  @DisplayName("투명 PNG는 흰 종이 위에 올려 검은 배경이 되지 않는다")
  void transparentPng_isFlattenedOnWhite() throws IOException {
    // given: 완전 투명
    BufferedImage argb = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);

    // when
    BufferedImage out = decode(pre.prepare(encode(argb, "png"), "t.png").jpeg());

    // then
    Color c = new Color(out.getRGB(100, 100));
    assertThat(c.getRed()).isGreaterThan(240);
    assertThat(c.getGreen()).isGreaterThan(240);
    assertThat(c.getBlue()).isGreaterThan(240);
  }

  @Test
  @DisplayName("파일 뒤에 덧붙은 데이터와 메타데이터는 재인코딩으로 사라진다")
  void trailingPayload_isStripped() throws IOException {
    // given: 정상 JPEG 뒤에 스크립트 문자열을 붙인 파일(폴리글랏 시도)
    byte[] jpg = encode(page(300, 300), "jpg");
    byte[] evil = new byte[jpg.length + 40];
    System.arraycopy(jpg, 0, evil, 0, jpg.length);
    byte[] tail = "<script>alert('x')</script>EVIL-TAIL-PAYLOAD".getBytes();
    System.arraycopy(tail, 0, evil, jpg.length, Math.min(tail.length, 40));

    // when
    byte[] out = pre.prepare(evil, "poly.jpg").jpeg();

    // then
    assertThat(new String(out, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("EVIL-TAIL").doesNotContain("<script>");
  }

  @Test
  @DisplayName("사진이 아닌 파일이나 깨진 파일은 읽을 수 없다는 안내로 거절한다")
  void notAnImage_isRejected() {
    assertThatThrownBy(() -> pre.prepare("hello, not an image".getBytes(), "x.jpg"))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("IMAGE_UNREADABLE"));
    assertThatThrownBy(() -> pre.prepare(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2}, "broken.jpg"))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("IMAGE_UNREADABLE"));
  }

  @Test
  @DisplayName("너무 작은 사진은 글자를 읽을 수 없다고 거절한다")
  void tinyImage_isRejected() throws IOException {
    assertThatThrownBy(() -> pre.prepare(encode(page(40, 40), "png"), "tiny.png"))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("IMAGE_TOO_SMALL"));
  }

  @Test
  @DisplayName("해상도 상한을 넘으면 디코딩하지 않고 거절한다 (해상도 폭탄 방지)")
  void pixelBomb_isRejectedBeforeDecoding() throws IOException {
    // given: 상한을 1,000픽셀로 낮춘 전처리기에 800x800 사진
    var strict = new ExamInsightImagePreprocessor(1800, 1_000L);

    // when / then
    assertThatThrownBy(() -> strict.prepare(encode(page(800, 800), "png"), "bomb.png"))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("IMAGE_TOO_LARGE"));
  }

  @Test
  @DisplayName("EXIF 방향 6(시계 90도)인 사진은 글자가 바로 서도록 돌려 가로·세로가 바뀐다")
  void exifOrientation6_rotatesPortrait() throws IOException {
    // given: 가로 400x200 사진에 EXIF 방향 6을 심는다
    byte[] jpg = withOrientation(encode(page(400, 200), "jpg"), 6);

    // when
    ExamInsightImagePreprocessor.Prepared p = pre.prepare(jpg, "phone.jpg");

    // then
    assertThat(ExamInsightImagePreprocessor.exifOrientation(jpg)).isEqualTo(6);
    assertThat(p.width()).isEqualTo(200);
    assertThat(p.height()).isEqualTo(400);
  }

  @Test
  @DisplayName("방향 변환은 점 하나의 위치로 검증한다 (좌상단 빨간 점이 어디로 가는지)")
  void orient_movesTopLeftPixelCorrectly() {
    // given: 4x2 이미지, 좌상단(0,0)만 빨강
    BufferedImage src = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
    src.setRGB(0, 0, 0xFF0000);
    assertThat(red(ExamInsightImagePreprocessor.orient(src, 2), 4, 2)).containsExactly(3, 0);
    assertThat(red(ExamInsightImagePreprocessor.orient(src, 3), 4, 2)).containsExactly(3, 1);
    assertThat(red(ExamInsightImagePreprocessor.orient(src, 4), 4, 2)).containsExactly(0, 1);
    assertThat(red(ExamInsightImagePreprocessor.orient(src, 5), 2, 4)).containsExactly(0, 0);
    assertThat(red(ExamInsightImagePreprocessor.orient(src, 6), 2, 4)).containsExactly(1, 0);
    assertThat(red(ExamInsightImagePreprocessor.orient(src, 7), 2, 4)).containsExactly(1, 3);
    assertThat(red(ExamInsightImagePreprocessor.orient(src, 8), 2, 4)).containsExactly(0, 3);
  }

  @Test
  @DisplayName("EXIF가 없거나 깨졌으면 회전하지 않는다")
  void exifMissingOrBroken_meansNoRotation() throws IOException {
    assertThat(ExamInsightImagePreprocessor.exifOrientation(encode(page(100, 100), "jpg"))).isEqualTo(1);
    assertThat(ExamInsightImagePreprocessor.exifOrientation(new byte[] {1, 2, 3})).isEqualTo(1);
    assertThat(ExamInsightImagePreprocessor.exifOrientation(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 5}))
        .isEqualTo(1);
  }

  private static int[] red(BufferedImage img, int w, int h) {
    assertThat(img.getWidth()).isEqualTo(w);
    assertThat(img.getHeight()).isEqualTo(h);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if ((img.getRGB(x, y) & 0xFFFFFF) == 0xFF0000) return new int[] {x, y};
      }
    }
    throw new AssertionError("red pixel not found");
  }

  /** JPEG SOI 바로 뒤에 방향 태그만 있는 최소 EXIF APP1을 끼워 넣는다. */
  private static byte[] withOrientation(byte[] jpeg, int orientation) {
    byte[] exif = {
        (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
        'E', 'x', 'i', 'f', 0, 0,
        'M', 'M', 0, 0x2A, 0, 0, 0, 8,
        0, 1,
        0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
        0, 0, 0, 0
    };
    byte[] out = new byte[jpeg.length + exif.length];
    System.arraycopy(jpeg, 0, out, 0, 2);
    System.arraycopy(exif, 0, out, 2, exif.length);
    System.arraycopy(jpeg, 2, out, 2 + exif.length, jpeg.length - 2);
    return out;
  }
}
