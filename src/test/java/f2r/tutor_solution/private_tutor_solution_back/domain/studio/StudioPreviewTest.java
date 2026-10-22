package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StudioPreviewTest {
  private byte[] zip(String name, byte[] bytes) throws IOException {
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(bytes);
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  @Test
  void givenHostileHtml_whenRendered_thenNoActiveOrExternalContent() {
    String result =
        StudioPreview.document(
            "<h1>콘티</h1><script>alert(1)</script><iframe src='https://bad'></iframe><img"
                + " src='https://bad/pixel' onerror='alert(2)'><a"
                + " href='javascript:alert(3)'>link</a><form"
                + " action='https://bad'><input></form><svg onload='bad()'></svg><style>@import"
                + " 'https://bad';</style>",
            "html");
    var doc = org.jsoup.Jsoup.parse(result);
    assertEquals("콘티", doc.selectFirst("h1").text());
    assertTrue(doc.select("script,iframe,img,a,form,input,svg").isEmpty());
    assertFalse(result.contains("https://bad"));
    assertTrue(result.contains("default-src 'none'"));
  }

  @Test
  void givenMarkdown_whenRendered_thenSemanticReadableAndRawHtmlEscaped() {
    String result =
        StudioPreview.document(
            "# 장면\n\n"
                + "**강조**\n\n"
                + "- 첫 컷\n"
                + "- 둘째 컷\n\n"
                + "<script>alert(1)</script>\n\n"
                + "![외부](https://bad/pixel)",
            "md");
    var doc = org.jsoup.Jsoup.parse(result);
    assertEquals("장면", doc.selectFirst("h1").text());
    assertEquals(2, doc.select("li").size());
    assertEquals("강조", doc.selectFirst("strong").text());
    assertTrue(doc.select("script,img").isEmpty());
  }

  @Test
  void givenArchive_whenSelected_thenExactBytesAndNoExtraction() throws Exception {
    byte[] source = "# 원본".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(source, StudioPreview.zipEntry(zip("docs/a.md", source), "docs/a.md"));
    assertThrows(
        ResponseStatusException.class,
        () -> StudioPreview.zipEntry(zip("../a.md", source), "../a.md"));
    assertThrows(
        ResponseStatusException.class,
        () -> StudioPreview.zipEntry(zip("a.md", source), "missing.md"));
    assertThrows(
        ResponseStatusException.class, () -> StudioPreview.zipEntry(zip("a.exe", source), "a.exe"));
  }

  @Test
  void givenZipBomb_whenSelected_thenSizeRatioRejected() throws Exception {
    byte[] bomb = zip("large.md", new byte[StudioPreview.ENTRY_LIMIT + 1]);
    assertThrows(ResponseStatusException.class, () -> StudioPreview.zipEntry(bomb, "large.md"));
  }

  @Test
  void givenImage_whenPreviewed_thenBoundedDimensionsAndOriginalUnchanged() throws Exception {
    var original =
        new java.awt.image.BufferedImage(2400, 1200, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var bytes = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(original, "png", bytes);
    byte[] source = bytes.toByteArray(), copy = source.clone();
    var image =
        javax.imageio.ImageIO.read(new ByteArrayInputStream(StudioPreview.thumbnail(source)));
    assertTrue(image.getWidth() <= 1024);
    assertTrue(image.getHeight() <= 1024);
    assertArrayEquals(copy, source);
    assertThrows(ResponseStatusException.class, () -> StudioPreview.thumbnail("bad".getBytes()));
  }

  @Test
  void givenPdf_whenOpened_thenActualPagePixelsAndBoundedNavigation() throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
      doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      doc.save(bytes);
    }
    var page = StudioPreview.renderPdf(bytes.toByteArray(), 1);
    assertEquals(1, page.pages());
    assertTrue(javax.imageio.ImageIO.read(new ByteArrayInputStream(page.bytes())).getWidth() > 0);
    assertThrows(
        ResponseStatusException.class, () -> StudioPreview.renderPdf(bytes.toByteArray(), 2));
  }

  @Test
  void givenUnauthorizedUser_whenPreviewRequested_thenNoStorageRead() throws Exception {
    var studio = mock(StudioService.class);
    when(studio.file(null, "foreign")).thenThrow(StudioService.error(403, "denied"));
    var preview = new StudioPreview(studio);
    assertThrows(ResponseStatusException.class, () -> preview.view(null, "foreign", null));
    verify(studio, never()).bytes(any(), any());
    assertThrows(ResponseStatusException.class, () -> preview.entries(null, "foreign"));
  }
}
