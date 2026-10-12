package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProposalChangeValidator — 생성 시 입력 검증")
class ProposalChangeValidatorTest {

  private static void rejects(ProposalChange c) {
    assertThatThrownBy(() -> ProposalChangeValidator.validate(List.of(c)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 올바른 text·image·style when 검증 then 통과하고 정규화된 값을 돌려준다")
  void valid() {
    List<ProposalChange> out = ProposalChangeValidator.validate(List.of(
        new ProposalChange("ve5", "text", "새 제목", null, null),
        new ProposalChange("ve7", "image", "https://x.com/a.png", null, null),
        new ProposalChange("ve5", "style", null, Map.of("color", " red "), ""),
        new ProposalChange("ve5", "style", null, Map.of("font-size", "20px"), "(max-width: 768px)")));

    assertThat(out).hasSize(4);
    assertThat(out.get(2).styles()).containsEntry("color", "red");
    assertThat(out.get(2).mediaText()).isNull();
  }

  @Test
  @DisplayName("given 비었거나 51건 when 검증 then 거부")
  void sizes() {
    assertThatThrownBy(() -> ProposalChangeValidator.validate(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProposalChangeValidator.validate(null))
        .isInstanceOf(IllegalArgumentException.class);
    List<ProposalChange> many = new ArrayList<>();
    for (int i = 0; i < 51; i++) {
      many.add(new ProposalChange("a" + i, "text", "x", null, null));
    }
    assertThatThrownBy(() -> ProposalChangeValidator.validate(many))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("given 위험·잘못된 입력 when 검증 then 모두 거부")
  void rejectsBadInput() {
    rejects(new ProposalChange("1bad", "text", "x", null, null));
    rejects(new ProposalChange("a b", "text", "x", null, null));
    rejects(new ProposalChange("ve5", "script", "x", null, null));
    rejects(new ProposalChange("ve5", null, "x", null, null));
    rejects(new ProposalChange("ve5", "text", "<script>alert(1)</script>", null, null));
    rejects(new ProposalChange("ve5", "text", "  ", null, null));
    rejects(new ProposalChange("ve5", "text", "a\u0000b", null, null));
    rejects(new ProposalChange("ve5", "text", "x".repeat(2001), null, null));
    rejects(new ProposalChange("ve7", "image", "javascript:alert(1)", null, null));
    rejects(new ProposalChange("ve7", "image", "data:image/png;base64,AAAA", null, null));
    rejects(new ProposalChange("ve7", "image", "//evil.com/a.png", null, null));
    rejects(new ProposalChange("ve7", "image", "https://x.com/a b.png", null, null));
    rejects(new ProposalChange("ve5", "style", null, Map.of(), null));
    rejects(new ProposalChange("ve5", "style", null, Map.of("Color", "red"), null));
    rejects(new ProposalChange("ve5", "style", null, Map.of("color", "red;}body{display:none"), null));
    rejects(new ProposalChange("ve5", "style", null, Map.of("background", "url(http://evil/x.png)"), null));
    rejects(new ProposalChange("ve5", "style", null, Map.of("width", "expression(alert(1))"), null));
    rejects(new ProposalChange("ve5", "style", null, Map.of("color", "red"), "(max-width: 100px)"));
  }
}
