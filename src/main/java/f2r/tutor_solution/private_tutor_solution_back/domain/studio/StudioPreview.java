package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.*;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/** Bounded, transient views. Never rewrites an original or creates a stored derivative. */
@Service
@RequiredArgsConstructor
public class StudioPreview {
  private final StudioService studio;
  private final Semaphore reads = new Semaphore(2);
  static final int SOURCE_LIMIT = 20 * 1024 * 1024;
  static final int ENTRY_LIMIT = 8 * 1024 * 1024;
  static final Set<String> TYPES = Set.of("png", "pdf", "html", "md", "json", "mp4");

  public record View(byte[] bytes, String mime, String extension) {}

  public View view(AuthenticatedPrincipal p, String id, String entry) throws IOException {
    var metadata = studio.file(p, id); // Authorize before storage/CPU use.
    if (((Number) metadata.get("size_bytes")).longValue() > SOURCE_LIMIT)
      throw StudioService.error(413, "미리보기는 원본 20 MiB까지 지원합니다.");
    if (!reads.tryAcquire()) throw StudioService.error(429, "미리보기가 진행 중입니다. 잠시 후 다시 시도하세요.");
    try {
      String ext = (String) metadata.get("extension");
      byte[] bytes = studio.bytes(p, id);
      if (entry != null) {
        if (!ext.equals("zip")) throw StudioService.error(400, "ZIP 파일만 내부 항목을 조회할 수 있습니다.");
        bytes = zipEntry(bytes, entry);
        ext = extension(entry);
      }
      if (!TYPES.contains(ext)) throw StudioService.error(415, "이 형식은 목록 또는 원본 다운로드를 이용하세요.");
      if (Set.of("html", "md").contains(ext))
        return new View(
            document(new String(bytes, StandardCharsets.UTF_8), ext)
                .getBytes(StandardCharsets.UTF_8),
            "text/html;charset=UTF-8",
            ext);
      if (ext.equals("png")) return new View(thumbnail(bytes), "image/png", ext);
      if (ext.equals("json")) return new View(bytes, "application/json", ext);
      return new View(bytes, ext.equals("pdf") ? "application/pdf" : "video/mp4", ext);
    } finally {
      reads.release();
    }
  }

  public record PdfPage(byte[] bytes, int pages) {}

  public PdfPage pdfPage(AuthenticatedPrincipal p, String id, String entry, int page)
      throws IOException {
    var view = view(p, id, entry);
    if (!view.extension().equals("pdf")) throw StudioService.error(400, "PDF 파일이 아닙니다.");
    if (!reads.tryAcquire()) throw StudioService.error(429, "PDF 열람이 진행 중입니다.");
    try {
      return renderPdf(view.bytes(), page);
    } finally {
      reads.release();
    }
  }

  static PdfPage renderPdf(byte[] bytes, int page) throws IOException {
    try (var document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      int count = document.getNumberOfPages();
      if (page < 1 || page > count || page > 100)
        throw StudioService.error(400, "PDF 미리보기는 처음 100페이지까지 가능합니다.");
      var box = document.getPage(page - 1).getCropBox();
      float max = Math.max(box.getWidth(), box.getHeight());
      if (!Float.isFinite(max) || max <= 0 || max > 100000)
        throw StudioService.error(422, "PDF 페이지 크기를 확인하세요.");
      var renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
      renderer.setSubsamplingAllowed(true);
      var image =
          renderer.renderImage(
              page - 1, Math.min(1.5f, 1200 / max), org.apache.pdfbox.rendering.ImageType.RGB);
      var output = new ByteArrayOutputStream();
      javax.imageio.ImageIO.write(image, "png", output);
      return new PdfPage(output.toByteArray(), count);
    }
  }

  public Object entries(AuthenticatedPrincipal p, String id) throws IOException {
    var f = studio.file(p, id);
    if (!"zip".equals(f.get("extension"))) throw StudioService.error(400, "ZIP 파일이 아닙니다.");
    if (((Number) f.get("size_bytes")).longValue() > SOURCE_LIMIT)
      throw StudioService.error(413, "ZIP 미리보기는 20 MiB까지 지원합니다.");
    if (!reads.tryAcquire()) throw StudioService.error(429, "미리보기가 진행 중입니다.");
    try {
      return archive(studio.bytes(p, id), null);
    } finally {
      reads.release();
    }
  }

  static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  static boolean safeName(String name) {
    return name != null
        && name.length() <= 512
        && !name.startsWith("/")
        && !name.contains("\\")
        && !name.contains(":")
        && !name.matches(".*[\\p{Cntrl}].*")
        && Arrays.stream(name.split("/", -1)).noneMatch(v -> v.equals("..") || v.equals("."));
  }

