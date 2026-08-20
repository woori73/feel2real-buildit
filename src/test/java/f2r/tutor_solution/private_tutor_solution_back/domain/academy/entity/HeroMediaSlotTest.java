package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("히어로 이미지 슬롯")
class HeroMediaSlotTest {

  @Test
  void from_허용된_슬롯_키를_변환한다() {
    // given: URL path variable로 들어오는 슬롯 문자열
    // when & then
    assertThat(HeroMediaSlot.from("principal")).isEqualTo(HeroMediaSlot.PRINCIPAL);
    assertThat(HeroMediaSlot.from("textbook1")).isEqualTo(HeroMediaSlot.TEXTBOOK1);
    assertThat(HeroMediaSlot.from("textbook3")).isEqualTo(HeroMediaSlot.TEXTBOOK3);
    assertThat(HeroMediaSlot.from("schedule")).isEqualTo(HeroMediaSlot.SCHEDULE);
  }

  @Test
  void from_허용되지_않은_값은_거부한다() {
    // given: 오타이거나 존재하지 않는 슬롯
    // when & then: 문자열을 그대로 쓰면 오타가 런타임까지 흘러가므로 여기서 닫는다
    assertThatThrownBy(() -> HeroMediaSlot.from("textbook4"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("올바르지 않은");
    assertThatThrownBy(() -> HeroMediaSlot.from("PRINCIPAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HeroMediaSlot.from(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void textbookSlot_교재_표지는_1부터_3_나머지는_0() {
    // given & when & then: Academy.updateTextbookCover(slot)에 넘길 인덱스.
    // 교재가 아닌 슬롯은 0이어야 한다 — 키에서 숫자를 파싱하던 구현은 schedule에서 터졌다.
    assertThat(HeroMediaSlot.PRINCIPAL.textbookSlot()).isZero();
    assertThat(HeroMediaSlot.SCHEDULE.textbookSlot()).isZero();
    assertThat(HeroMediaSlot.TEXTBOOK1.textbookSlot()).isEqualTo(1);
    assertThat(HeroMediaSlot.TEXTBOOK2.textbookSlot()).isEqualTo(2);
    assertThat(HeroMediaSlot.TEXTBOOK3.textbookSlot()).isEqualTo(3);
  }

  @Test
  void filenameHint_슬롯이_저장_파일명에_드러난다() {
    // given & when & then: storage에서 어떤 슬롯인지 눈으로 구분되어야 디버깅이 쉽다
    assertThat(HeroMediaSlot.PRINCIPAL.filenameHint()).isEqualTo("academy-hero-principal");
    assertThat(HeroMediaSlot.TEXTBOOK2.filenameHint()).isEqualTo("academy-hero-textbook2");
    assertThat(HeroMediaSlot.SCHEDULE.filenameHint()).isEqualTo("academy-hero-schedule");
  }

  @Test
  void key_프론트가_쓰는_슬롯_문자열과_일치한다() {
    // given & when & then: academy_sections.js의 SCHEDULE_MEDIA_SLOT과 같은 값이어야
    // 업로드 URL(/api/tutor/academy/media/hero/{slot})이 400으로 떨어지지 않는다
    assertThat(HeroMediaSlot.SCHEDULE.key()).isEqualTo("schedule");
  }
}
