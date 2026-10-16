package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ExamInsightTextExtractorTest {

  final ExamInsightTextExtractor ex = new ExamInsightTextExtractor(mock(DocumentUploadValidator.class));

  static byte[] zip(String entry, String xml) throws Exception {
    ByteArrayOutputStream bo = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(bo)) {
      z.putNextEntry(new ZipEntry(entry));
      z.write(xml.getBytes(StandardCharsets.UTF_8));
      z.closeEntry();
    }
    return bo.toByteArray();
  }

  @Test
  void txt는_본문을_돌려준다() {
    // given
    MockMultipartFile f = new MockMultipartFile("f", "past.txt", "text/plain", "1. Choose the best word.\n\n\n\n2. Fill in.".getBytes(StandardCharsets.UTF_8));
    // when
    ExamInsightTextExtractor.Extracted r = ex.extract(f);
    // then
    assertThat(r.format()).isEqualTo("txt");
    assertThat(r.text()).isEqualTo("1. Choose the best word.\n\n2. Fill in.");
  }

  @Test
  void 이미지는_명확한_안내와_함께_거절한다() {
    // given
    MockMultipartFile f = new MockMultipartFile("f", "scan.jpg", "image/jpeg", new byte[] {1, 2, 3});
    // when / then
    assertThatThrownBy(() -> ex.extract(f))
        .isInstanceOfSatisfying(ExamInsightException.class, e -> assertThat(e.getCode()).isEqualTo("UNSUPPORTED_IMAGE"));
  }

  @Test
  void docx_문단_텍스트를_읽는다() throws Exception {
    // given minimal docx
    String xml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
        + "<w:p><w:r><w:t>Hello</w:t></w:r></w:p><w:p><w:r><w:t>World</w:t></w:r></w:p></w:body></w:document>";
    MockMultipartFile f = new MockMultipartFile("f", "a.docx", "application/octet-stream", zip("word/document.xml", xml));
    // when
    ExamInsightTextExtractor.Extracted r = ex.extract(f);
    // then
    assertThat(r.text()).contains("Hello").contains("World");
  }

  @Test
  void 깨진_docx는_PARSE_FAILED() {
    // given not a zip
    MockMultipartFile f = new MockMultipartFile("f", "a.docx", "application/octet-stream", "nope".getBytes(StandardCharsets.UTF_8));
    // when / then
    assertThatThrownBy(() -> ex.extract(f)).isInstanceOf(ExamInsightException.class);
  }

  @Test
  void plainText는_MS949도_읽는다() {
    // given cp949 bytes
    byte[] b = "영어 시험".getBytes(Charset.forName("x-windows-949"));
    // when / then
    assertThat(ExamInsightTextExtractor.plainText(b)).isEqualTo("영어 시험");
  }

  @Test
  void clean은_제어문자와_연속빈줄을_정리한다() {
    // given / when / then
    assertThat(ExamInsightTextExtractor.clean("a\u0000b  \r\n\r\n\r\n\r\nc")).isEqualTo("ab\n\nc");
    assertThat(ExamInsightTextExtractor.clean(null)).isEmpty();
  }

  @Test
  void safe는_경로를_지운다() {
    // given / when / then
    assertThat(ExamInsightTextExtractor.safe("C:\\x\\y/z.pdf")).isEqualTo("z.pdf");
    assertThat(ExamInsightTextExtractor.safe(null)).isEqualTo("(이름 없음)");
  }

  @Test
  void 지원_형식_목록은_OCR이_필요한_형식을_포함하지_않는다() {
    // given / when / then
    assertThat(ExamInsightTextExtractor.SUPPORTED_EXTENSIONS).containsExactly("pdf", "docx", "hwpx", "txt", "md");
  }
}
