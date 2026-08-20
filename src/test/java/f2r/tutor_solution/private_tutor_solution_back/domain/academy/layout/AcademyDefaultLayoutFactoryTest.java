package f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AcademyDefaultLayoutFactoryTest {

  private final AcademyDefaultLayoutFactory factory = new AcademyDefaultLayoutFactory();

  private Academy academy() {
    return Academy.builder()
        .name("우리학원")
        .intro("소개글입니다.")
        .logoStorageKey("logo-key")
        .build();
  }

  @Test
  void generate_producesBlocksInFixedSemanticOrder() {
    // given
    Academy academy = academy();

    // when
    AcademyPageLayoutSchema schema = factory.generate(academy);

    // then
    List<String> types = schema.blocks().stream().map(AcademyLayoutBlock::type).toList();
    assertThat(types).containsExactly(
        "topNav", "hero", "quote", "teachers", "curriculum", "consultationCta", "blogPreview");
  }

  @Test
  void generate_teachersBlockContainsSafePlaceholder() {
    // given
    Academy academy = academy();

    // when
    AcademyPageLayoutSchema schema = factory.generate(academy);

    // then
    AcademyLayoutBlock teachers = blockOf(schema, "teachers");
    assertThat(teachers.config()).containsEntry("placeholder", true);
    assertThat(teachers.config().get("teachers")).isEqualTo(List.of());
  }

  @Test
  void generate_consultationCtaIsPreparingDisabledWithoutPaymentTarget() {
    // given
    Academy academy = academy();

    // when
    AcademyPageLayoutSchema schema = factory.generate(academy);

    // then
    AcademyLayoutBlock cta = blockOf(schema, "consultationCta");
    assertThat(cta.config()).containsEntry("state", "PREPARING");
    assertThat(cta.config()).containsEntry("enabled", false);
    assertThat(cta.config().keySet())
        .noneMatch(key -> key.toLowerCase().contains("payment")
            || key.toLowerCase().contains("crm")
            || key.toLowerCase().contains("action"));
  }

  private AcademyLayoutBlock blockOf(AcademyPageLayoutSchema schema, String type) {
    return schema.blocks().stream()
        .filter(block -> block.type().equals(type))
        .findFirst()
        .orElseThrow();
  }
}
