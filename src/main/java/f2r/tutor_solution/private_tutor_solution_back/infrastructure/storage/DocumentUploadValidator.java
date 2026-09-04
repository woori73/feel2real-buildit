package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 BuildIt Wiki 자료실·커뮤니티 첨부의 문서 파일 업로드 검증.
 *
 * <p>확장자 화이트리스트 + 매직바이트(파일 시그니처) 교차검증 + 실행파일 차단 + 용량 상한.
 * Tika 등 별도 라이브러리 없이 문서 계열(PDF / OLE2(hwp·doc·xls·ppt) / ZIP(hwpx·docx·xlsx·pptx·zip)),
 * HTML, 일반 이미지(JPEG / PNG / WebP)를 허용한다.
 */
@Component
public class DocumentUploadValidator {

  /** HTML 확장자. Wiki의 모든 문서 업로드 경로에서 허용한다. */
  private static final Set<String> HTML_EXTENSIONS = Set.of("html", "htm");

  /** 문서 계열 허용 확장자(소문자, 점 없음) — 자료실 기본 화이트리스트. HTML 포함. */
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
      "pdf", "hwp", "hwpx", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "html", "htm");

  /** 일반 이미지 확장자. 게시글 첨부(변형문제·자료 공유·요청)에서 문서와 함께 허용한다. */
  private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

  /** 게시글 첨부용 화이트리스트 = 문서(HTML 포함) + 이미지. */
  private static final Set<String> ATTACHMENT_EXTENSIONS = union(ALLOWED_EXTENSIONS, IMAGE_EXTENSIONS);

  /**
   * 호환용 상수. 과거 Question Wiki ADMIN "직접 올리기" 전용으로 사용했지만,
   * 이제 기본 문서 정책 자체가 HTML 을 허용하므로 ALLOWED_EXTENSIONS 와 동일하다.
   */
  public static final Set<String> DOCUMENT_WITH_HTML_EXTENSIONS = union(ALLOWED_EXTENSIONS, HTML_EXTENSIONS);

  private static final byte[] MAGIC_PDF = {0x25, 0x50, 0x44, 0x46};                                // %PDF
  private static final byte[] MAGIC_OLE2 =
      {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};  // hwp5/doc/xls/ppt
  private static final byte[] MAGIC_ZIP_LOCAL = {0x50, 0x4B, 0x03, 0x04};                          // PK.. (hwpx/docx/...)
  private static final byte[] MAGIC_ZIP_EMPTY = {0x50, 0x4B, 0x05, 0x06};
  private static final byte[] MAGIC_ZIP_SPANNED = {0x50, 0x4B, 0x07, 0x08};

  private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};                // JPEG/JFIF/EXIF
  private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}; // ‰PNG␍␊␚␊
  private static final byte[] MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};                               // RIFF (WebP 컨테이너)
  private static final byte[] MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};                               // WEBP (RIFF offset 8)

  private static final byte[] MAGIC_MZ = {0x4D, 0x5A};                                             // PE(.exe/.dll)
  private static final byte[] MAGIC_ELF = {0x7F, 0x45, 0x4C, 0x46};                                // ELF
  private static final byte[] MAGIC_SHEBANG = {0x23, 0x21};                                        // #!

  private final UploadSizeGuard uploadSizeGuard;

  public DocumentUploadValidator(UploadSizeGuard uploadSizeGuard) {
    this.uploadSizeGuard = uploadSizeGuard;
  }

  /** Wiki 자료실 기본: 문서 + HTML. 이미지 불허. */
  public void validate(MultipartFile file) {
    validate(file, ALLOWED_EXTENSIONS);
  }

  /** 게시글 첨부: 문서 + HTML + 이미지(JPEG/PNG/WebP). 실행파일 위장은 계속 차단. */
  public void validateAttachment(MultipartFile file) {
    validate(file, ATTACHMENT_EXTENSIONS);
  }

  /** 확장자 화이트리스트를 좁혀서 검증한다. */
  public void validate(MultipartFile file, Set<String> allowedExtensions) {
    if (file == null || file.isEmpty()) {
      throw badRequest("파일을 업로드해주세요.");
    }
    uploadSizeGuard.requireWithinDocumentLimit(file);

    String extension = extensionOf(file.getOriginalFilename());
    if (extension == null) {
      throw badRequest("파일 확장자를 확인할 수 없습니다.");
    }
    if (!allowedExtensions.contains(extension)) {
      throw badRequest("허용되지 않는 파일 형식입니다: ." + extension);
    }

    byte[] head = readHead(file);
    if (startsWith(head, MAGIC_MZ) || startsWith(head, MAGIC_ELF) || startsWith(head, MAGIC_SHEBANG)) {
      throw badRequest("실행 파일은 업로드할 수 없습니다.");
    }
    if (!magicMatchesExtension(head, extension)) {
      throw badRequest("파일 내용이 확장자와 일치하지 않습니다.");
    }
  }

  /**
   * HTML 은 매직바이트가 없는 텍스트 포맷이라, 실행파일 위장(MZ/ELF/{@code #!})을 배제한 뒤
   * 선행 공백/BOM 을 건너뛴 첫 바이트가 {@code '<'} 인지로 판별한다.
   */
  private static boolean looksLikeHtml(byte[] head) {
    int i = 0;
    if (head.length >= 3
        && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
      i = 3;
    }
    while (i < head.length
        && (head[i] == ' ' || head[i] == '\t' || head[i] == '\r' || head[i] == '\n')) {
      i++;
    }
    return i < head.length && head[i] == '<';
  }

  /** 확장자로 응답 Content-Type을 추론한다. 알 수 없으면 application/octet-stream. */
  public static String contentType(String filename) {
    String extension = extensionOf(filename);
    if (extension == null) {
      return "application/octet-stream";
    }
    return switch (extension) {
      case "pdf" -> "application/pdf";
      case "hwp" -> "application/x-hwp";
      case "hwpx" -> "application/haansofthwpx";
      case "doc" -> "application/msword";
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      case "xls" -> "application/vnd.ms-excel";
      case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      case "ppt" -> "application/vnd.ms-powerpoint";
      case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
      case "zip" -> "application/zip";
      case "html", "htm" -> "text/html";
      case "jpg", "jpeg" -> "image/jpeg";
      case "png" -> "image/png";
      case "webp" -> "image/webp";
      default -> "application/octet-stream";
    };
  }

  private boolean magicMatchesExtension(byte[] head, String extension) {
    return switch (extension) {
      case "pdf" -> startsWith(head, MAGIC_PDF);
      case "hwp", "doc", "xls", "ppt" -> startsWith(head, MAGIC_OLE2);
      case "hwpx", "docx", "xlsx", "pptx", "zip" ->
          startsWith(head, MAGIC_ZIP_LOCAL) || startsWith(head, MAGIC_ZIP_EMPTY) || startsWith(head, MAGIC_ZIP_SPANNED);
      case "jpg", "jpeg" -> startsWith(head, MAGIC_JPEG);
      case "png" -> startsWith(head, MAGIC_PNG);
      case "webp" -> startsWith(head, MAGIC_RIFF) && regionEquals(head, 8, MAGIC_WEBP);
      case "html", "htm" -> looksLikeHtml(head);
      default -> false;
    };
  }

  private byte[] readHead(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return in.readNBytes(16);
    } catch (IOException e) {
      throw badRequest("파일을 읽을 수 없습니다.");
    }
  }

  private static boolean regionEquals(byte[] data, int offset, byte[] expected) {
    if (data.length < offset + expected.length) {
      return false;
    }
    return Arrays.equals(data, offset, offset + expected.length, expected, 0, expected.length);
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    java.util.HashSet<String> merged = new java.util.HashSet<>(a);
    merged.addAll(b);
    return Set.copyOf(merged);
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    return Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
  }

  private static String extensionOf(String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return null;
    }
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  /** 화면·서비스에서 accept 속성/안내에 쓸 문서 허용 확장자 목록. */
  public static List<String> allowedExtensions() {
    return List.of("pdf", "hwp", "hwpx", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "html", "htm");
  }

  /** 게시글 첨부 accept 속성/안내에 쓸 허용 확장자 목록(문서 + HTML + 이미지). */
  public static List<String> attachmentAllowedExtensions() {
    return List.of("pdf", "hwp", "hwpx", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip",
        "html", "htm", "jpg", "jpeg", "png", "webp");
  }
}
