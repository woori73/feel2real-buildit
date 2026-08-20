package f2r.tutor_solution.private_tutor_solution_back.infrastructure.image;

import lombok.extern.slf4j.Slf4j;
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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 애니메이션 GIF를 프레임을 보존한 채 재인코딩한다.
 *
 * <p>JDK ImageIO의 GIF writer는 {@code canWriteSequence() == true}라 멀티프레임 쓰기를 지원한다.
 * 외부 바이너리(ImageMagick/FFmpeg) 없이 in-process로 처리할 수 있는 이유다.
 *
 * <p><b>왜 프레임을 합성(composite)하는가</b>: GIF 프레임은 전체 화면이 아니라 부분 사각형일 수 있고,
 * disposal method에 따라 이전 프레임 위에 누적된다. 부분 프레임을 그대로 축소하면 오프셋이 깨져
 * 화면이 밀린다. 그래서 논리 화면(logical screen) 캔버스에 disposal 규칙대로 합성해 매 프레임을
 * 완전한 전체 프레임으로 만든 뒤, disposal을 {@code none}으로 기록한다. 파일은 조금 커지지만
 * 렌더링이 결정적이다.
 *
 * <p><b>메모리</b>: 프레임을 모두 모아 두지 않고 합성 즉시 시퀀스로 흘려보낸다. 따라서 상주 메모리는
 * 프레임 수가 아니라 캔버스 크기에 묶인다 (restoreToPrevious 대비 캔버스 1장 추가).
 */
@Slf4j
public final class AnimatedGifTranscoder {

  /** GIF 스트림/프레임 메타데이터 트리 이름 (JDK GIF 플러그인 native format). */
  private static final String STREAM_METADATA_FORMAT = "javax_imageio_gif_stream_1.0";
  private static final String IMAGE_METADATA_FORMAT = "javax_imageio_gif_image_1.0";

  /**
   * 프레임 수 상한. 프레임마다 캔버스 합성 + 축소 + 인코딩이 돌기 때문에 CPU 시간이 프레임 수에
   * 선형으로 늘어난다. 상한이 없으면 수천 프레임짜리 GIF 하나가 업로드 스레드를 오래 점유한다.
   */
  static final int MAX_FRAMES = 300;

  /**
   * 논리 화면 픽셀 상한. 합성 캔버스는 ARGB(픽셀당 4바이트)라 크기를 직접 제한해야 힙이 묶인다.
   * {@code app.image.max-decoded-pixels}와 같은 취지의 방어선이다.
   */
  static final long MAX_CANVAS_PIXELS = 40_000_000L;

  /**
   * 출력 바이트 상한. 부분 프레임을 전체 프레임으로 펴면 원본보다 커질 수 있어, 입력 크기만으로는
   * 출력을 묶을 수 없다. 넘으면 실패시킨다(무한정 커지는 대신 거부).
   */
  static final int MAX_OUTPUT_BYTES = 20 * 1024 * 1024;

  private static final int DEFAULT_DELAY_CENTIS = 10;

  private AnimatedGifTranscoder() {
  }

  /** GIF 시그니처(GIF87a/GIF89a) 여부. 픽셀은 건드리지 않는다. */
  public static boolean isGif(byte[] bytes) {
    if (bytes == null || bytes.length < 6) return false;
    return bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
        && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
  }

  /**
   * GIF의 프레임 수를 센다. GIF가 아니거나 읽을 수 없으면 0.
   *
   * <p>1이면 정지 GIF다 — 호출자는 기존 JPEG 경로를 그대로 태우면 된다.
   */
  public static int frameCount(byte[] bytes) {
    if (!isGif(bytes)) return 0;
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (in == null) return 0;
      Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
      if (!readers.hasNext()) return 0;
      ImageReader reader = readers.next();
      try {
        reader.setInput(in);
        return reader.getNumImages(true);
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalStateException e) {
      log.debug("GIF 프레임 수 판정 실패: {}", e.getMessage());
      return 0;
    }
  }

