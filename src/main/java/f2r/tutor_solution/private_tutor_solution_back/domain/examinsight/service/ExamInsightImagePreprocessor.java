package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 사진을 이미지 인식 모델에 보내기 전에 안전하고 가볍게 다시 만든다.
 *
 * <ul>
 *   <li><b>검증:</b> 확장자나 헤더가 아니라 실제로 디코딩해 JPEG·PNG·WebP인지 확인한다. 픽셀 수 상한을 둔다(해상도 폭탄 방지).
 *   <li><b>재인코딩:</b> 항상 새 JPEG로 다시 쓴다. EXIF(위치 정보 포함)·덧붙은 데이터·다중 페이로드가 모두 사라진다.
 *   <li><b>비용 통제:</b> 긴 변을 {@code max-image-side}로 줄여 토큰·전송량을 묶는다. 큰 사진은 디코딩 단계에서 부분 샘플링해
 *       힙을 아낀다.
 *   <li><b>방향:</b> 휴대폰 사진의 EXIF 회전 값을 적용해 글자가 바로 서게 한다.
 * </ul>
 */
@Component
public class ExamInsightImagePreprocessor {

  /** 다시 만든 JPEG와 크기, 원본 바이트의 SHA-256(같은 사진 중복 호출 방지용). */
  public record Prepared(byte[] jpeg, int width, int height, String sha256) {}

  static final int MIN_SIDE = 80;
  private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png", "webp");
  private static final long SUBSAMPLE_ABOVE_PIXELS = 12_000_000L;
  private static final long SUBSAMPLE_TARGET_PIXELS = 6_000_000L;

  private final int maxSide;
  private final long maxPixels;

  @Autowired
  public ExamInsightImagePreprocessor(
      @Value("${app.exam-insight.max-image-side:1800}") int maxSide,
      @Value("${app.exam-insight.max-image-pixels:100000000}") long maxPixels) {
    this.maxSide = maxSide;
    this.maxPixels = maxPixels;
  }

