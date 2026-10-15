package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantAssetCatalog.Asset;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantAssetCatalog.Hit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Applied;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Op;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Plan;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Snapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssistantAssetCatalog — BuildIt2 디자인 자산 카탈로그·검색·추천")
class AssistantAssetCatalogTest {

  private static final Set<String> BUILDIT2_IDS = Set.of(
      "tmpl-open-school", "tmpl-demo-school", "tmpl-minimal-landing", "comp-hero-hyperui", "comp-team-tailblocks",
      "comp-testimonials-hyperui", "comp-nav-footer-hyperui", "anim-slide-in-bottom", "anim-gsap-scroll-fade",
      "anim-gsap-counter");

  private static Snapshot empty() {
    return new Snapshot("", Map.of(), null, null);
  }

  @Test
  @DisplayName("given 카탈로그 when 읽으면 then BuildIt2 자산 10종 전부가 있고, 연결 안 된 자산은 이유가 적혀 있다")
  void coversAllTenAssets() {
    Set<String> ids = new HashSet<>();
    AssistantAssetCatalog.all().forEach(a -> ids.add(a.id()));
    assertThat(ids).isEqualTo(BUILDIT2_IDS);
    for (Asset a : AssistantAssetCatalog.all()) {
      if (!a.active()) {
        assertThat(a.status()).isEqualTo("NOT_CONNECTED");
        assertThat(a.note()).as(a.id()).isNotBlank();
        assertThat(a.ops()).as(a.id()).isEmpty();
      }
    }
    assertThat(AssistantAssetCatalog.active()).hasSize(6);
  }

  @Test
  @DisplayName("given ACTIVE 자산 when 연산을 검증하면 then 전부 허용 목록을 통과한다(자산이 규칙 밖 값을 만들 수 없다)")
  void activeAssetsPassValidation() {
    for (Asset a : AssistantAssetCatalog.active()) {
      Plan p = AssistantPatchApplier.plan(List.of(Op.section("applyAsset", a.id())), empty(), "");
      assertThat(p.skipped()).as(a.id()).isEmpty();
      assertThat(p.accepted()).as(a.id()).isNotEmpty();
    }
  }

  @Test
  @DisplayName("given 레시피 when 빈 작업본에 적용하면 then 디자인 값만 바뀌고 섹션 표시·순서는 건드리지 않는다")
  void recipesTouchOnlyDraft() {
    for (Asset a : AssistantAssetCatalog.active()) {
      Plan p = AssistantPatchApplier.plan(List.of(Op.section("applyAsset", a.id())), empty(), "");
      Applied applied = AssistantPatchApplier.apply(empty(), p.accepted());
      assertThat(applied.visibility()).as(a.id()).isNull();
      assertThat(applied.order()).as(a.id()).isNull();
      assertThat(applied.intro()).as(a.id()).isNull();
    }
  }

  @Test
  @DisplayName("given 학교형 요청 when 검색하면 then 학교형 레시피가 1순위")
  void searchByKeyword() {
    List<Hit> hits = AssistantAssetCatalog.search("학교형으로 신뢰감 있게 만들어줘", "새봄학원", null);
    assertThat(hits.get(0).asset().id()).isEqualTo("tmpl-open-school");
    assertThat(AssistantAssetCatalog.search("스크롤할 때 자연스럽게 등장하게 해줘", "x", null).get(0).asset().id())
        .isEqualTo("anim-gsap-scroll-fade");
    assertThat(AssistantAssetCatalog.search("카드들이 아래에서 차례대로 올라오게 해줘", "x", null).get(0).asset().id())
        .isEqualTo("anim-slide-in-bottom");
  }

  @Test
  @DisplayName("given NOT_CONNECTED 자산 when 검색·추천하면 then 절대 나오지 않는다")
  void neverReturnsNotConnected() {
    for (String text : List.of("강사진 카드 후기 합격 숫자 카운트 내비 푸터", "합격 후기 보여줘", "강사 소개를 카드로")) {
      assertThat(AssistantAssetCatalog.search(text, "x", null)).allSatisfy(h -> assertThat(h.asset().active()).isTrue());
    }
    assertThat(AssistantAssetCatalog.rankedActive("강사진 후기", "x")).allSatisfy(a -> assertThat(a.active()).isTrue());
  }

