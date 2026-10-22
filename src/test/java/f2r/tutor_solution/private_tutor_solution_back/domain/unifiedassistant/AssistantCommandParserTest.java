package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AssistantCommandParserTest {
  private final AssistantCommandParser parser=new AssistantCommandParser();
  @Test void givenStudentRegistration_whenParsed_thenEditableFieldsOnly() {
    var r=parser.parse("학생 김하늘 등록해줘, 학교: 새봄중, 학년: 중2");
    assertThat(r.operation()).isEqualTo("STUDENT_CREATE");
    assertThat(r.input()).containsEntry("name","김하늘").containsEntry("schoolName","새봄중").containsEntry("grade","중2");
  }
  @Test void givenAttendance_whenParsed_thenExplicitStudentAndEvent() {
    var r=parser.parse("학생 ID 12 결석 기록해줘");
    assertThat(r.operation()).isEqualTo("ATTENDANCE_RECORD");assertThat(r.input()).containsEntry("studentId",12L).containsEntry("eventType","ABSENT");
  }
  @Test void givenFinanceInstruction_whenParsed_thenDateAmountAndDirectionNeedPreview() {
    var r=parser.parse("2099-10-14 전화요금 350,000원 지출 등록해줘");
    assertThat(r.operation()).isEqualTo("LEDGER_ENTRY");assertThat(r.input()).containsEntry("amount",350000L).containsEntry("occurredOn","2099-10-14").containsEntry("direction","EXPENSE");
  }
  @Test void givenApprovalOrDeletion_whenParsed_thenNoOperation() {
    for(String s:new String[]{"승인해줘","학생 삭제해줘","지금 실행해"})assertThat(parser.parse(s).operation()).isEmpty();
  }
  @Test void givenMissingFieldsOrAmbiguousCommand_whenParsed_thenNoGuessedIdentifiers() {
    assertThat(parser.parse("학생 결석 처리해줘").input()).doesNotContainKey("studentId");
    assertThat(parser.parse("아무거나 해줘").operation()).isEmpty();
  }
  @Test void givenOversizedOrBlankCommand_whenParsed_thenRejected() {
    assertThatThrownBy(()->parser.parse(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->parser.parse("가".repeat(2001))).isInstanceOf(IllegalArgumentException.class);
  }
}
