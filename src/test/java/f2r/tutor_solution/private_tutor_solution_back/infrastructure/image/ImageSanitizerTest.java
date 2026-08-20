package f2r.tutor_solution.private_tutor_solution_back.infrastructure.image;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSanitizerTest {

  private final ImageSanitizer sanitizer = new ImageSanitizer();

  @Test
  void reencodeToJpeg_downscalesWhenLongestSideExceedsMax() throws IOException {
    // given: 최장변이 한계(1600)를 넘는 3000x2000 PNG
    MockMultipartFile file = pngFile(3000, 2000);

    // when
    byte[] result = sanitizer.reencodeToJpeg(file);

    // then: 비율 유지하며 최장변이 1600으로 축소된다 (3000:2000 → 1600:1067)
    BufferedImage decoded = decode(result);
    assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(ImageSanitizer.MAX_DIMENSION);
    assertThat(decoded.getWidth()).isEqualTo(1600);
    assertThat(decoded.getHeight()).isEqualTo(1067);
  }

  @Test
  void reencodeToJpeg_keepsDimensionsWhenWithinMax() throws IOException {
    // given: 한계 이하인 800x600 PNG
    MockMultipartFile file = pngFile(800, 600);

    // when
    byte[] result = sanitizer.reencodeToJpeg(file);

    // then: 해상도는 그대로 유지된다
    BufferedImage decoded = decode(result);
    assertThat(decoded.getWidth()).isEqualTo(800);
    assertThat(decoded.getHeight()).isEqualTo(600);
  }

  @Test
  void reencodeToJpeg_flattensTransparencyToOpaqueJpeg() throws IOException {
    // given: 알파 채널(투명)을 가진 ARGB PNG
    MockMultipartFile file = transparentPngFile(400, 400);

    // when
    byte[] result = sanitizer.reencodeToJpeg(file);

    // then: 결과는 알파가 없는 JPEG (투명 → 흰 배경 평탄화)
    BufferedImage decoded = decode(result);
    assertThat(decoded.getColorModel().hasAlpha()).isFalse();
  }

  @Test
  void sanitize_preservesAnimationForAnimatedGif() throws IOException {
    // given: 4프레임 애니메이션 GIF
    byte[] gif = animatedGifBytes(80, 60, 4);

    // when
    ImageSanitizer.SanitizedImage result = sanitizer.sanitize(gif, "reaction.gif");

    // then: GIF로 남고 프레임이 유지된다 (JPEG로 뭉개지 않는다)
    assertThat(result.contentType()).isEqualTo("image/gif");
    assertThat(result.extension()).isEqualTo("gif");
    assertThat(result.isAnimatedGif()).isTrue();
    assertThat(AnimatedGifTranscoder.frameCount(result.bytes())).isEqualTo(4);
  }

  @Test
  void sanitize_convertsStaticGifToJpeg() throws IOException {
    // given: 프레임이 1장뿐인 정지 GIF
    byte[] gif = animatedGifBytes(80, 60, 1);

    // when
    ImageSanitizer.SanitizedImage result = sanitizer.sanitize(gif, "static.gif");

    // then: 움직이지 않으므로 기존 JPEG 경로를 그대로 탄다
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(result.extension()).isEqualTo("jpg");
  }

  @Test
  void sanitize_convertsPngToJpeg() throws IOException {
    // given: 일반 PNG
    MockMultipartFile file = pngFile(200, 100);

    // when
    ImageSanitizer.SanitizedImage result = sanitizer.sanitize(file);

    // then: GIF가 아닌 포맷의 동작은 그대로다
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(result.extension()).isEqualTo("jpg");
    assertThat(result.isAnimatedGif()).isFalse();
  }

  @Test
  void sanitize_keepsAnimatedWebpAsWebp() throws IOException {
    // given: 실제 애니메이션 WebP (8프레임)
    byte[] webp;
    try (var in = getClass().getResourceAsStream("/images/animated.webp")) {
      webp = in.readAllBytes();
    }

    // when
    ImageSanitizer.SanitizedImage result = sanitizer.sanitize(webp, "reaction.webp");

    // then: 포맷을 유지한다 — GIF로 바꾸면 2.8배로 불어나고 JPEG면 첫 프레임만 남는다
    assertThat(result.contentType()).isEqualTo("image/webp");
    assertThat(result.extension()).isEqualTo("webp");
    assertThat(result.isAnimatedWebp()).isTrue();
    assertThat(result.isAnimatedGif()).isFalse();
  }

  @Test
  void sanitize_doesNotEnlargeAnimatedWebp() throws IOException {
    // given
    byte[] webp;
    try (var in = getClass().getResourceAsStream("/images/animated.webp")) {
      webp = in.readAllBytes();
    }

    // when
    ImageSanitizer.SanitizedImage result = sanitizer.sanitize(webp, "reaction.webp");

    // then: 재인코딩이 아니라 검증 + 메타데이터 제거이므로 절대 커지지 않는다
    assertThat(result.bytes().length).isLessThanOrEqualTo(webp.length);
  }

  @Test
  void contentTypeForStorageKey_followsExtension() {
    // given / when / then: 확장자를 붙이는 쪽과 읽는 쪽이 같은 규칙을 쓴다
    assertThat(ImageSanitizer.contentTypeForStorageKey("u/1/abc_x.gif")).isEqualTo("image/gif");
    assertThat(ImageSanitizer.contentTypeForStorageKey("u/1/abc_x.GIF")).isEqualTo("image/gif");
    assertThat(ImageSanitizer.contentTypeForStorageKey("u/1/abc_x.webp")).isEqualTo("image/webp");
    assertThat(ImageSanitizer.contentTypeForStorageKey("u/1/abc_x.jpg")).isEqualTo("image/jpeg");
  }

  @Test
  void contentTypeForStorageKey_treatsLegacyKeysAsJpeg() {
    // given: 이 기능 이전에 저장된 키(확장자 없음/null)
    // when / then: backfill 없이 기존 이미지가 그대로 서빙되도록 JPEG로 본다
    assertThat(ImageSanitizer.contentTypeForStorageKey(null)).isEqualTo("image/jpeg");
    assertThat(ImageSanitizer.contentTypeForStorageKey("u/1/legacy-no-extension")).isEqualTo("image/jpeg");
  }

  /** N프레임 GIF 바이트. frames=1이면 정지 GIF. */
  private static byte[] animatedGifBytes(int width, int height, int frames) throws IOException {
    javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
      writer.prepareWriteSequence(writer.getDefaultStreamMetadata(param));
      for (int i = 0; i < frames; i++) {
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        g.setColor(new Color((i * 70) % 256, 120, 200));
        g.fillRect(0, 0, width, height);
        g.dispose();
        writer.writeToSequence(new javax.imageio.IIOImage(frame, null, null), param);
      }
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private MockMultipartFile pngFile(int width, int height) throws IOException {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.BLUE);
    g.fillRect(0, 0, width, height);
    g.dispose();
    return toMultipart(img);
  }

  private MockMultipartFile transparentPngFile(int width, int height) throws IOException {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(new Color(255, 0, 0, 128));
    g.fillRect(0, 0, width / 2, height);
    g.dispose();
    return toMultipart(img);
  }

  private MockMultipartFile toMultipart(BufferedImage img) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return new MockMultipartFile("image", "test.png", "image/png", out.toByteArray());
  }

  private BufferedImage decode(byte[] bytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(bytes));
  }
}
