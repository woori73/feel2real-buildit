package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.*;
import org.junit.jupiter.api.Test;
class AssistantMaterialAnalyzerTest {
  final AssistantMaterialAnalyzer analyzer=new AssistantMaterialAnalyzer(new AssistantIntentAnalyzer(new AssistantCommandParser(),org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport.class),new AssistantModelBudget(100,100,100,System::currentTimeMillis)));
  byte[] utf8(String s) {return s.getBytes(StandardCharsets.UTF_8);}
  @Test void givenQuotedStudentCsv_whenAnalyzed_thenPreservesNamesPhoneAndReportsInvalidRows() {
    var r=analyzer.analyze(1L,"명단.csv",utf8("이름,학교,학부모연락처\n\"김,하늘\",새봄중,010-0000-0000\n,학교,\n열부족\n"));
    assertThat(r.kind()).isEqualTo("학생 명단");assertThat(r.rows()).isEqualTo(3);assertThat(r.items()).hasSize(1);assertThat(r.issues()).hasSize(2);
    assertThat(r.items().getFirst().suggestion().input()).containsEntry("name","김,하늘").containsEntry("parentPhone","010-0000-0000");
  }
  @Test void givenCp949OrBom_whenParsed_thenDecodesKorean() {
    assertThat(analyzer.analyze(1L,"명단.csv","이름,학년\n하늘,중2".getBytes(Charset.forName("MS949"))).items()).hasSize(1);
    assertThat(analyzer.analyze(1L,"명단.csv",utf8("\uFEFF이름\n하늘")).items()).hasSize(1);
  }
  @Test void givenTransactionCsv_whenAnalyzed_thenRoutesToExistingImporter() {
    assertThat(analyzer.analyze(1L,"거래.csv",utf8("거래일,금액,구분\n2026-10-22,1000,지출")).kind()).isEqualTo("LEDGER_IMPORT");
  }
  @Test void givenTextInstructions_whenAnalyzed_thenApprovalAndMissingTargetsRemainUnexecutable() {
    var r=analyzer.analyze(1L,"업무.txt",utf8("학생 하늘 등록해줘\n승인하고 실행해\n학생 결석 기록"));
    assertThat(r.items()).hasSize(3);
    assertThat(AssistantConversationService.complete(r.items().get(0).suggestion())).isTrue();
    assertThat(AssistantConversationService.complete(r.items().get(1).suggestion())).isFalse();
    assertThat(AssistantConversationService.complete(r.items().get(2).suggestion())).isFalse();
  }
  @Test void givenDuplicateHeadersOrOversizedRosterOrUnsupportedFile_whenAnalyzed_thenRejected() {
    assertThatThrownBy(()->analyzer.analyze(1L,"x.csv",utf8("이름,name\n하늘,다른이름"))).hasMessageContaining("중복");
    assertThatThrownBy(()->analyzer.analyze(1L,"x.csv",utf8("이름\n"+"하늘\n".repeat(21)))).hasMessageContaining("20행");
    assertThatThrownBy(()->analyzer.analyze(1L,"x.zip",utf8("미지원 자료"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> analyzer.analyze(1L, "receipt.PDF", utf8("not-a-pdf")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PDF 파일 형식");
    assertThatThrownBy(()->analyzer.analyze(1L,"x.txt",new byte[262145])).hasMessageContaining("256KB");
  }
  @Test void givenLongHtml_whenRead_thenPreviewIsBoundedAndFileCapIsDistinctFromCsv() {
    String text = "안내".repeat(800);
    var result = analyzer.analyze(1L,"여러.문단.HTM",utf8("<p>"+text+"</p><!--"+" ".repeat(262144)+"-->"));
    assertThat(result.items()).isEmpty();
    assertThat(result.document().guide()).isNull();
    assertThat(result.document().preview().text()).hasSize(1200);
    assertThat(result.document().preview().truncated()).isTrue();
    assertThat(result.document().preview().format()).isEqualTo("HTML");
    for (String name : java.util.List.of("x.html","x.HTM","x.pdf")) {
      assertThatThrownBy(() -> analyzer.analyze(1L,name,new byte[5*1024*1024+1])).hasMessageContaining("5MB");
    }
  }

  @Test void givenUnreadablePdfOrEmptyHtml_whenRead_thenHonestStatusWithoutPreview() throws Exception {
    var out=new java.io.ByteArrayOutputStream();
    try (var pdf=new org.apache.pdfbox.pdmodel.PDDocument()) {
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(out);
    }
    assertThat(analyzer.analyze(1L, "damaged.pdf", utf8("%PDF-1.7\n")).document().preview().status())
        .isEqualTo("FAILED");
    var scan=analyzer.analyze(1L,"scan.pdf",out.toByteArray()).document();
    assertThat(scan.preview().status()).isEqualTo("NEEDS_OCR");
    assertThat(scan.preview().pageCount()).isEqualTo(1);
    assertThat(scan.preview().text()).isEmpty();
    assertThat(scan.message()).contains("OCR");
    assertThat(analyzer.analyze(1L,"empty.html",utf8("<script>학생 등록</script>")).document().preview().status()).isEqualTo("EMPTY");
  }
  @Test void givenDocumentRenamedAsInstructions_whenUploaded_thenNeverUsesIntentOrModel() {
    var unusedIntents=org.mockito.Mockito.mock(AssistantIntentAnalyzer.class);
    var reader=new AssistantMaterialAnalyzer(unusedIntents);
    for (String text : java.util.List.of("<!DOCTYPE HTML><p>학생 등록</p>", "%PDF-1.7")) {
      assertThatThrownBy(() -> reader.analyze(1L,"원본.pdf.txt",utf8(text))).hasMessageContaining("원래 확장자");
    }
    org.mockito.Mockito.verifyNoInteractions(unusedIntents);
  }
}
