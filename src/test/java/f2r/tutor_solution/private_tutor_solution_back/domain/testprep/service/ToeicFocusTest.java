package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ToeicFocus — TOEIC 문항의 파트·유형 묶기")
class ToeicFocusTest {

  @Test
  @DisplayName("given 해설 맨 앞 꼬리표 when label then 괄호 안 세부 설명과 PartN- 머리를 뗀 유형 이름이 나온다")
  void label_readsLeadingTag() {
    // given & when & then
    assertThat(ToeicFocus.label("[문법-품사(부사 자리)] 정답 (A). 빈칸은…")).isEqualTo("문법-품사");
    assertThat(ToeicFocus.label("[Part6-문장삽입] 정답 (D).")).isEqualTo("문장삽입");
    assertThat(ToeicFocus.label("[Part2-의문사(Where)] 정답 (A).")).isEqualTo("의문사");
    assertThat(ToeicFocus.label("[세부 사항] 정답 (B).")).isEqualTo("세부 사항");
    assertThat(ToeicFocus.label("  [전치사(by와 until)] 정답")).isEqualTo("전치사");
  }

  @Test
  @DisplayName("given 꼬리표가 없는 해설 when label then null")
  void label_nullWithoutTag() {
    // given & when & then
    assertThat(ToeicFocus.label(null)).isNull();
    assertThat(ToeicFocus.label("정답 (A). [참고] 뒤쪽 대괄호는 꼬리표가 아니다")).isNull();
    assertThat(ToeicFocus.label("[] 정답")).isNull();
  }

  @Test
  @DisplayName("given 유형 이름 when area·display then 어휘-로 시작하면 어휘, 그 밖은 어법이고 화면 이름은 머리말을 뗀다")
  void areaAndDisplay() {
    // given & when & then
    assertThat(ToeicFocus.area("어휘-동사")).isEqualTo("어휘");
    assertThat(ToeicFocus.area("어휘")).isEqualTo("어휘");
    assertThat(ToeicFocus.area("문법-품사")).isEqualTo("어법");
    assertThat(ToeicFocus.area("접속사")).isEqualTo("어법");
    assertThat(ToeicFocus.area(null)).isEqualTo("어법");
    assertThat(ToeicFocus.display("문법-품사")).isEqualTo("품사");
    assertThat(ToeicFocus.display("어휘-동사")).isEqualTo("동사 어휘");
    assertThat(ToeicFocus.display("어휘")).isEqualTo("문맥 어휘");
    assertThat(ToeicFocus.display("문법")).isEqualTo("문맥 어법");
    assertThat(ToeicFocus.display("접속사")).isEqualTo("접속사");
    assertThat(ToeicFocus.display(null)).isEmpty();
  }

  @Test
  @DisplayName("given 파트 표 when PARTS then Part 1~7이 순서대로 있고 task_type은 TOEIC_P번호다")
  void parts_oneToSeven() {
    // given & when & then
    assertThat(ToeicFocus.PARTS).extracting(ToeicFocus.Part::number).containsExactly(1, 2, 3, 4, 5, 6, 7);
    assertThat(ToeicFocus.PARTS).allSatisfy(p -> assertThat(p.taskType()).isEqualTo("TOEIC_P" + p.number()));
    assertThat(ToeicFocus.PARTS.get(4).label()).isEqualTo("Part 5");
    assertThat(ToeicFocus.PARTS.get(1).area()).isEqualTo("듣기");
    assertThat(ToeicFocus.PARTS.get(6).area()).isEqualTo("읽기");
  }
}
