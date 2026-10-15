package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 문서/이미지를 안전한 저장 형태로 정규화한 뒤 {@link FileStorage}에 영속화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadedFileIngestor {

  private static final int PDF_DPI = 150;
  private static final int PDF_MAX_PAGE = 20;

  private final FileStorage fileStorage;
  private final ImageSanitizer imageSanitizer;
  private final UploadSizeGuard uploadSizeGuard;

  public record StoredFile(String storageKey, String fileType, long fileSize) {}

  public StoredFile store(MultipartFile file, Long userId) {
    return store(file, userId, resolveFileType(file));
  }

  public StoredFile store(MultipartFile file, Long userId, String fileType) {
    uploadSizeGuard.requireWithinDocumentLimit(file);
    try {
      if ("IMAGE".equals(fileType)) {
        ImageSanitizer.SanitizedImage sanitized = imageSanitizer.sanitize(file);
        String key = fileStorage.saveImage(
            userId, file.getOriginalFilename(), sanitized.bytes(), sanitized.extension());
        return new StoredFile(key, "IMAGE", sanitized.bytes().length);
      }
      if ("PDF".equals(fileType)) {
        List<byte[]> pages = renderPdfPages(file);
        long totalSize = pages.stream().mapToLong(bytes -> bytes.length).sum();
        String key = fileStorage.savePdfPages(userId, pages);
        return new StoredFile(key, "PDF", totalSize);
      }
      try (InputStream in = file.getInputStream()) {
        String key = fileStorage.saveRaw(userId, file.getOriginalFilename(), in, file.getSize());
        return new StoredFile(key, fileType, file.getSize());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("파일 저장 실패", e);
    }
  }

  public String resolveFileType(MultipartFile file) {
    String ct = file != null ? file.getContentType() : null;
    if (ct == null) return "UNKNOWN";
    if (ct.startsWith("image/")) return "IMAGE";
    if (ct.equals("application/pdf")) return "PDF";
    return "UNKNOWN";
  }

  public List<byte[]> renderPdfPages(MultipartFile file) throws IOException {
    try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
      PDFRenderer renderer = new PDFRenderer(doc);
      int pageCount = Math.min(doc.getNumberOfPages(), PDF_MAX_PAGE);
      List<byte[]> pages = new ArrayList<>(pageCount);
      for (int i = 0; i < pageCount; i++) {
        BufferedImage pageImage = renderer.renderImageWithDPI(i, PDF_DPI, ImageType.RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        if (!ImageIO.write(pageImage, "jpeg", out)) {
          throw new IOException("PDF 페이지 JPEG 인코딩 실패: page " + (i + 1));
        }
        pages.add(out.toByteArray());
      }
      if (pageCount < doc.getNumberOfPages()) {
        log.warn("PDF 페이지 수({})가 최대치({})를 초과하여 앞 {} 페이지만 저장",
            doc.getNumberOfPages(), PDF_MAX_PAGE, PDF_MAX_PAGE);
      }
      return pages;
    }
  }
}
