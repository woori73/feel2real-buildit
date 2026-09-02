package f2r.tutor_solution.private_tutor_solution_back.infrastructure.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 사용자 업로드 파일을 안전하게 내려주는 응답 빌더.
 *
 * <p>{@code LessonCommunityController}가 쓰던 규칙을 공용화: PDF/이미지만 inline 허용,
 * 나머지는 {@code application/octet-stream} + {@code attachment}, 한글 파일명은 RFC 5987,
 * {@code X-Content-Type-Options: nosniff}.
 */
public final class FileDownloadResponses {

  private FileDownloadResponses() {}

  public static ResponseEntity<byte[]> serve(byte[] bytes, String contentType, String filename) {
    MediaType mediaType = safeMediaType(contentType);
    boolean inline = isInlineSafe(mediaType);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename, inline))
        .contentType(mediaType)
        .body(bytes);
  }

  /** 항상 attachment로 강제하고 싶을 때(자료실 다운로드 등). */
  public static ResponseEntity<byte[]> attachment(byte[] bytes, String contentType, String filename) {
    MediaType mediaType = safeMediaType(contentType);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename, false))
        .contentType(mediaType)
        .body(bytes);
  }

  private static MediaType safeMediaType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      MediaType parsed = MediaType.parseMediaType(contentType);
      return isInlineSafe(parsed) ? parsed : MediaType.APPLICATION_OCTET_STREAM;
    } catch (Exception e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private static boolean isInlineSafe(MediaType mediaType) {
    if (mediaType == null) {
      return false;
    }
    if (MediaType.APPLICATION_PDF.includes(mediaType)) {
      return true;
    }
    return "image".equalsIgnoreCase(mediaType.getType()) && !"svg+xml".equalsIgnoreCase(mediaType.getSubtype());
  }

  private static String contentDisposition(String filename, boolean inline) {
    String safe = (filename == null || filename.isBlank()) ? "download" : filename;
    String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
    String asciiFallback = safe.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
    String disposition = inline ? "inline" : "attachment";
    return disposition + "; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
  }
}
