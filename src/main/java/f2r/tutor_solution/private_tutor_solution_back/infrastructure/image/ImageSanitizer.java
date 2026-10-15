package f2r.tutor_solution.private_tutor_solution_back.infrastructure.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Slf4j
@Component
public class ImageSanitizer {

  /**
   * 저장 용량 관리: 가장 긴 변을 이 픽셀 수로 제한한다 (원본이 더 작으면 그대로 둔다).
   *
   * <p>public인 이유: 외부 디코더(ffmpeg HEIC 폴백)를 거치는 호출자가 자기 쪽 안전 천장을 이 값에
   * 맞춰야 최종 결과가 일관된다.
   */
  public static final int MAX_DIMENSION = 1600;
  /** JPEG 재인코딩 품질 (0~1). 화질과 용량의 절충값. */
  static final float JPEG_QUALITY = 0.82f;

  public static final String JPEG_CONTENT_TYPE = "image/jpeg";
  public static final String GIF_CONTENT_TYPE = "image/gif";
  public static final String WEBP_CONTENT_TYPE = "image/webp";
  public static final String JPEG_EXTENSION = "jpg";
  public static final String GIF_EXTENSION = "gif";
  public static final String WEBP_EXTENSION = "webp";

  /**
   * 정규화 결과. 바이트뿐 아니라 <b>실제 포맷</b>을 함께 돌려주는 게 핵심이다.
   *
   * <p>예전에는 모든 업로드가 JPEG로 나온다는 전제가 저장 확장자(.jpg)와 서빙 Content-Type에
   * 하드코딩돼 있었다. 애니메이션 GIF가 생기면서 그 전제가 깨졌으므로, 포맷은 호출자가 추측하지
   * 않고 이 값을 따라간다.
   */
  public record SanitizedImage(byte[] bytes, String contentType, String extension) {

    public boolean isAnimatedGif() {
      return GIF_CONTENT_TYPE.equals(contentType);
    }

    public boolean isAnimatedWebp() {
      return WEBP_CONTENT_TYPE.equals(contentType);
    }
  }

  /**
   * 저장 키의 확장자로 Content-Type을 판정한다.
   *
   * <p>Source/Academy 로고·커버처럼 Content-Type 컬럼이 없는 경로를 위한 것이다. 확장자를 붙이는
   * 쪽이 {@link SanitizedImage#extension()}이므로 두 값은 항상 함께 움직인다.
   *
   * <p>확장자가 없거나 모르는 레거시 키는 JPEG로 본다 — 이 기능 이전에 저장된 이미지는 전부
   * JPEG였으므로 backfill 없이 그대로 서빙된다.
   */
  public static String contentTypeForStorageKey(String storageKey) {
    String key = storageKey != null ? storageKey.toLowerCase() : "";
    if (key.endsWith("." + GIF_EXTENSION)) return GIF_CONTENT_TYPE;
    if (key.endsWith("." + WEBP_EXTENSION)) return WEBP_CONTENT_TYPE;
    return JPEG_CONTENT_TYPE;
  }

  /**
   * 업로드 이미지를 저장 가능한 형태로 정규화한다.
   *
   * <p>움직이는 이미지는 포맷을 유지하고, 나머지는 전부 JPEG로 재인코딩한다.
   * <ul>
   *   <li>애니메이션 GIF — 프레임을 보존해 GIF로 재인코딩</li>
   *   <li>애니메이션 WebP — 검증 후 메타데이터만 제거(픽셀은 원본 그대로).
   *       재인코딩하지 않는 이유는 {@link AnimatedWebp} 참고</li>
   *   <li>그 외(정지 WebP 포함) — JPEG로 재인코딩</li>
   * </ul>
   */
  public SanitizedImage sanitize(MultipartFile file) throws IOException {
    return sanitize(file.getBytes(), file.getOriginalFilename());
  }

  /** 이미 읽어 둔 바이트용 {@link #sanitize(MultipartFile)}. 같은 파일을 두 번 읽지 않는다. */
  public SanitizedImage sanitize(byte[] originalBytes, String filenameHint) throws IOException {
    if (AnimatedGifTranscoder.frameCount(originalBytes) > 1) {
      return new SanitizedImage(
          AnimatedGifTranscoder.reencode(originalBytes, MAX_DIMENSION), GIF_CONTENT_TYPE, GIF_EXTENSION);
    }
    if (AnimatedWebp.isAnimated(originalBytes)) {
      return new SanitizedImage(
          AnimatedWebp.validateAndStrip(originalBytes), WEBP_CONTENT_TYPE, WEBP_EXTENSION);
    }
    return new SanitizedImage(
        reencodeToJpeg(originalBytes, filenameHint), JPEG_CONTENT_TYPE, JPEG_EXTENSION);
  }

  /**
   * 이미지를 JPEG로 재인코딩한다.
   * <p>1) EXIF orientation을 픽셀에 반영(재인코딩 시 EXIF가 사라지므로 먼저 처리)
   * <p>2) 최장변이 {@link #MAX_DIMENSION}을 넘으면 비율 유지하며 축소(용량 관리)
   * <p>3) 투명 영역은 흰 배경으로 평탄화 후 품질 지정 JPEG로 인코딩
   */
  public byte[] reencodeToJpeg(MultipartFile file) throws IOException {
    return reencodeToJpeg(file.getBytes(), file.getOriginalFilename());
  }

