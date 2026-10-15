package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageLayout;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyDefaultLayoutFactory;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlock;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyPageLayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AcademyPageLayoutServiceTest {

  private AcademyPageLayoutRepository repository;
  private AcademyPageLayoutService service;
  private final JsonMapper mapper = JsonMapper.builder().build();

  private Academy academy;

  @BeforeEach
  void setUp() {
    repository = mock(AcademyPageLayoutRepository.class);
    service = new AcademyPageLayoutService(
        repository, new AcademyDefaultLayoutFactory(), mapper);
    academy = Academy.builder()
        .name("우리학원")
        .intro("소개글입니다.")
        .logoStorageKey("logo-key")
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 1L);
    when(repository.save(any(AcademyPageLayout.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void createLayout_persistsExactlyOneLayoutWhenNoneExists() {
    // given
    when(repository.existsByAcademy_AcademyId(1L)).thenReturn(false);
    AcademyPageLayoutSchema schema = new AcademyPageLayoutSchema(
        1, List.of(new AcademyLayoutBlock("hero", 0, Map.of("headline", "우리학원"))));

    // when
    AcademyPageLayout saved = service.createLayout(academy, schema);

    // then
    verify(repository, times(1)).save(any(AcademyPageLayout.class));
    assertThat(saved.getAcademy()).isSameAs(academy);
    assertThat(saved.getLayoutJson()).contains("hero");
  }

  @Test
  void createLayout_rejectsDuplicateWhenLayoutAlreadyExists() {
    // given
    when(repository.existsByAcademy_AcademyId(1L)).thenReturn(true);
    AcademyPageLayoutSchema schema = new AcademyPageLayoutSchema(
        1, List.of(new AcademyLayoutBlock("hero", 0, Map.of())));

    // when / then
    assertThatThrownBy(() -> service.createLayout(academy, schema))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미");
    verify(repository, never()).save(any(AcademyPageLayout.class));
  }

  @Test
  void createLayout_rejectsNonAllowlistedBlockType() {
    // given
    AcademyPageLayoutSchema schema = new AcademyPageLayoutSchema(
        1, List.of(new AcademyLayoutBlock("rawHtml", 0, Map.of("html", "<script>"))));

    // when / then
    assertThatThrownBy(() -> service.createLayout(academy, schema))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은");
    verify(repository, never()).save(any(AcademyPageLayout.class));
  }

  @Test
  void resolveLayoutSchema_returnsStoredLayoutWhenPresent() {
    // given
    AcademyPageLayoutSchema stored = new AcademyPageLayoutSchema(
        1, List.of(new AcademyLayoutBlock("quote", 0, Map.of("text", "명언"))));
    AcademyPageLayout layout = AcademyPageLayout.builder()
        .academy(academy)
        .layoutJson(mapper.writeValueAsString(stored))
        .build();
    when(repository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(layout));

    // when
    AcademyPageLayoutSchema resolved = service.resolveLayoutSchema(academy);

    // then
    assertThat(resolved.blocks()).hasSize(1);
    assertThat(resolved.blocks().get(0).type()).isEqualTo("quote");
    verify(repository, never()).save(any(AcademyPageLayout.class));
  }

  @Test
  void resolveLayoutSchema_returnsDefaultLayoutForLegacyAcademyWithoutLayout() {
    // given
    when(repository.findByAcademy_AcademyId(1L)).thenReturn(Optional.empty());

    // when
    AcademyPageLayoutSchema resolved = service.resolveLayoutSchema(academy);

    // then
    assertThat(resolved.blocks().stream().map(AcademyLayoutBlock::type).toList())
        .containsExactly("topNav", "hero", "quote", "teachers", "curriculum", "consultationCta", "blogPreview");
    AcademyLayoutBlock teachers = resolved.blocks().stream()
        .filter(b -> b.type().equals("teachers")).findFirst().orElseThrow();
    assertThat(teachers.config()).containsEntry("placeholder", true);
    verify(repository, never()).save(any(AcademyPageLayout.class));
  }

  @Test
  void saveLayout_createsNewLayoutWhenNoneExists() {
    // given
    when(repository.findByAcademy_AcademyId(1L)).thenReturn(Optional.empty());
    AcademyPageLayoutSchema schema = new AcademyPageLayoutSchema(
        1, List.of(new AcademyLayoutBlock("hero", 0, Map.of("headline", "새 학원"))));

    // when
    AcademyPageLayout saved = service.saveLayout(academy, schema);

    // then
    verify(repository, times(1)).save(any(AcademyPageLayout.class));
    assertThat(saved.getAcademy()).isSameAs(academy);
    assertThat(saved.getLayoutJson()).contains("새 학원");
  }

  @Test
  void saveLayout_overwritesExistingLayoutWithoutInsert() {
    // given
    AcademyPageLayout existing = AcademyPageLayout.builder()
        .academy(academy)
        .layoutVersion(1)
        .layoutJson(mapper.writeValueAsString(new AcademyPageLayoutSchema(
            1, List.of(new AcademyLayoutBlock("hero", 0, Map.of("headline", "옛 제목"))))))
        .build();
    when(repository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(existing));
    AcademyPageLayoutSchema updated = new AcademyPageLayoutSchema(
        2, List.of(new AcademyLayoutBlock("hero", 0, Map.of("headline", "새 제목"))));

    // when
    AcademyPageLayout result = service.saveLayout(academy, updated);

    // then
    verify(repository, never()).save(any(AcademyPageLayout.class)); // dirty checking, 새 insert 없음
    assertThat(result).isSameAs(existing);
    assertThat(result.getLayoutVersion()).isEqualTo(2);
    assertThat(result.getLayoutJson()).contains("새 제목").doesNotContain("옛 제목");
  }

  @Test
  void saveLayout_rejectsNonAllowlistedBlockType() {
    // given
    AcademyPageLayoutSchema schema = new AcademyPageLayoutSchema(
        1, List.of(new AcademyLayoutBlock("rawHtml", 0, Map.of("html", "<script>"))));

    // when / then
    assertThatThrownBy(() -> service.saveLayout(academy, schema))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("허용되지 않은");
    verify(repository, never()).findByAcademy_AcademyId(any());
    verify(repository, never()).save(any(AcademyPageLayout.class));
  }

  @Test
  void deleteLayout_removesStoredLayoutWhenPresent() {
    // given
    AcademyPageLayout existing = AcademyPageLayout.builder().academy(academy).layoutJson("{}").build();
    when(repository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(existing));

    // when
    service.deleteLayout(academy);

    // then
    verify(repository, times(1)).delete(existing);
  }

  @Test
  void deleteLayout_isNoOpWhenNoLayoutExists() {
    // given
    when(repository.findByAcademy_AcademyId(1L)).thenReturn(Optional.empty());

    // when
    service.deleteLayout(academy);

    // then
    verify(repository, never()).delete(any(AcademyPageLayout.class));
  }

  @Test
  void resolveLayoutSchema_throwsControlledErrorWhenStoredJsonIsCorrupt() {
    // given
    AcademyPageLayout layout = AcademyPageLayout.builder()
        .academy(academy)
        .layoutJson("{ broken json")
        .build();
    when(repository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(layout));

    // when / then
    assertThatThrownBy(() -> service.resolveLayoutSchema(academy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("해석할 수 없습니다");
  }
}