  @Test
  @DisplayName("given 같은 입력 when 여러 번 검색하면 then 항상 같은 결과이고, 동점은 학원 이름에 따라 갈린다")
  void deterministicWithSeedTieBreak() {
    String q = "소규모 학원";
    assertThat(AssistantAssetCatalog.search(q, "별빛학원", null)).isEqualTo(AssistantAssetCatalog.search(q, "별빛학원", null));
    Set<String> firsts = new HashSet<>();
    for (String name : List.of("별빛학원", "새봄학원", "한울학원", "이음학원", "다온학원", "라온학원", "해든학원", "나래학원")) {
      List<Hit> hits = AssistantAssetCatalog.search("영어 수학", name, "recipe");
      if (!hits.isEmpty()) firsts.add(hits.get(0).asset().id());
    }
    assertThat(firsts.size()).as("동점이면 학원마다 다른 레시피가 1순위가 된다").isGreaterThan(1);
  }

  @Test
  @DisplayName("given 분위기 단어가 분명한 요청 when 첫 생성 레시피를 고르면 then 그 레시피")
  void recommendsByMood() {
    Asset a = AssistantAssetCatalog.recommendRecipe("미니멀하고 깔끔한 소수정예 영어회화", "새봄학원", "ENGLISH", false);
    assertThat(a.id()).isEqualTo("tmpl-minimal-landing");
  }

  @Test
  @DisplayName("given 샘플로 프리셋이 정해졌고 분위기 레시피가 그 프리셋과 다르면 when 고르면 then 프리셋을 뒤집지 않는다")
  void doesNotFlipLockedPreset() {
    Asset a = AssistantAssetCatalog.recommendRecipe("미니멀하고 깔끔한 학원", "새봄학원", "EXAM", true);
    assertThat(a).isNotNull();
    assertThat(a.preset()).isEqualTo("EXAM");
  }

  @Test
  @DisplayName("given 분위기 단서가 없는 요청 when 고르면 then 이미 정해진 프리셋과 같은 레시피만, 맞는 게 없으면 null")
  void fallsBackToSamePresetOrNull() {
    assertThat(AssistantAssetCatalog.recommendRecipe("우리 학원 홈페이지", "새봄학원", "ENGLISH", false).preset()).isEqualTo("ENGLISH");
    assertThat(AssistantAssetCatalog.recommendRecipe("우리 학원 홈페이지", "새봄학원", "DARK", false)).isNull();
  }

  @Test
  @DisplayName("given 연결 안 된 자산·모르는 id when applyAsset 하면 then 건너뛰고 사용자에게 이유를 안내한다")
  void applyNotConnectedOrUnknownIsSkipped() {
    Plan p = AssistantPatchApplier.plan(
        List.of(Op.section("applyAsset", "comp-team-tailblocks"), Op.section("applyAsset", "evil-asset")), empty(), "");
    assertThat(p.accepted()).isEmpty();
    assertThat(p.skipped()).hasSize(2);
    assertThat(p.skipped().get(0)).contains("강사").contains("지어내지");
    assertThat(p.skipped().get(1)).contains("등록되지 않은");
  }

  @Test
  @DisplayName("given applyAsset 뒤에 사용자가 말한 색 when 검증하면 then 개별 지정이 자산 값보다 우선한다")
  void explicitOpWinsOverAsset() {
    Plan p = AssistantPatchApplier.plan(
        List.of(Op.of("setColor", "colorPrimary", "#ff0000"), Op.section("applyAsset", "tmpl-open-school")), empty(), "");
    Applied applied = AssistantPatchApplier.apply(empty(), p.accepted());
    assertThat(applied.draftPatch()).containsEntry("colorPrimary", "#ff0000")
        .containsEntry("designAsset", "tmpl-open-school").containsEntry("motionStyle", "slide");
  }
}
