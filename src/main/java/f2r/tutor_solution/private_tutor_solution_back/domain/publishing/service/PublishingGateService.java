package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PreflightResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PDF 출력 검증 게이트 (P2).
 *
 * <p>실측 기준 ({@code backend-verification.md}):
 * <ul>
 *   <li>overset = 0, missingGlyphs = 0, PPI ≥ 150, 페이지 수 일치
 *   <li>X-4 요청 시: OutputIntent 존재, ICC N=4, DeviceRGB 부재
 * </ul>
 *
 * <p>외부 도구(qpdf/pikepdf) 없이 PDF 원시 내용 검사로 판정한다.
 * {@code pdfinfo} 200 OK가 아니라 실측값으로 판정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishingGateService {

  private static final Pattern PAGE_COUNT = Pattern.compile("/Count\\s+(\\d+)");
  private static final Pattern OUTPUT_INTENT = Pattern.compile("/S\\s*/GTS_PDFX");
  private static final Pattern ICC_N4 = Pattern.compile("/N\\s+4");

  /** 게이트 검증 결과. */
  public record GateResult(boolean passed, List<String> failures) {
  }

  /**
   * preflight 결과에 대한 기본 게이트.
   */
  public GateResult checkPreflight(PreflightResponse preflight, int expectedMinPpi) {
    List<String> failures = new ArrayList<>();
    for (var f : preflight.overset()) {
      failures.add("[overset] " + f.detail()
          + (f.page() != null ? " (p." + f.page() + ")" : ""));
    }
    for (var g : preflight.missingGlyphs()) {
      failures.add("[missingGlyphs] " + g.detail()
          + (g.page() != null ? " (p." + g.page() + ")" : "")
          + (g.glyph() != null && !g.glyph().isEmpty() ? " glyph=" + g.glyph() : ""));
    }
    for (var p : preflight.lowPpi()) {
      failures.add("[lowPpi] " + p.detail()
          + (p.page() != null ? " (p." + p.page() + ")" : "")
          + (p.ppi() != null ? " ppi=" + p.ppi() : ""));
    }
    return new GateResult(failures.isEmpty(), failures);
  }

  /**
   * 출력 PDF 파일에 대한 X-4 게이트.
   *
   * @param pdfFile 출력된 PDF
   * @param expectedPages 기대 페이지 수 (null이면 검사 생략)
   */
  public GateResult checkPdfX4(Path pdfFile, Integer expectedPages) {
    List<String> failures = new ArrayList<>();
    String content;
    try {
      byte[] bytes = Files.readAllBytes(pdfFile);
      // PDF 헤더·트레일러는 Latin-1로 읽어야 안전
      content = new String(bytes, StandardCharsets.ISO_8859_1);
    } catch (IOException e) {
      return new GateResult(false, List.of("PDF 읽기 실패: " + e.getMessage()));
    }

    // 1. OutputIntent (PDF/X) 존재
    if (!OUTPUT_INTENT.matcher(content).find()) {
      failures.add("OutputIntent(GTS_PDFX)가 없습니다. X-4 출력이 아닙니다.");
    }
    // 2. ICC N=4 (CMYK)
    if (!ICC_N4.matcher(content).find()) {
      failures.add("CMYK ICC 프로필(N=4)이 없습니다. RGB로 변환되었을 수 있습니다.");
    }
    // 3. DeviceRGB 부재 (X-4 CMYK 경로에서는 없어야 함)
    if (content.contains("/DeviceRGB")) {
      failures.add("DeviceRGB이 포함되어 있습니다. CMYK 경로를 확인하세요.");
    }
    // 4. 페이지 수 일치
    if (expectedPages != null) {
      Integer actual = extractPageCount(content);
      if (actual == null) {
        failures.add("페이지 수를 확인할 수 없습니다.");
      } else if (!actual.equals(expectedPages)) {
        failures.add("페이지 수 불일치: 기대 " + expectedPages + ", 실제 " + actual);
      }
    }
    // 5. 한글 폰트 임베딩 확인 (NotoSansKR)
    if (!content.contains("NotoSansKR")) {
      failures.add("NotoSansKR 폰트가 임베드되지 않았습니다.");
    }

    if (!failures.isEmpty()) {
      log.warn("PDF/X-4 게이트 실패: {}", failures);
    }
    return new GateResult(failures.isEmpty(), failures);
  }

  /**
   * PDF/A-2b 출력 검증.
   *
   * <p><b>CLI 0.6.0 실측 확인된 제한사항</b>:
   * <ul>
   *   <li>XMP 메타데이터는 {@code pdfaid:part=2}로 올바르게 기록됨
   *   <li>그러나 OutputIntent 서브타입이 {@code /GTS_PDFA1}로 기록됨 (CLI 버그, {@code /GTS_PDFA2}여야 함)
   *   <li>CMYK 색상이 강제로 RGB로 변환됨 ("no CMYK output intent profile is available yet")
   * </ul>
   * 따라서 A-2b는 <b>RGB 아카이빙 전용</b>으로만 사용하고, 인쇄용 CMYK는 X-4를 사용해야 한다.
   *
   * @return 항상 passed=true (경고만 기록). 실패 조건이 아님.
   */
  public GateResult checkPdfA2b(Path pdfFile) {
    List<String> failures = new ArrayList<>();
    try {
      String content = new String(Files.readAllBytes(pdfFile), StandardCharsets.ISO_8859_1);
      boolean hasPdfa1Subtype = content.contains("/GTS_PDFA1");
      boolean hasPart2 = content.contains("pdfaid:part>2");
      if (hasPdfa1Subtype && hasPart2) {
        log.warn("PDF/A-2b CLI 제한: XMP part=2이나 OutputIntent가 /GTS_PDFA1로 기록됨. "
            + "veraPDF 검증 시 flavour 1b로 판정될 수 있음. RGB 아카이빙 용도로만 사용.");
      }
      if (content.contains("CMYK colours were converted to RGB")
          || !content.contains("/N 4")) {
        log.warn("PDF/A-2b는 CMYK를 RGB로 강제 변환함. 인쇄용으로 사용하지 마세요.");
      }
    } catch (IOException e) {
      return new GateResult(false, List.of("PDF 읽기 실패: " + e.getMessage()));
    }
    return new GateResult(true, failures);
  }

  private Integer extractPageCount(String content) {
    // /Type /Pages ... /Count N 중 가장 큰 값 (루트 Pages)
    Matcher m = PAGE_COUNT.matcher(content);
    int max = -1;
    while (m.find()) {
      try {
        max = Math.max(max, Integer.parseInt(m.group(1)));
      } catch (NumberFormatException ignored) {
      }
    }
    return max >= 0 ? max : null;
  }
}
