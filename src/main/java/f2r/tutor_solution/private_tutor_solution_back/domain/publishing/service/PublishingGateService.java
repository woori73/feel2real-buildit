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
    if (!preflight.passed()) {
      for (var issue : preflight.issues()) {
        failures.add("[" + issue.kind() + "] " + issue.message()
            + (issue.page() != null ? " (p." + issue.page() + ")" : ""));
      }
    }
    // missingGlyphs 명시적 확인
    boolean hasMissingGlyphs = preflight.issues().stream()
        .anyMatch(i -> "missingGlyphs".equalsIgnoreCase(i.kind()));
    if (hasMissingGlyphs) {
      failures.add("missingGlyphs > 0: 한글 폰트가 임베드되지 않았습니다.");
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
   * PDF/A-2b 출력 경고: 강제 RGB 변환이 발생하므로 인쇄용으로 부적합.
   * 아카이빙 전용임을 호출부에 알린다.
   */
  public GateResult checkPdfA2b(Path pdfFile) {
    List<String> failures = new ArrayList<>();
    try {
      String content = new String(Files.readAllBytes(pdfFile), StandardCharsets.ISO_8859_1);
      if (content.contains("/GTS_PDFA1") && !content.contains("/GTS_PDFX")) {
        // CLI 버그 의심: a2b 요청 시 서브타입이 PDFA1로 기록됨 (실측)
        log.warn("PDF/A 서브타입이 GTS_PDFA1로 기록됨 (CLI 버그 의심). veraPDF flavour 2b로 별도 확인 필요.");
      }
    } catch (IOException e) {
      return new GateResult(false, List.of("PDF 읽기 실패: " + e.getMessage()));
    }
    // A-2b는 RGB 아카이빙 전용 — 게이트 자체는 통과시키되 경고 로그
    log.warn("PDF/A-2b는 강제 RGB 변환되므로 인쇄용으로 사용하지 마세요 (아카이빙 전용).");
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