  /**
   * 이미 읽어 둔 바이트로 재인코딩한다.
   *
   * <p>{@link #canDecode}로 미리 판정했거나 다른 디코더를 거쳐 온 바이트를 가진 호출자를 위한 것이다.
   * {@code MultipartFile} 버전을 쓰면 같은 파일을 두 번 읽게 되는데, 20MB 업로드에서 그 40MB가
   * 그대로 힙 부담이 된다.
   */
  public byte[] reencodeToJpeg(byte[] originalBytes, String filenameHint) throws IOException {
    BufferedImage image;
    try (InputStream in = new ByteArrayInputStream(originalBytes)) {
      image = ImageIO.read(in);
    }
    if (image == null) {
      throw new IOException("이미지를 디코딩할 수 없습니다: " + filenameHint);
    }

    int orientation = readExifOrientation(originalBytes);
    image = applyExifOrientation(image, orientation);
    image = downscaleToMaxDimension(image, MAX_DIMENSION);

    BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = rgb.createGraphics();
    g.drawImage(image, 0, 0, Color.WHITE, null);
    g.dispose();

    return writeJpeg(rgb, JPEG_QUALITY);
  }

  /**
   * ImageIO가 이 바이트를 디코딩할 수 있는지 헤더만 보고 판정한다. 픽셀은 디코딩하지 않는다.
   *
   * <p>{@link #reencodeToJpeg}는 디코딩 실패를 {@link IOException}으로 던지는데, 호출자는 그것이
   * "지원하지 않는 포맷"인지 "손상된 파일"인지 구분할 수 없다. HEIC 폴백처럼 포맷 문제일 때만
   * 다른 경로를 타야 하는 호출자를 위해 판정을 분리했다.
   *
   * <p>기존 호출자의 동작에는 영향이 없다(순수 가산).
   */
  public boolean canDecode(byte[] bytes) {
    return readDimension(bytes) != null;
  }

  /**
   * 디코딩 전에 헤더에서 픽셀 크기를 읽는다. 읽을 수 없으면 null.
   *
   * <p>디코딩된 {@code BufferedImage}는 픽셀당 4바이트를 잡으므로, 큰 이미지는 디코딩 자체가
   * 힙을 터뜨린다. 바이트 크기 상한만으로는 압축률 때문에 이걸 막을 수 없어 픽셀 수로 따로 판정한다.
   *
   * <p>주의: ImageIO가 읽지 못하는 포맷(HEIC 등)은 여기서도 null이라 픽셀 판정이 불가능하다.
   * 그 경로는 디코더 쪽(ffmpeg)에서 출력 크기를 제한해야 한다.
   */
  public Dimension readDimension(byte[] bytes) {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (in == null) {
        return null;
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        return null;
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in);
        return new Dimension(reader.getWidth(0), reader.getHeight(0));
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      return null;
    }
  }

  /** 헤더에서 읽은 이미지 크기. */
  public record Dimension(int width, int height) {
    public long pixels() {
      return (long) width * height;
    }
  }

  /** 최장변(가로/세로 중 큰 값)이 maxDim 이하면 원본을 그대로, 넘으면 비율 유지하며 축소한다. */
  static BufferedImage downscaleToMaxDimension(BufferedImage src, int maxDim) {
    int w = src.getWidth();
    int h = src.getHeight();
    int longest = Math.max(w, h);
    if (longest <= maxDim) return src;

    double scale = (double) maxDim / longest;
    int newW = Math.max(1, (int) Math.round(w * scale));
    int newH = Math.max(1, (int) Math.round(h * scale));

    BufferedImage dst = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.drawImage(src, 0, 0, newW, newH, Color.WHITE, null);
    g.dispose();
    return dst;
  }

  /** 품질 파라미터를 지정해 JPEG 바이트로 인코딩한다 (ImageIO 기본 인코딩은 품질 제어가 없다). */
  private static byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    if (param.canWriteCompressed()) {
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  public static int readExifOrientation(byte[] bytes) {
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      Metadata metadata = ImageMetadataReader.readMetadata(in);
      ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
        return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
      }
    } catch (Exception e) {
      log.debug("EXIF orientation 읽기 실패 — 회전 적용 안 함: {}", e.getMessage());
    }
    return 1;
  }

  public static BufferedImage applyExifOrientation(BufferedImage src, int orientation) {
    if (orientation <= 1 || orientation > 8) return src;
    int w = src.getWidth();
    int h = src.getHeight();
    AffineTransform tx = new AffineTransform();
    int newW = w;
    int newH = h;
    switch (orientation) {
      case 3 -> {
        tx.translate(w, h);
        tx.rotate(Math.PI);
      }
      case 6 -> {
        tx.translate(h, 0);
        tx.rotate(Math.PI / 2);
        newW = h;
        newH = w;
      }
      case 8 -> {
        tx.translate(0, w);
        tx.rotate(-Math.PI / 2);
        newW = h;
        newH = w;
      }
      default -> { return src; }
    }
    BufferedImage dst = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics();
    g.drawImage(src, tx, null);
    g.dispose();
    return dst;
  }
}