  public Prepared prepare(byte[] original, String label) {
    String sha = sha256(original);
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
      Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
      if (readers == null || !readers.hasNext()) throw unreadable(label);
      ImageReader reader = readers.next();
      try {
        if (!ALLOWED_FORMATS.contains(reader.getFormatName().toLowerCase())) throw unreadable(label);
        reader.setInput(in, true, true);
        int w = reader.getWidth(0);
        int h = reader.getHeight(0);
        if (w < MIN_SIDE || h < MIN_SIDE) {
          throw ExamInsightException.badRequest("IMAGE_TOO_SMALL",
              "사진이 너무 작아 글자를 읽을 수 없어요(가로·세로 최소 " + MIN_SIDE + "px): " + label);
        }
        if ((long) w * h > maxPixels) {
          throw ExamInsightException.badRequest("IMAGE_TOO_LARGE",
              "사진 해상도가 너무 커요. 크기를 줄여 다시 올려 주세요: " + label);
        }
        ImageReadParam param = reader.getDefaultReadParam();
        long pixels = (long) w * h;
        if (pixels > SUBSAMPLE_ABOVE_PIXELS) {
          int f = (int) Math.ceil(Math.sqrt((double) pixels / SUBSAMPLE_TARGET_PIXELS));
          param.setSourceSubsampling(f, f, 0, 0);
        }
        BufferedImage decoded = reader.read(0, param);
        int orientation = reader.getFormatName().equalsIgnoreCase("jpeg") ? exifOrientation(original) : 1;
        BufferedImage oriented = orient(decoded, orientation);
        BufferedImage flat = scaleAndFlatten(oriented);
        return new Prepared(encodeJpeg(flat), flat.getWidth(), flat.getHeight(), sha);
      } finally {
        reader.dispose();
      }
    } catch (ExamInsightException e) {
      throw e;
    } catch (IOException | RuntimeException | OutOfMemoryError e) {
      throw unreadable(label);
    }
  }

  // ───────────────────────── 변환 ─────────────────────────

  private BufferedImage scaleAndFlatten(BufferedImage src) {
    int w = src.getWidth();
    int h = src.getHeight();
    double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
    int tw = Math.max(1, (int) Math.round(w * scale));
    int th = Math.max(1, (int) Math.round(h * scale));
    BufferedImage cur = src;
    // 한 번에 크게 줄이면 글자가 뭉개지므로 절반씩 줄이며 간다.
    while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
      cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2);
    }
    return draw(cur, tw, th);
  }

  private static BufferedImage draw(BufferedImage src, int w, int h) {
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setColor(Color.WHITE); // 투명 PNG는 흰 종이 위에 올린다
      g.fillRect(0, 0, w, h);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(src, 0, 0, w, h, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static byte[] encodeJpeg(BufferedImage img) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) throw new IOException("no jpeg writer");
    ImageWriter writer = writers.next();
    try {
      ImageWriteParam p = writer.getDefaultWriteParam();
      p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      p.setCompressionQuality(0.85f);
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bo)) {
        writer.setOutput(out);
        writer.write(null, new IIOImage(img, null, null), p);
      }
      return bo.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  /**
   * EXIF 방향 값(1~8)대로 이미지를 돌리거나 뒤집는다. 점 하나씩 옮겨 정의가 그대로 읽히게 한다
   * (2 좌우반전, 3 180도, 4 상하반전, 5 전치, 6 시계 90도, 7 전치+180도, 8 반시계 90도).
   */
  static BufferedImage orient(BufferedImage src, int orientation) {
    if (orientation <= 1 || orientation > 8) return src;
    int w = src.getWidth();
    int h = src.getHeight();
    boolean swap = orientation >= 5;
    BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int rgb = src.getRGB(x, y);
        switch (orientation) {
          case 2 -> out.setRGB(w - 1 - x, y, rgb);
          case 3 -> out.setRGB(w - 1 - x, h - 1 - y, rgb);
          case 4 -> out.setRGB(x, h - 1 - y, rgb);
          case 5 -> out.setRGB(y, x, rgb);
          case 6 -> out.setRGB(h - 1 - y, x, rgb);
          case 7 -> out.setRGB(h - 1 - y, w - 1 - x, rgb);
          default -> out.setRGB(y, w - 1 - x, rgb);
        }
      }
    }
    return out;
  }

  /** JPEG APP1(Exif)의 IFD0에서 방향 태그(0x0112)를 읽는다. 없거나 깨졌으면 1(회전 없음). */
  static int exifOrientation(byte[] b) {
    try {
      if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return 1;
      int pos = 2;
      while (pos + 4 < b.length) {
        if ((b[pos] & 0xFF) != 0xFF) return 1;
        int marker = b[pos + 1] & 0xFF;
        int len = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
        if (marker == 0xDA || len < 2) return 1;
        if (marker == 0xE1 && len >= 14 && b[pos + 4] == 'E' && b[pos + 5] == 'x' && b[pos + 6] == 'i'
            && b[pos + 7] == 'f') {
          int tiff = pos + 10;
          boolean le = b[tiff] == 'I';
          int ifd = tiff + (int) readInt(b, tiff + 4, 4, le);
          int count = (int) readInt(b, ifd, 2, le);
          for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (readInt(b, entry, 2, le) == 0x0112) {
              int v = (int) readInt(b, entry + 8, 2, le);
              return v >= 1 && v <= 8 ? v : 1;
            }
          }
          return 1;
        }
        pos += 2 + len;
      }
    } catch (RuntimeException e) {
      return 1;
    }
    return 1;
  }

  private static long readInt(byte[] b, int off, int n, boolean le) {
    long v = 0;
    for (int i = 0; i < n; i++) {
      int byteVal = b[off + (le ? n - 1 - i : i)] & 0xFF;
      v = (v << 8) | byteVal;
    }
    return v;
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ExamInsightException unreadable(String label) {
    return ExamInsightException.badRequest("IMAGE_UNREADABLE",
        "사진 파일을 읽을 수 없어요. JPG·PNG·WebP인지, 손상되지 않았는지 확인해 주세요: " + label);
  }
}
