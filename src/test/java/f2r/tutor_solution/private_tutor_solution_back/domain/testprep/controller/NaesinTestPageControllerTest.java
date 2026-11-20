package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ExamListItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NaesinTestPageControllerTest {

  private static ExamListItem exam(String code, String title, String subtitle, long count) {
    return new ExamListItem(1L, code, "NAESIN", title, subtitle, null, null, 7200, count);
  }

  @Test
  @DisplayName("given 내신 회차 제목·부제, when toRow면 회차·세트명·묶음·범위·시간·만점을 나눈다")
  void toRowSplitsTitle() {
    // given
    ExamListItem e = exam("R11", "압구정중 2학년 2학기 기말 11회 · Lesson 7·8 종합 예상문제 1회",
        "Lesson 7·8 학습지 필기본 1~23쪽 · 70문항 · 35분 · 237점", 70);
    // when
    NaesinTestPageController.Row r = NaesinTestPageController.toRow(e);
    // then
    assertThat(r.round()).isEqualTo("11회");
    assertThat(r.setName()).isEqualTo("Lesson 7·8 종합 예상문제 1회");
    assertThat(r.group()).isEqualTo("Lesson 7·8 종합");
    assertThat(r.scope()).isEqualTo("Lesson 7·8 학습지 필기본 1~23쪽");
    assertThat(r.minutes()).isEqualTo(35);
    assertThat(r.maxPoints()).isEqualTo(237);
  }

  @Test
  @DisplayName("given 여러 회차, when group이면 같은 범위끼리 나온 순서대로 묶는다")
  void groupKeepsOrder() {
    // given
    List<ExamListItem> exams = List.of(
        exam("R01", "압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회", "Lesson 7 학습지 필기본 1~11쪽 · 40문항 · 20분 · 134점", 40),
        exam("R02", "압구정중 2학년 2학기 기말 2회 · Lesson 7 예상문제 2회", "Lesson 7 학습지 필기본 1~11쪽 · 40문항 · 20분 · 134점", 40),
        exam("R14", "압구정중 2학년 2학기 기말 14회 · Final 예상문제 1회", "Lesson 7·8 학습지 필기본 전 범위 · 40문항 · 20분 · 137점", 40));
    // when
    List<NaesinTestPageController.Group> groups = NaesinTestPageController.group(exams);
    // then
    assertThat(groups).extracting(NaesinTestPageController.Group::name).containsExactly("Lesson 7", "Final");
    assertThat(groups.get(0).rows()).hasSize(2);
    assertThat(groups.get(0).rows().get(0).minutes()).isEqualTo(20);
  }

  @Test
  @DisplayName("given 여러 학교 시험, when schoolRows면 학교·과정 4곳 순서대로 시험 코드 앞부분이 맞는 회차 수를 센다")
  void schoolRowsCountByPrefix() {
    // given
    List<ExamListItem> exams = List.of(
        exam("TP-NAESIN-APM2-2602-R01", "압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회", "s · 40문항 · 20분 · 134점", 40),
        exam("TP-NAESIN-APM2-2602-R02", "압구정중 2학년 2학기 기말 2회 · Lesson 7 예상문제 2회", "s · 40문항 · 20분 · 134점", 40),
        exam("TP-NAESIN-APM1-2602-R01", "압구정중 1학년 2학기 기말 1회 · Lesson 5 예상문제 1회", "s · 40문항 · 20분 · 130점", 40),
        exam("TP-NAESIN-CSH1-MOCK-R01", "고1 수능 영어 독해 모의고사 1회 · 18~45번 변형문제 1회", "s · 28문항 · 45분 · 62점", 28));
    // when
    List<NaesinTestPageController.SchoolRow> rows = NaesinTestPageController.schoolRows(exams);
    // then
    assertThat(rows).extracting(r -> r.school().slug()).containsExactly("apgujeong-m2", "singu-m2", "apgujeong-m1", "csat-h1");
    assertThat(rows).extracting(NaesinTestPageController.SchoolRow::rounds).containsExactly(2, 0, 1, 1);
  }

  @Test
  @DisplayName("given 고1 수능 독해 모의고사 회차, when toRow면 '모의고사 N회'를 회차로 나누고 시간은 45분으로 둔다")
  void toRowCsatMock() {
    // given
    ExamListItem e = exam("TP-NAESIN-CSH1-MOCK-R03", "고1 수능 영어 독해 모의고사 3회 · 18~45번 변형문제 3회",
        "2022년 5월 고1 이투스 원본 · 28문항 · 45분 · 62점", 28);
    // when
    NaesinTestPageController.Row r = NaesinTestPageController.toRow(e);
    // then
    assertThat(r.round()).isEqualTo("3회");
    assertThat(r.setName()).isEqualTo("18~45번 변형문제 3회");
    assertThat(r.group()).isEqualTo("18~45번");
    assertThat(r.scope()).isEqualTo("2022년 5월 고1 이투스 원본");
    assertThat(r.minutes()).isEqualTo(45);
    assertThat(r.maxPoints()).isEqualTo(62);
  }

  @Test
  @DisplayName("given 시험 코드와 문항 수, when minutes면 수능 독해는 45분, 내신은 문항당 30초로 계산한다")
  void minutesByExam() {
    // given
    String csat = "TP-NAESIN-CSH1-MOCK-R01";
    String naesin = "TP-NAESIN-SGM2-2602-R01";
    // when
    int csatMin = NaesinTestPageController.minutes(csat, 28);
    int naesinMin = NaesinTestPageController.minutes(naesin, 40);
    // then
    assertThat(csatMin).isEqualTo(45);
    assertThat(naesinMin).isEqualTo(20);
  }
}
