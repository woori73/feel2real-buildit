package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfMarkTitleTest {

  @TempDir Path dir;
  private static final String KO = "한글 교재 템플릿 (A4)";

  @Test
  @DisplayName("한글 제목이면 템플릿별 ASCII 마크 제목을 쓴다")
  void markTitleMapsEachTemplate() {
    // given / when / then
    assertThat(PdfMarkTitle.markTitleFor("ko-textbook-a4", KO)).isEqualTo("Korean Textbook Template (A4)");
    assertThat(PdfMarkTitle.markTitleFor("ko-workbook-b5", "한글 문제집 템플릿 (B5)"))
        .isEqualTo("Korean Workbook Template (B5)");
    assertThat(PdfMarkTitle.markTitleFor("ko-book-46", "한글 단행본 템플릿 (46판)"))
        .isEqualTo("Korean Book Template (46)");
  }

  @Test
  @DisplayName("이미 ASCII이거나 제목을 모르면 변경하지 않는다(null)")
  void markTitleIsNullWhenNothingToFix() {
    // given / when / then
    assertThat(PdfMarkTitle.markTitleFor("ko-textbook-a4", "Plain English Title")).isNull();
    assertThat(PdfMarkTitle.markTitleFor("ko-textbook-a4", null)).isNull();
    assertThat(PdfMarkTitle.markTitleFor("ko-textbook-a4", "  ")).isNull();
  }

  @Test
  @DisplayName("알 수 없는 템플릿은 코드(ASCII) 또는 Document로 대체한다")
  void unknownTemplateFallsBackToAscii() {
    // given / when / then
    assertThat(PdfMarkTitle.markTitleFor("custom-x", KO)).isEqualTo("custom-x");
    assertThat(PdfMarkTitle.markTitleFor("커스텀", KO)).isEqualTo("Document");
    assertThat(PdfMarkTitle.markTitleFor(null, KO)).isEqualTo("Document");
  }

  @Test
  @DisplayName(".designcraft(zip)의 document.json 제목을 읽는다")
  void readsDocumentTitleFromDesigncraftZip() throws Exception {
    // given
    Path file = dir.resolve("a.designcraft");
    writeZip(file, "document.json", "{\"title\":\"" + KO + "\",\"x\":1}");
    // when / then
    assertThat(PdfMarkTitle.readDocumentTitle(file, new ObjectMapper())).isEqualTo(KO);
  }

  @Test
  @DisplayName("파일이 없거나 zip이 아니거나 title이 없으면 null")
  void readDocumentTitleReturnsNullOnProblems() throws Exception {
    // given
    Path notZip = dir.resolve("bad.designcraft");
    Files.writeString(notZip, "not a zip");
    Path noTitle = dir.resolve("n.designcraft");
    writeZip(noTitle, "document.json", "{\"a\":1}");
    Path noDoc = dir.resolve("d.designcraft");
    writeZip(noDoc, "other.json", "{}");
    // when / then
    ObjectMapper om = new ObjectMapper();
    assertThat(PdfMarkTitle.readDocumentTitle(dir.resolve("missing"), om)).isNull();
    assertThat(PdfMarkTitle.readDocumentTitle(notZip, om)).isNull();
    assertThat(PdfMarkTitle.readDocumentTitle(noTitle, om)).isNull();
    assertThat(PdfMarkTitle.readDocumentTitle(noDoc, om)).isNull();
  }

  @Test
  @DisplayName("Info 제목과 XMP dc:title만 원래 제목으로 되돌리고 나머지는 유지한다")
  void restoreTitleRewritesInfoAndXmpOnly() throws Exception {
    // given: ASCII 제목으로 만든 PDF (Info + XMP)
    Path pdf = dir.resolve("out.pdf");
    makePdf(pdf, "Korean Textbook Template (A4)", 2);
    // when
    PdfMarkTitle.restoreTitle(pdf, KO);
    // then
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      assertThat(doc.getDocumentInformation().getTitle()).isEqualTo(KO);
      assertThat(doc.getDocumentInformation().getAuthor()).isEqualTo("keep-author");
      assertThat(doc.getNumberOfPages()).isEqualTo(2);
      String xmp = new String(doc.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
      assertThat(xmp).contains(KO).doesNotContain("Korean Textbook Template").contains("<dc:creator>");
    }
    assertThat(Files.exists(dir.resolve("out.pdf.title.tmp"))).isFalse();
  }

  @Test
  @DisplayName("XML 특수문자가 있는 제목도 안전하게 XMP에 기록한다")
  void restoreTitleEscapesXml() throws Exception {
    // given
    Path pdf = dir.resolve("esc.pdf");
    makePdf(pdf, "ascii", 1);
    // when
    PdfMarkTitle.restoreTitle(pdf, "한글 & <교재>");
    // then
    try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
      assertThat(doc.getDocumentInformation().getTitle()).isEqualTo("한글 & <교재>");
      String xmp = new String(doc.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
      assertThat(xmp).contains("한글 &amp; &lt;교재&gt;");
    }
  }

  @Test
  @DisplayName("저장은 압축 없이 해서 기존 X-4 게이트의 문자열 검사가 유지된다")
  void restoreTitleKeepsKeywordsGreppableInRawBytes() throws Exception {
    // given
    Path pdf = dir.resolve("raw.pdf");
    makePdf(pdf, "ascii", 1);
    // when
    PdfMarkTitle.restoreTitle(pdf, KO);
    // then: 카탈로그/메타데이터 키워드가 원문 바이트에 그대로 보인다
    String raw = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
    assertThat(raw).contains("/Metadata").contains("/Catalog").doesNotContain("/ObjStm");
  }

  @Test
  @DisplayName("PDF가 아니면 IOException이고 임시 파일을 남기지 않는다")
  void restoreTitleFailsCleanlyOnBrokenPdf() throws Exception {
    // given
    Path pdf = dir.resolve("broken.pdf");
    Files.writeString(pdf, "not a pdf");
    // when / then
    assertThatThrownBy(() -> PdfMarkTitle.restoreTitle(pdf, KO)).isInstanceOf(IOException.class);
    assertThat(Files.readString(pdf)).isEqualTo("not a pdf");
    assertThat(Files.exists(dir.resolve("broken.pdf.title.tmp"))).isFalse();
  }

  private static void writeZip(Path file, String name, String content) throws IOException {
    try (OutputStream os = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(os)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }

  private static void makePdf(Path file, String title, int pages) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      for (int i = 0; i < pages; i++) {
        doc.addPage(new PDPage());
      }
      doc.getDocumentInformation().setTitle(title);
      doc.getDocumentInformation().setAuthor("keep-author");
      String xmp = "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?><x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
          + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
          + "<rdf:Description xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
          + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">" + title + "</rdf:li></rdf:Alt></dc:title>"
          + "<dc:creator><rdf:Seq><rdf:li>keep-author</rdf:li></rdf:Seq></dc:creator>"
          + "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";
      PDMetadata md = new PDMetadata(doc);
      md.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
      doc.getDocumentCatalog().setMetadata(md);
      doc.save(file.toFile());
    }
  }
}
