package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

/** 게시 요청에만 보관하는 첨부 이미지. 외부 URL이나 원본 파일명을 네이버에 넘기지 않는다. */
public record CafeImage(String mimeType, byte[] bytes) {
  public static final int MAX_BYTES = 4 * 1024 * 1024;
  public static final int MAX_TOTAL_BYTES = 12 * 1024 * 1024;
  public static final int MAX_IMAGES = 5;

  public CafeImage {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
      throw CafeService.error(413, "cafe_image_limit_4MiB");
    bytes = bytes.clone();
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw CafeService.error(400, "cafe_image_png_or_jpeg_required");
      var reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String detected = switch (format) {
          case "png" -> "image/png";
          case "jpeg", "jpg" -> "image/jpeg";
          default -> throw CafeService.error(400, "cafe_image_png_or_jpeg_required");
        };
        if (!detected.equals(mimeType)) throw CafeService.error(400, "cafe_image_type_mismatch");
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || width > 12000 || height > 12000
            || (long) width * height > 16_000_000)
          throw CafeService.error(400, "cafe_image_dimensions_limit");
        var decoded = reader.read(0);
        if (decoded == null) throw CafeService.error(400, "cafe_image_invalid");
        decoded.flush();
      } finally { reader.dispose(); }
    } catch (IOException | IllegalArgumentException e) {
      throw CafeService.error(400, "cafe_image_invalid");
    }
  }

  @Override public byte[] bytes() { return bytes.clone(); }
  @Override public String toString() { return "CafeImage[" + mimeType + ", " + bytes.length + " bytes]"; }

  public static List<CafeImage> validate(List<CafeImage> images) {
    if (images == null) return List.of();
    if (images.size() > MAX_IMAGES || images.stream().anyMatch(Objects::isNull))
      throw CafeService.error(400, "cafe_image_limit_5_files");
    if (images.stream().mapToLong(i -> i.bytes.length).sum() > MAX_TOTAL_BYTES)
      throw CafeService.error(413, "cafe_images_total_limit_12MiB");
    return List.copyOf(images);
  }
}
