package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 정보 화면 원문 읽기: 형식별 경로와 실패 사유. 자료는 테스트용 가상 내용이다. */
class MaterialTextExtractorTest {

  @Test
  @DisplayName("given 문단과 표가 있는 docx when 읽으면 then 본문 순서대로 문단·열 제목 표가 나온다")
  void docxParagraphsAndTablesInOrder() throws Exception {
    // given
    byte[] docx;
    try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
      doc.createParagraph().createRun().setText("11월 반 편성 안내");
      var table = doc.createTable(3, 2);
      table.getRow(0).getCell(0).setText("이름"); table.getRow(0).getCell(1).setText("반");
      table.getRow(1).getCell(0).setText("가상학생A"); table.getRow(1).getCell(1).setText("중1 A반");
      table.getRow(2).getCell(0).setText("가상학생B"); table.getRow(2).getCell(1).setText("중2 B반");
      doc.createParagraph().createRun().setText("문의는 원장에게");
      doc.write(out);
      docx = out.toByteArray();
    }
    // when
    var r = MaterialTextExtractor.extract("docx", docx);
    // then
    assertThat(r.status()).isEqualTo("READ");
    assertThat(r.blocks()).extracting(MaterialTextExtractor.Block::type).containsExactly("PARAGRAPH", "TABLE", "PARAGRAPH");
    assertThat(r.blocks().get(1).header()).containsExactly("이름", "반");
    assertThat(r.blocks().get(1).rows()).hasSize(2);
    assertThat(r.blocks().get(1).rows().get(0)).containsExactly("가상학생A", "중1 A반");
  }

  @Test
  @DisplayName("given 따옴표 안 쉼표·CP949 CSV when 읽으면 then 첫 줄을 열 제목으로 한 표가 된다")
  void csvWithQuotesAndKoreanWindowsEncoding() {
    // given
    byte[] utf8 = "﻿이름,메모\n가상학생,\"수학, 영어\"\n".getBytes(StandardCharsets.UTF_8);
    byte[] cp949 = "이름,학년\n가상학생,중2\n".getBytes(Charset.forName("MS949"));
    // when
    var a = MaterialTextExtractor.extract("csv", utf8);
    var b = MaterialTextExtractor.extract("CSV", cp949);
    // then
    assertThat(a.blocks().get(0).header()).containsExactly("이름", "메모");
    assertThat(a.blocks().get(0).rows().get(0)).containsExactly("가상학생", "수학, 영어");
    assertThat(b.blocks().get(0).rows().get(0)).containsExactly("가상학생", "중2");
  }

  @Test
  @DisplayName("given 글자 층이 있는 PDF when 읽으면 then 페이지 순서대로 번호와 함께 나온다")
  void pdfPagesInOrder() throws Exception {
    // given
    byte[] pdf = pdf("First page notice", "Second page schedule");
    // when
    var r = MaterialTextExtractor.extract("pdf", pdf);
    // then
    assertThat(r.status()).isEqualTo("READ");
    assertThat(r.pageCount()).isEqualTo(2);
    assertThat(r.blocks()).extracting(MaterialTextExtractor.Block::page).containsExactly(1, 2);
    assertThat(r.blocks().get(1).text()).contains("Second page schedule");
  }

  @Test
  @DisplayName("given 글자 없는(스캔) PDF·이미지 when 읽으면 then 본문을 지어내지 않고 OCR 필요 사유만 준다")
  void scannedPdfAndImagesNeedOcr() throws Exception {
    // given
    byte[] blank = pdf();
    // when
    var scanned = MaterialTextExtractor.extract("pdf", blank);
    var image = MaterialTextExtractor.extract("jpg", new byte[] {1, 2, 3});
    // then
    assertThat(scanned.status()).isEqualTo("NEEDS_OCR");
    assertThat(scanned.blocks()).isEmpty();
    assertThat(scanned.reason()).contains("OCR");
    assertThat(image.status()).isEqualTo("NEEDS_OCR");
    assertThat(image.blocks()).isEmpty();
  }

  @Test
  @DisplayName("given 손상 파일·미지원 형식·빈 파일 when 읽으면 then 각각 실패·미지원·비어 있음 사유를 준다")
  void failuresHaveReasons() {
    assertThat(MaterialTextExtractor.extract("docx", "not a zip".getBytes()).status()).isEqualTo("FAILED");
    assertThat(MaterialTextExtractor.extract("zip", new byte[] {1}).status()).isEqualTo("UNSUPPORTED");
    assertThat(MaterialTextExtractor.extract("txt", new byte[0]).status()).isEqualTo("EMPTY");
    assertThat(MaterialTextExtractor.extract("txt", "   \n\n ".getBytes()).status()).isEqualTo("EMPTY");
  }

  @Test
  @DisplayName("given 빈 줄로 나뉜 텍스트·HTML when 읽으면 then 문단으로 나누고 태그는 뺀다")
  void textAndHtmlParagraphs() {
    var t = MaterialTextExtractor.extract("md", "첫 문단 줄1\n줄2\n\n둘째 문단".getBytes(StandardCharsets.UTF_8));
    assertThat(t.blocks()).extracting(MaterialTextExtractor.Block::text).containsExactly("첫 문단 줄1\n줄2", "둘째 문단");
    var h = MaterialTextExtractor.extract("html", "<html><body><h1>안내</h1><script>x()</script><p>본문</p></body></html>".getBytes(StandardCharsets.UTF_8));
    assertThat(h.blocks().get(0).text()).contains("안내").contains("본문").doesNotContain("<p>");
  }

  private static byte[] pdf(String... pages) throws Exception {
    try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
      if (pages.length == 0) doc.addPage(new PDPage());
      for (String text : pages) {
        var page = new PDPage();
        doc.addPage(page);
        try (var cs = new PDPageContentStream(doc, page)) {
          cs.beginText();
          cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
          cs.newLineAtOffset(72, 700);
          cs.showText(text);
          cs.endText();
        }
      }
      doc.save(out);
      return out.toByteArray();
    }
  }
}