  /**
   * 애니메이션을 보존해 GIF로 재인코딩한다.
   *
   * <p>프레임 지연(delayTime)과 반복 횟수(Netscape loop extension)를 보존하고, 최장변이
   * {@code maxDimension}을 넘으면 모든 프레임을 같은 비율로 축소한다.
   *
   * @throws IOException 디코딩 실패, 프레임/픽셀/출력 상한 초과
   */
  public static byte[] reencode(byte[] originalBytes, int maxDimension) throws IOException {
    requireComplete(originalBytes);
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(originalBytes))) {
      if (in == null) {
        throw new IOException("GIF 입력 스트림을 열 수 없습니다.");
      }
      Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
      if (!readers.hasNext()) {
        throw new IOException("GIF reader를 찾을 수 없습니다.");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in);
        return reencodeWithReader(reader, maxDimension);
      } finally {
        reader.dispose();
      }
    }
  }

  /**
   * GIF trailer(0x3B)로 파일이 끝나는지 확인한다.
   *
   * <p>ImageIO의 GIF reader는 관대해서, 뒤가 잘린 GIF도 예외 없이 <b>읽히는 프레임까지만</b>
   * 돌려준다. 그대로 두면 전송 중 잘린 30프레임 GIF가 10프레임짜리로 조용히 저장된다. 프레임이
   * 말없이 사라지는 대신 업로드를 실패시킨다.
   *
   * <p>trailer 뒤의 NUL 패딩은 허용한다 — 일부 인코더가 붙이는데 손상은 아니다.
   */
  private static void requireComplete(byte[] bytes) throws IOException {
    if (!isGif(bytes)) {
      throw new IOException("GIF 파일이 아닙니다.");
    }
    int last = bytes.length - 1;
    while (last >= 0 && bytes[last] == 0) {
      last--;
    }
    if (last < 0 || bytes[last] != 0x3B) {
      throw new IOException("GIF 파일이 손상되었거나 잘렸습니다.");
    }
  }

  private static byte[] reencodeWithReader(ImageReader reader, int maxDimension) throws IOException {
    int frames = reader.getNumImages(true);
    if (frames < 1) {
      throw new IOException("GIF 프레임을 읽을 수 없습니다.");
    }
    if (frames > MAX_FRAMES) {
      throw new IOException("GIF 프레임이 너무 많습니다: " + frames + " (최대 " + MAX_FRAMES + ")");
    }

    Canvas canvas = readCanvasSize(reader);
    if ((long) canvas.width * canvas.height > MAX_CANVAS_PIXELS) {
      throw new IOException("GIF 화면이 너무 큽니다: " + canvas.width + "x" + canvas.height);
    }

    int loopCount = readLoopCount(reader);
    Scale scale = Scale.of(canvas.width, canvas.height, maxDimension);

    ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BufferedImage composed = newCanvas(canvas.width, canvas.height);

    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      writer.prepareWriteSequence(writer.getDefaultStreamMetadata(param));

      for (int i = 0; i < frames; i++) {
        FrameInfo info = readFrameInfo(reader, i);
        BufferedImage frame = reader.read(i);

        BufferedImage restorePoint = "restoreToPrevious".equals(info.disposalMethod)
            ? copyOf(composed)
            : null;

        drawFrame(composed, frame, info.x, info.y);

        BufferedImage output = scale.apply(composed);
        writer.writeToSequence(
            new IIOImage(output, null, frameMetadata(writer, param, output, info.delayCentis, i == 0, loopCount)),
            param);

        if (out.size() > MAX_OUTPUT_BYTES) {
          throw new IOException("GIF 재인코딩 결과가 상한을 넘었습니다: " + out.size() + " bytes");
        }

        applyDisposal(composed, info, restorePoint, frame.getWidth(), frame.getHeight());
      }
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  /** 프레임을 캔버스 위 (x, y)에 알파 합성한다. */
  private static void drawFrame(BufferedImage canvas, BufferedImage frame, int x, int y) {
    Graphics2D g = canvas.createGraphics();
    try {
      g.drawImage(frame, x, y, null);
    } finally {
      g.dispose();
    }
  }

  /**
   * 다음 프레임을 그리기 전에 현재 프레임의 disposal 규칙을 적용한다.
   *
   * <p>{@code restoreToBackgroundColor}는 이 프레임이 차지한 사각형만 투명으로 비우고,
   * {@code restoreToPrevious}는 그리기 직전 캔버스로 되돌린다. 나머지는 그대로 누적한다.
   */
  private static void applyDisposal(
      BufferedImage canvas, FrameInfo info, BufferedImage restorePoint, int frameWidth, int frameHeight) {
    switch (info.disposalMethod) {
      case "restoreToBackgroundColor" -> {
        Graphics2D g = canvas.createGraphics();
        try {
          g.setComposite(java.awt.AlphaComposite.Clear);
          g.fillRect(info.x, info.y, frameWidth, frameHeight);
        } finally {
          g.dispose();
        }
      }
      case "restoreToPrevious" -> {
        if (restorePoint != null) {
          Graphics2D g = canvas.createGraphics();
          try {
            g.setComposite(java.awt.AlphaComposite.Src);
            g.drawImage(restorePoint, 0, 0, null);
          } finally {
            g.dispose();
          }
        }
      }
      default -> {
        // none / doNotDispose: 캔버스 유지
      }
    }
  }

  private static BufferedImage newCanvas(int width, int height) {
    return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
  }

  private static BufferedImage copyOf(BufferedImage src) {
    BufferedImage copy = newCanvas(src.getWidth(), src.getHeight());
    Graphics2D g = copy.createGraphics();
    try {
      g.setComposite(java.awt.AlphaComposite.Src);
      g.drawImage(src, 0, 0, null);
    } finally {
      g.dispose();
    }
    return copy;
  }

  /** 논리 화면 크기. 프레임 0의 크기와 다를 수 있어 스트림 메타데이터에서 읽는다. */
  private static Canvas readCanvasSize(ImageReader reader) throws IOException {
    IIOMetadata metadata = reader.getStreamMetadata();
    if (metadata != null) {
      IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(STREAM_METADATA_FORMAT);
      IIOMetadataNode descriptor = firstChild(root, "LogicalScreenDescriptor");
      if (descriptor != null) {
        int width = attrAsInt(descriptor, "logicalScreenWidth", 0);
        int height = attrAsInt(descriptor, "logicalScreenHeight", 0);
        if (width > 0 && height > 0) {
          return new Canvas(width, height);
        }
      }
    }
    return new Canvas(reader.getWidth(0), reader.getHeight(0));
  }

  /** Netscape 2.0 application extension의 반복 횟수. 없으면 0(무한 반복). */
  private static int readLoopCount(ImageReader reader) {
    try {
      IIOMetadata metadata = reader.getStreamMetadata();
      if (metadata == null) return 0;
      IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(STREAM_METADATA_FORMAT);
      IIOMetadataNode extensions = firstChild(root, "ApplicationExtensions");
      if (extensions == null) return 0;
      for (Node node = extensions.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (!(node instanceof IIOMetadataNode extension)) continue;
        if (!"NETSCAPE".equals(extension.getAttribute("applicationID"))) continue;
        if (extension.getUserObject() instanceof byte[] data && data.length >= 3) {
          return (data[1] & 0xFF) | ((data[2] & 0xFF) << 8);
        }
      }
    } catch (IOException | RuntimeException e) {
      log.debug("GIF loop count 읽기 실패 — 무한 반복으로 처리: {}", e.getMessage());
    }
    return 0;
  }

  private static FrameInfo readFrameInfo(ImageReader reader, int index) {
    int x = 0;
    int y = 0;
    int delay = DEFAULT_DELAY_CENTIS;
    String disposal = "none";
    try {
      IIOMetadata metadata = reader.getImageMetadata(index);
      IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(IMAGE_METADATA_FORMAT);
      IIOMetadataNode descriptor = firstChild(root, "ImageDescriptor");
      if (descriptor != null) {
        x = attrAsInt(descriptor, "imageLeftPosition", 0);
        y = attrAsInt(descriptor, "imageTopPosition", 0);
      }
      IIOMetadataNode control = firstChild(root, "GraphicControlExtension");
      if (control != null) {
        delay = attrAsInt(control, "delayTime", DEFAULT_DELAY_CENTIS);
        String value = control.getAttribute("disposalMethod");
        if (value != null && !value.isBlank()) disposal = value;
      }
    } catch (IOException | RuntimeException e) {
      log.debug("GIF 프레임 {} 메타데이터 읽기 실패 — 기본값 사용: {}", index, e.getMessage());
    }
    // 지연 0은 브라우저마다 해석이 갈린다(멈춤 또는 최고속). 원본 의도를 살리는 최소값으로 올린다.
    if (delay <= 0) delay = DEFAULT_DELAY_CENTIS;
    return new FrameInfo(x, y, delay, disposal);
  }

  /**
   * 출력 프레임 메타데이터. 프레임을 전체 화면으로 펴 두었으므로 disposal은 {@code none}이다.
   * 반복 횟수는 GIF 규격상 첫 프레임에만 기록한다.
   */
  private static IIOMetadata frameMetadata(
      ImageWriter writer, ImageWriteParam param, BufferedImage image,
      int delayCentis, boolean firstFrame, int loopCount) throws IOException {

    IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), param);
    IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(IMAGE_METADATA_FORMAT);

    IIOMetadataNode control = firstChild(root, "GraphicControlExtension");
    if (control == null) {
      control = new IIOMetadataNode("GraphicControlExtension");
      root.appendChild(control);
    }
    control.setAttribute("disposalMethod", "none");
    control.setAttribute("userInputFlag", "FALSE");
    control.setAttribute("delayTime", Integer.toString(delayCentis));

    if (firstFrame) {
      IIOMetadataNode extensions = new IIOMetadataNode("ApplicationExtensions");
      IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
      extension.setAttribute("applicationID", "NETSCAPE");
      extension.setAttribute("authenticationCode", "2.0");
      extension.setUserObject(new byte[]{
          0x1, (byte) (loopCount & 0xFF), (byte) ((loopCount >> 8) & 0xFF)
      });
      extensions.appendChild(extension);
      root.appendChild(extensions);
    }

    metadata.setFromTree(IMAGE_METADATA_FORMAT, root);
    return metadata;
  }

  private static IIOMetadataNode firstChild(IIOMetadataNode parent, String name) {
    if (parent == null) return null;
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof IIOMetadataNode child && name.equals(child.getNodeName())) {
        return child;
      }
    }
    return null;
  }

  private static int attrAsInt(IIOMetadataNode node, String name, int fallback) {
    String value = node.getAttribute(name);
    if (value == null || value.isBlank()) return fallback;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private record Canvas(int width, int height) {
  }

  private record FrameInfo(int x, int y, int delayCentis, String disposalMethod) {
  }

  /** 모든 프레임에 동일하게 적용할 축소 비율. 프레임마다 다르면 애니메이션이 떨린다. */
  private record Scale(int width, int height, boolean identity) {

    static Scale of(int width, int height, int maxDimension) {
      int longest = Math.max(width, height);
      if (longest <= maxDimension) {
        return new Scale(width, height, true);
      }
      double ratio = (double) maxDimension / longest;
      return new Scale(
          Math.max(1, (int) Math.round(width * ratio)),
          Math.max(1, (int) Math.round(height * ratio)),
          false);
    }

    BufferedImage apply(BufferedImage source) {
      if (identity) {
        return copyOf(source);
      }
      BufferedImage target = newCanvas(width, height);
      Graphics2D g = target.createGraphics();
      try {
        g.setComposite(java.awt.AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
      } finally {
        g.dispose();
      }
      return target;
    }
  }
}
