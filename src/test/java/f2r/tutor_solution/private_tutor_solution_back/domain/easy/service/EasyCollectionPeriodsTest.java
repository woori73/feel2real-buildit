package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EasyCollectionPeriodsTest {

  @Test
  @DisplayName("given 70일 기간 when 31일 단위로 나눔 then 겹침·빈틈 없는 3구간")
  void splitIntoContiguousWindows() {
    // given
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = from.plusDays(69);
    // when
    var windows = EasyCollectionPeriods.split(from, to, 31);
    // then
    assertThat(windows).hasSize(3);
    assertThat(windows.get(0)[0]).isEqualTo(from);
    assertThat(windows.get(0)[1]).isEqualTo(from.plusDays(30));
    assertThat(windows.get(1)[0]).isEqualTo(from.plusDays(31));
    assertThat(windows.get(2)[1]).isEqualTo(to);
  }

  @Test
  @DisplayName("given 시작일이 종료일보다 늦음 when 나눔 then 종료일 하루 구간")
  void startAfterEnd() {
    LocalDate day = LocalDate.of(2026, 11, 10);
    var windows = EasyCollectionPeriods.split(day.plusDays(3), day, 31);
    assertThat(windows).hasSize(1);
    assertThat(windows.get(0)[0]).isEqualTo(day);
  }

  @Test
  @DisplayName("given 4일 구간 when 반으로 나눔 then 앞 2일·뒤 2일, 하루 구간은 나누지 않는다")
  void halve() {
    LocalDate from = LocalDate.of(2026, 11, 1);
    var halves = EasyCollectionPeriods.halve(from, from.plusDays(3));
    assertThat(halves.get(0)[1]).isEqualTo(from.plusDays(1));
    assertThat(halves.get(1)[0]).isEqualTo(from.plusDays(2));
    assertThatThrownBy(() -> EasyCollectionPeriods.halve(from, from))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 원문 JSON when 표식 필드 읽기 then 값 또는 null")
  void rawJsonField() {
    assertThat(EasyCollectionRawJson.text("{\"original_external_id\":\"APP-1\"}",
        "original_external_id")).isEqualTo("APP-1");
    assertThat(EasyCollectionRawJson.text("not json", "x")).isNull();
    assertThat(EasyCollectionRawJson.text("{\"x\":\"  \"}", "x")).isNull();
  }
}
