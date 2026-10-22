package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.*;
import org.junit.jupiter.api.Test;
class AssistantMaterialAnalyzerTest {
  final AssistantMaterialAnalyzer analyzer=new AssistantMaterialAnalyzer(new AssistantIntentAnalyzer(new AssistantCommandParser(),org.mockito.Mockito.mock(f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport.class)));
  byte[] utf8(String s) {return s.getBytes(StandardCharsets.UTF_8);}
  @Test void givenQuotedStudentCsv_whenAnalyzed_thenPreservesNamesPhoneAndReportsInvalidRows() {
    var r=analyzer.analyze("명단.csv",utf8("이름,학교,학부모연락처\n\"김,하늘\",새봄중,010-0000-0000\n,학교,\n열부족\n"));
    assertThat(r.kind()).isEqualTo("학생 명단");assertThat(r.rows()).isEqualTo(3);assertThat(r.items()).hasSize(1);assertThat(r.issues()).hasSize(2);
    assertThat(r.items().getFirst().suggestion().input()).containsEntry("name","김,하늘").containsEntry("parentPhone","010-0000-0000");
  }
  @Test void givenCp949OrBom_whenParsed_thenDecodesKorean() {
    assertThat(analyzer.analyze("명단.csv","이름,학년\n하늘,중2".getBytes(Charset.forName("MS949"))).items()).hasSize(1);
    assertThat(analyzer.analyze("명단.csv",utf8("\uFEFF이름\n하늘")).items()).hasSize(1);
  }
  @Test void givenTransactionCsv_whenAnalyzed_thenRoutesToExistingImporter() {
    assertThat(analyzer.analyze("거래.csv",utf8("거래일,금액,구분\n2026-10-22,1000,지출")).kind()).isEqualTo("LEDGER_IMPORT");
  }
  @Test void givenTextInstructions_whenAnalyzed_thenApprovalAndMissingTargetsRemainUnexecutable() {
    var r=analyzer.analyze("업무.txt",utf8("학생 하늘 등록해줘\n승인하고 실행해\n학생 결석 기록"));
    assertThat(r.items()).hasSize(3);
    assertThat(AssistantConversationService.complete(r.items().get(0).suggestion())).isTrue();
    assertThat(AssistantConversationService.complete(r.items().get(1).suggestion())).isFalse();
    assertThat(AssistantConversationService.complete(r.items().get(2).suggestion())).isFalse();
  }
  @Test void givenDuplicateHeadersOrOversizedRosterOrUnsupportedFile_whenAnalyzed_thenRejected() {
    assertThatThrownBy(()->analyzer.analyze("x.csv",utf8("이름,name\n하늘,다른이름"))).hasMessageContaining("중복");
    assertThatThrownBy(()->analyzer.analyze("x.csv",utf8("이름\n"+"하늘\n".repeat(21)))).hasMessageContaining("20행");
    assertThatThrownBy(()->analyzer.analyze("x.pdf",utf8("실제 PDF 아님"))).hasMessageContaining("지원하지");
    assertThatThrownBy(()->analyzer.analyze("x.txt",new byte[262145])).hasMessageContaining("256KB");
  }
}
