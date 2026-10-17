package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 designcraft-cli로 A4·B5·46판의 인쇄 마크 제목 변경을 검증한다.
 * 환경변수 DESIGNCRAFT_CLI(실행 파일 경로)가 있을 때만 실행되며 일반 CI에서는 건너뛴다.
 */
@EnabledIfEnvironmentVariable(named = "DESIGNCRAFT_CLI", matches = ".+")
class PdfMarkTitleRealCliTest {

  @TempDir Path dir;
  private static final Pattern FOOTER = Pattern.compile("^(.*) Page (\\d+) .*UTC\\s*$");

  @ParameterizedTest
  @ValueSource(strings = {"ko-textbook-a4", "ko-workbook-b5", "ko-book-46"})
  @DisplayName("마크 제목만 ASCII가 되고 본문·페이지 번호·메타데이터 제목은 그대로다")
  void markTitleFixKeepsBodyPageNumbersAndMetadata(String code) throws Exception {
    // given: 같은 템플릿·원고로 기존 방식(baseline)과 수정 방식을 각각 내보낸다
    Path template = Paths.get("ops/designcraft/templates", code);
    Path work = dir.resolve(code);
    Files.createDirectories(work);
    Files.copy(template.resolve("template.designcraft"), work.resolve("job.designcraft"));
    Files.copy(template.resolve("sample-manuscript.md"), work.resolve("m.md"));
    copyDir(template.resolve("Document Fonts"), work.resolve("Document Fonts"));
    String koTitle = PdfMarkTitle.readDocumentTitle(work.resolve("job.designcraft"), new ObjectMapper());
    String markTitle = PdfMarkTitle.markTitleFor(code, koTitle);
    assertThat(koTitle).isNotBlank();
    assertThat(markTitle).isNotNull().matches("[\\x20-\\x7E]+");

    Path baseline = export(work, "baseline.pdf", null);
    Path fixed = export(work, "fixed.pdf", markTitle);

    // when
    PdfMarkTitle.restoreTitle(fixed, koTitle);

    // then 1: 메타데이터 제목(Info/XMP)은 원래 문서 제목
    try (PDDocument b = Loader.loadPDF(baseline.toFile()); PDDocument f = Loader.loadPDF(fixed.toFile())) {
      assertThat(f.getDocumentInformation().getTitle()).isEqualTo(koTitle).isEqualTo(b.getDocumentInformation().getTitle());
      String xmp = new String(f.getDocumentCatalog().getMetadata().toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      assertThat(xmp).contains(koTitle).doesNotContain(markTitle);
      assertThat(f.getNumberOfPages()).isEqualTo(b.getNumberOfPages());
      assertThat(f.getNumberOfPages()).isGreaterThan(1);

      // then 2: 페이지별로 본문은 동일, 바닥글은 ASCII 제목 + 같은 페이지 번호
      for (int page = 1; page <= f.getNumberOfPages(); page++) {
        List<String> baseBody = new ArrayList<>(), fixBody = new ArrayList<>();
        String baseFooter = split(text(b, page), baseBody);
        String fixFooter = split(text(f, page), fixBody);
        assertThat(fixBody).as("page %d body", page).isEqualTo(baseBody);
        assertThat(fixFooter).as("page %d footer exists", page).isNotNull();
        Matcher m = FOOTER.matcher(fixFooter);
        assertThat(m.matches()).as("footer format: " + fixFooter).isTrue();
        assertThat(m.group(1).trim()).isEqualTo(markTitle);
        assertThat(Integer.parseInt(m.group(2))).isEqualTo(page);
        assertThat(baseFooter).as("baseline also has page %d", page).contains("Page " + page + " ");
        assertThat(fixFooter).doesNotContain("\u0000").doesNotContain("�");
      }
    }
    String keep = System.getenv("DC_PDF_OUT");
    if (keep != null && !keep.isBlank()) {
      Files.createDirectories(Paths.get(keep));
      Files.copy(fixed, Paths.get(keep, code + ".pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    // then 3: 압축 없이 저장되어 기존 문자열 게이트 키워드가 유지된다
    String raw = new String(Files.readAllBytes(fixed), java.nio.charset.StandardCharsets.ISO_8859_1);
    String baseRaw = new String(Files.readAllBytes(baseline), java.nio.charset.StandardCharsets.ISO_8859_1);
    for (String kw : new String[] {"GTS_PDFX", "NotoSansKR", "/N 4"}) {
      assertThat(raw.contains(kw)).as(kw).isEqualTo(baseRaw.contains(kw));
    }
    assertThat(raw.contains("/DeviceRGB")).isEqualTo(baseRaw.contains("/DeviceRGB"));
  }

  private Path export(Path work, String out, String markTitle) throws Exception {
    String pdfOptions = markTitle == null
        ? "{\"standard\":\"x4\",\"tagged\":true,\"marks\":{\"crop\":true,\"bleed\":true,\"pageInfo\":true}}"
        : "{\"standard\":\"x4\",\"tagged\":true,\"title\":\"" + markTitle + "\",\"marks\":{\"crop\":true,\"bleed\":true,\"pageInfo\":true}}";
    Path pdf = work.resolve(out);
    Process p = new ProcessBuilder(System.getenv("DESIGNCRAFT_CLI"), "run", "--in", "job.designcraft",
        "--cmd", "file.place={\"path\":\"m.md\",\"autoflow\":true}", "--cmd", "edit.selectAll",
        "--cmd", "style.paragraph.apply={\"name\":\"ko-body\"}", "--pdf-options", pdfOptions, "--export", out)
        .directory(work.toFile()).redirectErrorStream(true).start();
    String log = new String(p.getInputStream().readAllBytes());
    assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(p.exitValue()).as(log).isZero();
    assertThat(pdf).exists();
    return pdf;
  }

  private static String text(PDDocument doc, int page) throws Exception {
    PDFTextStripper s = new PDFTextStripper();
    s.setSortByPosition(true);
    s.setStartPage(page);
    s.setEndPage(page);
    return s.getText(doc);
  }

  /** 줄 단위로 나눠 'Page N … UTC' 바닥글 줄은 반환하고 나머지 본문 줄은 body에 담는다. */
  private static String split(String text, List<String> body) {
    String footer = null;
    for (String line : text.split("\\R")) {
      if (line.matches(".* Page \\d+ .*UTC\\s*")) {
        footer = line;
      } else if (!line.isBlank()) {
        body.add(line.strip());
      }
    }
    return footer;
  }

  private static void copyDir(Path from, Path to) throws Exception {
    Files.createDirectories(to);
    try (var s = Files.walk(from)) {
      for (Path p : (Iterable<Path>) s.sorted(Comparator.naturalOrder())::iterator) {
        Path t = to.resolve(from.relativize(p).toString());
        if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t);
      }
    }
  }
}