  static byte[] zipEntry(byte[] bytes, String name) throws IOException {
    return (byte[]) archive(bytes, name);
  }

  private static Object archive(byte[] bytes, String selected) throws IOException {
    if (selected != null && !safeName(selected))
      throw StudioService.error(400, "안전하지 않은 ZIP 경로입니다.");
    // Central directory gives complete lengths without inflating every entry. Temp file is always
    // removed.
    var path = java.nio.file.Files.createTempFile("f2r-studio-view-", ".zip");
    try {
      java.nio.file.Files.write(path, bytes);
      try (var zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
        var rows = new ArrayList<Map<String, Object>>();
        var names = new HashSet<String>();
        var entries = zip.entries();
        ZipEntry found = null;
        while (entries.hasMoreElements()) {
          var e = entries.nextElement();
          if (rows.size() >= 1000) throw StudioService.error(413, "ZIP 항목은 최대 1000개까지 열람합니다.");
          if (!names.add(e.getName())) throw StudioService.error(422, "중복 ZIP 경로를 열람할 수 없습니다.");
          boolean supported =
              !e.isDirectory()
                  && safeName(e.getName())
                  && TYPES.contains(extension(e.getName()))
                  && e.getSize() >= 0
                  && e.getSize() <= ENTRY_LIMIT
                  && e.getSize() <= Math.max(1, e.getCompressedSize()) * 1000;
          rows.add(Map.of("name", e.getName(), "size", e.getSize(), "previewable", supported));
          if (e.getName().equals(selected)) {
            if (!supported) throw StudioService.error(415, "지원하지 않거나 크기 제한을 넘은 ZIP 항목입니다.");
            found = e;
          }
        }
        if (selected == null)
          return Map.of(
              "entries", rows, "note", "원본 20 MiB · 항목 1000개 · 선택 항목 8 MiB · 중첩 ZIP 열람 불가");
        if (found == null) throw StudioService.error(404, "ZIP 항목이 없습니다.");
        try (var in = zip.getInputStream(found)) {
          byte[] data = in.readNBytes(ENTRY_LIMIT + 1);
          if (data.length > ENTRY_LIMIT) throw StudioService.error(413, "ZIP 항목 크기 초과");
          CRC32 crc = new CRC32();
          crc.update(data);
          if (data.length != found.getSize() || crc.getValue() != found.getCrc())
            throw StudioService.error(422, "ZIP 무결성 오류");
          return data;
        }
      }
    } finally {
      java.nio.file.Files.deleteIfExists(path);
    }
  }

  static String document(String source, String ext) {
    if (source.length() > 200000) throw StudioService.error(413, "문서 미리보기는 20만 자까지 지원합니다.");
    String html = source;
    if (ext.equals("md"))
      html =
          org.commonmark.renderer.html.HtmlRenderer.builder()
              .escapeHtml(true)
              .build()
              .render(org.commonmark.parser.Parser.builder().build().parse(source));
    // No links, images, styles, embeds, form controls or remote resources survive sanitization.
    String clean =
        Jsoup.clean(
            html,
            new Safelist()
                .addTags(
                    "p",
                    "br",
                    "hr",
                    "h1",
                    "h2",
                    "h3",
                    "h4",
                    "h5",
                    "h6",
                    "strong",
                    "b",
                    "em",
                    "i",
                    "ul",
                    "ol",
                    "li",
                    "blockquote",
                    "pre",
                    "code",
                    "table",
                    "thead",
                    "tbody",
                    "tr",
                    "th",
                    "td",
                    "del"));
    return "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><meta"
               + " http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src"
               + " 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><meta name=\"viewport\""
               + " content=\"width=device-width,initial-scale=1\"><style>body{font:16px/1.8"
               + " system-ui;color:#172b3a;padding:20px;overflow-wrap:anywhere}pre{white-space:pre-wrap;background:#f2f5f7;padding:12px}table{border-collapse:collapse}td,th{border:1px"
               + " solid #aaa;padding:8px}h1,h2,h3{line-height:1.4}</style></head><body>"
        + clean
        + "</body></html>";
  }

  static byte[] thumbnail(byte[] source) throws IOException {
    try (var input =
        javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
      var readers = javax.imageio.ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw StudioService.error(422, "이미지를 읽을 수 없습니다.");
      var reader = readers.next();
      try {
        reader.setInput(input);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 1 || h < 1 || (long) w * h > 40000000)
          throw StudioService.error(413, "이미지 해상도가 너무 큽니다.");
        var params = reader.getDefaultReadParam();
        int sample = Math.max(1, (Math.max(w, h) + 1023) / 1024);
        params.setSourceSubsampling(sample, sample, 0, 0);
        var image = reader.read(0, params);
        var out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
      } finally {
        reader.dispose();
      }
    }
  }
}
