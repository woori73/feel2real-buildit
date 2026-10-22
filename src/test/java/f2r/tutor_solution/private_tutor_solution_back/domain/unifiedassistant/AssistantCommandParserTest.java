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
  @Test void givenAttendanceInquiry_whenParsed_thenReadOnlyQueryNotWriteDraft() {
    for(String s:new String[]{"학생 ID 5 출결 조회해줘","학생 ID 5 출석 확인해줘","학생 ID 5 결석 내역 알려줘"}) {
      var r=parser.parse(s);
      assertThat(r.operation()).isEqualTo("ATTENDANCE_QUERY");assertThat(r.input()).containsEntry("studentId",5L).doesNotContainKey("eventType");
    }
  }
  @Test void givenRecordNounWithInquiryVerb_whenParsed_thenReadOnlyQuery() {
    // given: "기록"이 명사로 쓰인 자연스러운 조회 문장
    for(String s:new String[]{"학생 ID 5 출결 기록 조회해줘","학생 ID 5 출결 기록 확인해줘","학생 ID 5 결석 기록 알려줘"}) {
      // when
      var r=parser.parse(s);
      // then
      assertThat(r.operation()).as(s).isEqualTo("ATTENDANCE_QUERY");
      assertThat(r.input()).doesNotContainKey("eventType");
    }
  }
  @Test void givenRecordVerb_whenParsed_thenStillWriteDraft() {
    assertThat(parser.parse("학생 ID 5 지각 기록해줘").operation()).isEqualTo("ATTENDANCE_RECORD");
    assertThat(parser.parse("학생 ID 5 결석으로 기록하고 싶어").operation()).isEqualTo("ATTENDANCE_RECORD");
  }
  @Test void givenAttendanceRecordWithConfirmWord_whenParsed_thenStillWriteDraft() {
    assertThat(parser.parse("학생 ID 5 결석 기록해줘, 사유 확인").operation()).isEqualTo("ATTENDANCE_RECORD");
  }
  @Test void givenAttendanceCorrection_whenParsed_thenBlockedWithGuidanceNotNewEvent() {
    var r=parser.parse("학생 ID 5 출결 정정해줘 결석으로");
    assertThat(r.operation()).isEmpty();assertThat(r.message()).isEqualTo(AssistantCommandParser.CORRECTION_BLOCKED);
  }
  @Test void givenNoticeRequest_whenParsed_thenReadOnlyNoticeDraftWithTitleAndContent() {
    var r=parser.parse("공지 작성해줘, 제목: 휴강 안내, 내용: 내일은 휴강합니다");
    assertThat(r.operation()).isEqualTo("NOTICE_DRAFT");assertThat(r.input()).containsEntry("title","휴강 안내").containsEntry("content","내일은 휴강합니다");
  }
  @Test void givenFinanceStatusQuestion_whenParsed_thenStillOverview() {
    assertThat(parser.parse("이번 달 지출 현황 알려줘").operation()).isEqualTo("OVERVIEW");
  }
}
