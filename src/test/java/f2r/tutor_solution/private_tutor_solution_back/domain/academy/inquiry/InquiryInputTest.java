package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InquiryInput — 상담신청 입력 검증")
class InquiryInputTest {

  @Test
  @DisplayName("given 정상 입력 when 검증하면 then 연락처는 숫자만 남기고 이름·내용은 다듬어진다")
  void normalizesValidInput() {
    // given
    String name = "  김  학부모 ";
    String phone = "010-1234-5678";
    String message = "중2 영어 상담\r\n받고 싶어요 ";
    // when
    InquiryInput.Clean c = InquiryInput.validate(name, phone, message, true);
    // then
    assertThat(c.name()).isEqualTo("김 학부모");
    assertThat(c.phone()).isEqualTo("01012345678");
    assertThat(c.message()).isEqualTo("중2 영어 상담\n받고 싶어요");
  }

  @Test
  @DisplayName("given 동의하지 않음 when 검증하면 then 거절한다")
  void requiresConsent() {
    // given / when / then
    assertThatThrownBy(() -> InquiryInput.validate("김", "01012345678", "문의", false)).isInstanceOf(InquiryInput.Invalid.class);
    assertThatThrownBy(() -> InquiryInput.validate("김", "01012345678", "문의", null)).isInstanceOf(InquiryInput.Invalid.class);
  }

  @Test
  @DisplayName("given 잘못된 연락처 when 검증하면 then 거절하고 오류 메시지에 입력값을 싣지 않는다")
  void rejectsBadPhoneWithoutEchoing() {
    // given
    String[] bad = {"", "abc", "123", "0101234567890123456", "010-12<script>"};
    for (String p : bad) {
      // when / then
      assertThatThrownBy(() -> InquiryInput.validate("김", p, "문의", true))
          .isInstanceOf(InquiryInput.Invalid.class)
          .satisfies(e -> assertThat(e.getMessage()).doesNotContain(p.isEmpty() ? "\u0000" : p));
    }
  }

  @Test
  @DisplayName("given 빈 이름·긴 이름·꺾쇠 이름 when 검증하면 then 거절한다")
  void rejectsBadName() {
    // given / when / then
    assertThatThrownBy(() -> InquiryInput.validate(" ", "01012345678", "문의", true)).isInstanceOf(InquiryInput.Invalid.class);
    assertThatThrownBy(() -> InquiryInput.validate("a".repeat(51), "01012345678", "문의", true)).isInstanceOf(InquiryInput.Invalid.class);
    assertThatThrownBy(() -> InquiryInput.validate("<b>김</b>", "01012345678", "문의", true)).isInstanceOf(InquiryInput.Invalid.class);
  }

  @Test
  @DisplayName("given 빈 내용·1001자·링크 2개 이상 when 검증하면 then 거절한다")
  void rejectsBadMessage() {
    // given / when / then
    assertThatThrownBy(() -> InquiryInput.validate("김", "01012345678", "  ", true)).isInstanceOf(InquiryInput.Invalid.class);
    assertThatThrownBy(() -> InquiryInput.validate("김", "01012345678", "a".repeat(1001), true)).isInstanceOf(InquiryInput.Invalid.class);
    assertThatThrownBy(() -> InquiryInput.validate("김", "01012345678", "http://a.example http://b.example", true))
        .isInstanceOf(InquiryInput.Invalid.class);
    assertThat(InquiryInput.validate("김", "01012345678", "http://a.example 참고", true).message()).contains("참고");
  }

  @Test
  @DisplayName("given 국제번호 when 정규화하면 then 앞 + 를 유지한다")
  void keepsLeadingPlus() {
    // given / when / then
    assertThat(InquiryInput.normalizePhone("+82 10-1234-5678")).isEqualTo("+821012345678");
  }
}
