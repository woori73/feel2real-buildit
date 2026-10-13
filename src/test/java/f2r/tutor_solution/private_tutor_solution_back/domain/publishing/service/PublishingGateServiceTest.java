package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse.PreflightIssue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublishingGateServiceTest {

  private PublishingGateService gateService;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    gateService = new PublishingGateService();
  }

  @Test
  @DisplayName("preflight 통과 시 게이트 통과")
  void preflightPass() {
    PreflightResponse preflight = new PreflightResponse(true, List.of(), List.of());
    var result = gateService.checkPreflight(preflight, 150);
    assertTrue(result.passed());
  }

  @Test
  @DisplayName("missingGlyphs가 있으면 게이트 실패")
  void missingGlyphsFail() {
    PreflightResponse preflight = new PreflightResponse(false,
        List.of(new PreflightIssue("error", "missingGlyphs", "글리프 누락", 1)),
        List.of());
    var result = gateService.checkPreflight(preflight, 150);
    assertFalse(result.passed());
  }

  @Test
  @DisplayName("X-4 PDF 게이트: OutputIntent·ICC N=4·폰트 모두 있으면 통과")
  void pdfX4Pass() throws Exception {
    String pdf = "%PDF-1.7\n"
        + "/Type /Pages /Count 6\n"
        + "/S /GTS_PDFX /OutputConditionIdentifier (Custom)\n"
        + "/N 4 /Subtype /ICCBased\n"
        + "(NotoSansKR-Regular-Identity-H)\n";
    Path file = tempDir.resolve("test.pdf");
    Files.write(file, pdf.getBytes(StandardCharsets.ISO_8859_1));
    var result = gateService.checkPdfX4(file, 6);
    assertTrue(result.passed(), String.join("; ", result.failures()));
  }

  @Test
  @DisplayName("X-4 PDF 게이트: DeviceRGB 포함 시 실패")
  void pdfX4DeviceRgbFail() throws Exception {
    String pdf = "%PDF-1.7\n"
        + "/Type /Pages /Count 2\n"
        + "/S /GTS_PDFX\n"
        + "/N 4\n"
        + "/DeviceRGB\n"
        + "(NotoSansKR-Regular)\n";
    Path file = tempDir.resolve("test.pdf");
    Files.write(file, pdf.getBytes(StandardCharsets.ISO_8859_1));
    var result = gateService.checkPdfX4(file, 2);
    assertFalse(result.passed());
  }

  @Test
  @DisplayName("X-4 PDF 게이트: 페이지 수 불일치 시 실패")
  void pdfX4PageCountFail() throws Exception {
    String pdf = "%PDF-1.7\n"
        + "/Type /Pages /Count 4\n"
        + "/S /GTS_PDFX\n"
        + "/N 4\n"
        + "(NotoSansKR-Regular)\n";
    Path file = tempDir.resolve("test.pdf");
    Files.write(file, pdf.getBytes(StandardCharsets.ISO_8859_1));
    var result = gateService.checkPdfX4(file, 6);
    assertFalse(result.passed());
  }
}
