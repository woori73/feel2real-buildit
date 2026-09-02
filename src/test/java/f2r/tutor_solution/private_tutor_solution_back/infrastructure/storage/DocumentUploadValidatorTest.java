package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("DocumentUploadValidator — 확장자 + 매직바이트 교차검증, 실행파일 차단")
class DocumentUploadValidatorTest {

  private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};
  private static final byte[] OLE2 =
      {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
  private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14};
  private static final byte[] MZ = {0x4D, 0x5A, (byte) 0x90, 0x00};
  private static final byte[] ELF = {0x7F, 0x45, 0x4C, 0x46};

  private final DocumentUploadValidator validator =
      new DocumentUploadValidator(new UploadSizeGuard(20L * 1024 * 1024));

  private MockMultipartFile file(String name, byte[] bytes) {
    return new MockMultipartFile("file", name, "application/octet-stream", bytes);
  }

  @Test
  @DisplayName("given 각 확장자에 맞는 매직 when validate then 통과")
  void accepts_matchingExtensionAndMagic() {
    assertThatCode(() -> validator.validate(file("a.pdf", PDF))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.hwp", OLE2))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.doc", OLE2))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.xls", OLE2))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.ppt", OLE2))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.hwpx", ZIP))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.docx", ZIP))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.xlsx", ZIP))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.pptx", ZIP))).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(file("a.zip", ZIP))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("given 매직이 확장자 계열과 다르면 when validate then 400")
  void rejects_magicMismatch() {
    assertThatThrownBy(() -> validator.validate(file("a.pdf", ZIP)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("확장자와");
    assertThatThrownBy(() -> validator.validate(file("a.docx", PDF)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("확장자와");
  }

  @Test
  @DisplayName("given 실행파일(MZ/ELF) when validate then 400")
  void rejects_executables() {
    assertThatThrownBy(() -> validator.validate(file("a.pdf", MZ)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("실행 파일");
    assertThatThrownBy(() -> validator.validate(file("a.zip", ELF)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("실행 파일");
  }

  @Test
  @DisplayName("given 허용 외 확장자 when validate then 400")
  void rejects_disallowedExtension() {
    assertThatThrownBy(() -> validator.validate(file("a.exe", MZ)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("형식");
    assertThatThrownBy(() -> validator.validate(file("a.txt", PDF)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("형식");
  }

  @Test
  @DisplayName("given 확장자 없음 / 빈 파일 when validate then 400")
  void rejects_noExtensionOrEmpty() {
    assertThatThrownBy(() -> validator.validate(file("noext", PDF)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("확장자");
    assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0])))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("given 20MB 초과 when validate then 400")
  void rejects_oversize() {
    byte[] big = new byte[21 * 1024 * 1024];
    big[0] = 0x25; big[1] = 0x50; big[2] = 0x44; big[3] = 0x46;
    assertThatThrownBy(() -> validator.validate(file("a.pdf", big)))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("MB");
  }

  @Test
  @DisplayName("given 좁힌 화이트리스트(pdf만) when hwp then 400")
  void rejects_outsideNarrowedWhitelist() {
    assertThatThrownBy(() -> validator.validate(file("a.hwp", OLE2), Set.of("pdf")))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("형식");
  }

  @Test
  @DisplayName("contentType(): 확장자로 MIME 추론")
  void contentType_byExtension() {
    assertThat(DocumentUploadValidator.contentType("a.pdf")).isEqualTo("application/pdf");
    assertThat(DocumentUploadValidator.contentType("a.hwp")).isEqualTo("application/x-hwp");
    assertThat(DocumentUploadValidator.contentType("a.docx"))
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    assertThat(DocumentUploadValidator.contentType("weird")).isEqualTo("application/octet-stream");
  }
}
