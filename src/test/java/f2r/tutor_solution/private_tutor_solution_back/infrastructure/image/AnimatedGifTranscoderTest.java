package f2r.tutor_solution.private_tutor_solution_back.infrastructure.image;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnimatedGifTranscoderTest {

  private static final String IMAGE_METADATA_FORMAT = "javax_imageio_gif_image_1.0";

  @Test
  void isGif_detectsGifSignatureOnly() {
    // given: GIF89a 시그니처와 PNG 시그니처
    byte[] gif = "GIF89a".getBytes();
    byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};

    // when / then
    assertThat(AnimatedGifTranscoder.isGif(gif)).isTrue();
    assertThat(AnimatedGifTranscoder.isGif(png)).isFalse();
    assertThat(AnimatedGifTranscoder.isGif(new byte[]{1, 2})).isFalse();
    assertThat(AnimatedGifTranscoder.isGif(null)).isFalse();
  }

  @Test
  void frameCount_returnsFrameCountForAnimatedGif() throws IOException {
    // given: 3프레임 애니메이션 GIF
    byte[] gif = animatedGif(40, 30, 3, 7);

    // when
    int frames = AnimatedGifTranscoder.frameCount(gif);

    // then
    assertThat(frames).isEqualTo(3);
  }

  @Test
  void frameCount_returnsOneForStaticGif() throws IOException {
    // given: 1프레임짜리 정지 GIF
    byte[] gif = animatedGif(40, 30, 1, 10);

    // when
    int frames = AnimatedGifTranscoder.frameCount(gif);

    // then: 정지 GIF는 1 — 호출자는 기존 JPEG 경로로 보낸다
    assertThat(frames).isEqualTo(1);
  }

  @Test
  void frameCount_returnsZeroForNonGif() throws IOException {
    // given: GIF가 아닌 PNG 바이트
    byte[] png = pngBytes(20, 20);

    // when
    int frames = AnimatedGifTranscoder.frameCount(png);

    // then
    assertThat(frames).isZero();
  }

  @Test
  void reencode_preservesFrameCount() throws IOException {
    // given: 5프레임 애니메이션 GIF
    byte[] gif = animatedGif(60, 40, 5, 10);

    // when
    byte[] result = AnimatedGifTranscoder.reencode(gif, ImageSanitizer.MAX_DIMENSION);

    // then: 프레임이 하나도 사라지지 않는다 (= 움짤이 유지된다)
    assertThat(AnimatedGifTranscoder.frameCount(result)).isEqualTo(5);
  }

  @Test
  void reencode_preservesDelayTimePerFrame() throws IOException {
    // given: 프레임 지연이 7 centisecond인 애니메이션 GIF
    byte[] gif = animatedGif(40, 40, 4, 7);

    // when
    byte[] result = AnimatedGifTranscoder.reencode(gif, ImageSanitizer.MAX_DIMENSION);

    // then: 재생 속도가 바뀌지 않도록 각 프레임 지연이 그대로 유지된다
    assertThat(readDelays(result)).containsExactly(7, 7, 7, 7);
  }

  @Test
  void reencode_keepsDimensionsWhenWithinMax() throws IOException {
    // given: 최장변이 한계(1600) 이하인 애니메이션 GIF
    byte[] gif = animatedGif(200, 120, 3, 5);

    // when
    byte[] result = AnimatedGifTranscoder.reencode(gif, ImageSanitizer.MAX_DIMENSION);

    // then: 해상도는 그대로
    assertThat(readFrameSizes(result)).allSatisfy(size -> {
      assertThat(size[0]).isEqualTo(200);
      assertThat(size[1]).isEqualTo(120);
    });
  }

  @Test
  void reencode_downscalesEveryFrameUniformly() throws IOException {
    // given: 최장변이 한계를 넘는 애니메이션 GIF (한계를 100으로 낮춰 검증)
    byte[] gif = animatedGif(400, 200, 3, 5);

    // when
    byte[] result = AnimatedGifTranscoder.reencode(gif, 100);

    // then: 모든 프레임이 같은 크기로 축소된다 (프레임마다 크기가 다르면 애니메이션이 떨린다)
    List<int[]> sizes = readFrameSizes(result);
    assertThat(sizes).hasSize(3);
    assertThat(sizes).allSatisfy(size -> {
      assertThat(size[0]).isEqualTo(100);
      assertThat(size[1]).isEqualTo(50);
    });
  }

  @Test
  void reencode_rejectsWhenFrameCountExceedsLimit() throws IOException {
    // given: 프레임 상한을 1장 넘긴 GIF
    byte[] gif = animatedGif(4, 4, AnimatedGifTranscoder.MAX_FRAMES + 1, 1);

    // when / then: 업로드 스레드를 오래 점유하지 못하도록 거부한다
    assertThatThrownBy(() -> AnimatedGifTranscoder.reencode(gif, ImageSanitizer.MAX_DIMENSION))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("프레임이 너무 많습니다");
  }

  @Test
  void reencode_rejectsTruncatedGif() throws IOException {
    // given: 뒤쪽이 잘려 나간 손상된 GIF
    byte[] gif = animatedGif(40, 40, 4, 5);
    byte[] truncated = new byte[gif.length / 2];
    System.arraycopy(gif, 0, truncated, 0, truncated.length);

    // when / then: 조용히 통과시키지 않고 실패시킨다
    assertThatThrownBy(() -> AnimatedGifTranscoder.reencode(truncated, ImageSanitizer.MAX_DIMENSION))
        .isInstanceOf(IOException.class);
  }

  // ---------- helpers ----------

  /** delayTime(centisecond)을 지정한 N프레임 GIF를 만든다. 프레임마다 색이 다르다. */
  private static byte[] animatedGif(int width, int height, int frames, int delayCentis) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      writer.prepareWriteSequence(writer.getDefaultStreamMetadata(param));

      for (int i = 0; i < frames; i++) {
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = frame.createGraphics();
        g.setColor(new Color((i * 70) % 256, (i * 30) % 256, 200));
        g.fillRect(0, 0, width, height);
        g.dispose();

        IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frame), param);
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(IMAGE_METADATA_FORMAT);
        IIOMetadataNode control = firstChild(root, "GraphicControlExtension");
        if (control == null) {
          control = new IIOMetadataNode("GraphicControlExtension");
          root.appendChild(control);
        }
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(delayCentis));
        metadata.setFromTree(IMAGE_METADATA_FORMAT, root);

        writer.writeToSequence(new IIOImage(frame, null, metadata), param);
      }
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private static byte[] pngBytes(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private static List<Integer> readDelays(byte[] gif) throws IOException {
    List<Integer> delays = new ArrayList<>();
    forEachFrameMetadata(gif, root -> {
      IIOMetadataNode control = firstChild(root, "GraphicControlExtension");
      delays.add(control == null ? -1 : Integer.parseInt(control.getAttribute("delayTime")));
    });
    return delays;
  }

  private static List<int[]> readFrameSizes(byte[] gif) throws IOException {
    List<int[]> sizes = new ArrayList<>();
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
      ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
      try {
        reader.setInput(in);
        int frames = reader.getNumImages(true);
        for (int i = 0; i < frames; i++) {
          sizes.add(new int[]{reader.getWidth(i), reader.getHeight(i)});
        }
      } finally {
        reader.dispose();
      }
    }
    return sizes;
  }

  private static void forEachFrameMetadata(byte[] gif, java.util.function.Consumer<IIOMetadataNode> consumer)
      throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
      ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
      try {
        reader.setInput(in);
        int frames = reader.getNumImages(true);
        for (int i = 0; i < frames; i++) {
          IIOMetadata metadata = reader.getImageMetadata(i);
          consumer.accept((IIOMetadataNode) metadata.getAsTree(IMAGE_METADATA_FORMAT));
        }
      } finally {
        reader.dispose();
      }
    }
  }

  private static IIOMetadataNode firstChild(IIOMetadataNode parent, String name) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof IIOMetadataNode child && name.equals(child.getNodeName())) {
        return child;
      }
    }
    return null;
  }
}
