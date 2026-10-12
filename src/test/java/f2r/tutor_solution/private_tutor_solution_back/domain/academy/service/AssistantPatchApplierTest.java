package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Applied;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Op;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Plan;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Snapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssistantPatchApplier — 수정 patch 검증·적용 계산")
class AssistantPatchApplierTest {

  private static Snapshot snap(Map<String, String> draft) {
    return new Snapshot("학원 소개 문단", draft, Map.of(), null);
  }

  private static Plan plan(Snapshot s, String user, Op... ops) {
    return AssistantPatchApplier.plan(List.of(ops), s, user);
  }

  @Test
  @DisplayName("given 허용 목록 밖의 프리셋·색·글꼴·레이아웃 when 검증하면 then 모두 거부하고 이유를 남긴다")
  void rejectsUnknownValues() {
    Plan p = plan(snap(Map.of()), "",
        Op.of("setPreset", null, "NEON"), Op.of("setColor", "colorPrimary", "blue"),
        Op.of("setColor", "colorEvil", "#fff"), Op.of("setTypography", null, "COMIC"),
        Op.of("setFont", "headingFont", "Comic Sans"), Op.of("setHeroLayout", null, "spiral"),
        Op.of("setCardStyle", null, "glow"), Op.of("setText", "adminSecret", "x"),
        Op.section("hideSection", "sec-hero"), Op.of("deleteAll", null, null));
    assertThat(p.accepted()).isEmpty();
    assertThat(p.skipped()).hasSize(10);
  }

  @Test
  @DisplayName("given 정상 값 when 검증하면 then 정규화(대문자 프리셋·소문자 hex)해 통과")
  void acceptsAndNormalizes() {
    Plan p = plan(snap(Map.of()), "", Op.of("setPreset", null, "dark"), Op.of("setColor", "colorPrimary", "#0F4C81"),
        Op.of("setHeroLayout", null, "CENTER"));
    assertThat(p.skipped()).isEmpty();
    assertThat(p.accepted()).extracting(Op::value).containsExactly("DARK", "#0f4c81", "center");
  }

  @Test
  @DisplayName("given 같은 대상을 여러 번 when 검증하면 then 마지막만 남고 프리셋 교체가 먼저 적용된다")
  void dedupesAndOrdersPresetFirst() {
    Plan p = plan(snap(Map.of()), "", Op.of("setColor", "colorPrimary", "#111111"),
        Op.of("setColor", "colorPrimary", "#222222"), Op.of("setPreset", null, "WARM"));
    assertThat(p.accepted()).extracting(Op::op).containsExactly("setPreset", "setColor");
    assertThat(p.accepted().get(1).value()).isEqualTo("#222222");
  }

  @Test
  @DisplayName("given 사용자가 말하지 않은 전화번호·금액·URL·숫자 when 문구 검증하면 then 거부, 사용자가 말했으면 허용")
  void factGuard() {
    Snapshot s = snap(Map.of());
    assertThat(plan(s, "문구 바꿔줘", Op.of("setText", "introMotto", "문의 010-1234-5678")).accepted()).isEmpty();
    assertThat(plan(s, "문구 바꿔줘", Op.of("setText", "introMotto", "월 30만원부터")).accepted()).isEmpty();
    assertThat(plan(s, "문구 바꿔줘", Op.of("setText", "introMotto", "자세한 내용은 https://x.kr")).accepted()).isEmpty();
    assertThat(plan(s, "문구 바꿔줘", Op.of("setText", "introMotto", "서울대 합격 120명")).accepted()).isEmpty();
    assertThat(plan(s, "합격 120명이라고 써줘", Op.of("setText", "introMotto", "합격 120명")).accepted()).hasSize(1);
    assertThat(plan(s, "친근하게", Op.of("setText", "introMotto", "아이의 속도에 맞춰 함께 걷는 학원")).accepted()).hasSize(1);
  }

  @Test
  @DisplayName("given 스크립트·너무 긴·빈 문구 when 검증하면 then 거부")
  void textSafetyAndLimits() {
    Snapshot s = snap(Map.of());
    assertThat(plan(s, "", Op.of("setText", "introHeading", "<script>alert(1)</script>")).accepted()).isEmpty();
    assertThat(plan(s, "", Op.of("setText", "introHeading", "가".repeat(121))).accepted()).isEmpty();
    assertThat(plan(s, "", Op.of("setText", "introMotto", "가".repeat(601))).accepted()).isEmpty();
    assertThat(plan(s, "", Op.of("setText", "introHeading", "   ")).accepted()).isEmpty();
    assertThat(plan(s, "", Op.of("setIntro", null, "<iframe src=x>")).accepted()).isEmpty();
  }

  @Test
  @DisplayName("given 연산이 13개 when 검증하면 then 12개까지만 받고 안내한다")
  void maxOps() {
    Op[] ops = new Op[13];
    for (int i = 0; i < 13; i++) ops[i] = Op.of("setColor", i % 2 == 0 ? "colorPrimary" : "colorAccent", "#0000" + (10 + i));
    Plan p = plan(snap(Map.of()), "", ops);
    assertThat(p.skipped()).anyMatch(x -> x.contains("최대 12개"));
  }

  @Test
  @DisplayName("given 프리셋 교체 when 적용하면 then 이전 색·레이아웃·글꼴 지정이 초기화되고 변경 요약에 반영")
  void presetChangeResetsOverrides() {
    Snapshot s = snap(Map.of("designPreset", "EXAM", "colorPrimary", "#123456", "heroLayout", "split", "typographyPreset", "CLEAN"));
    Applied a = AssistantPatchApplier.apply(s, List.of(Op.of("setPreset", null, "DARK")));
    assertThat(a.draftPatch()).containsEntry("designPreset", "DARK")
        .containsEntry("colorPrimary", null).containsEntry("heroLayout", null).containsEntry("typographyPreset", null);
    assertThat(a.changes()).hasSize(1);
    assertThat(a.changes().get(0).label()).isEqualTo("디자인");
    assertThat(a.changes().get(0).after()).contains("다크형").contains("초기화");
    assertThat(a.undo().draftBefore()).containsEntry("colorPrimary", "#123456").containsEntry("designPreset", "EXAM");
  }

  @Test
  @DisplayName("given 복합 변경 when 적용하면 then 프리셋·글꼴·레이아웃이 함께 바뀌고 이미 같은 값이면 변경이 없다")
  void compoundAndNoop() {
    Snapshot s = snap(Map.of("designPreset", "DARK"));
    Applied same = AssistantPatchApplier.apply(s, List.of(Op.of("setPreset", null, "DARK")));
    assertThat(same.isEmpty()).isTrue();
    Applied a = AssistantPatchApplier.apply(s, List.of(
        Op.of("setTypography", null, "PREMIUM"), Op.of("setHeroLayout", null, "center"), Op.of("setCardStyle", null, "minimal")));
    assertThat(a.changes()).extracting(AssistantPatchApplier.Change::label).containsExactly("글꼴 분위기", "상단 레이아웃", "카드 스타일");
  }

  @Test
  @DisplayName("given 섹션 숨김·표시·이동 when 적용하면 then 표시 맵과 순서가 갱신되고 hero는 항상 첫째")
  void sectionOps() {
    Snapshot s = snap(Map.of());
    Applied a = AssistantPatchApplier.apply(s, List.of(
        Op.section("hideSection", "sec-videos"), Op.section("showSection", "sec-cta"),
        new Op("moveSection", null, "first", "sec-lessons", null, null)));
    assertThat(a.visibility()).containsEntry("sec-videos", false).containsEntry("sec-cta", true);
    assertThat(a.order().get(0)).isEqualTo("sec-hero");
    assertThat(a.order().get(1)).isEqualTo("sec-lessons");
    assertThat(a.order()).hasSameSizeAs(AcademyService.DEFAULT_SECTION_ORDER).doesNotHaveDuplicates();
    Applied before = AssistantPatchApplier.apply(s, List.of(new Op("moveSection", null, "before", "sec-cta", "sec-intro", null)));
    assertThat(before.order().indexOf("sec-cta")).isEqualTo(before.order().indexOf("sec-intro") - 1);
    Applied after = AssistantPatchApplier.apply(s, List.of(new Op("moveSection", null, "after", "sec-intro", null, "sec-cta")));
    assertThat(after.order().indexOf("sec-intro")).isEqualTo(after.order().indexOf("sec-cta") + 1);
  }

  @Test
  @DisplayName("given 옛 저장 순서(새 섹션 누락) when 정규화하면 then 전체 목록으로 채우고 hero를 맨 앞에 둔다")
  void normalizedOrder() {
    List<String> old = List.of("sec-intro", "sec-hero", "sec-lessons");
    List<String> n = AssistantPatchApplier.normalizedOrder(old);
    assertThat(n).hasSameSizeAs(AcademyService.DEFAULT_SECTION_ORDER).containsExactlyInAnyOrderElementsOf(AcademyService.DEFAULT_SECTION_ORDER);
    assertThat(n.get(0)).isEqualTo("sec-hero");
  }

  @Test
  @DisplayName("given 적용 후 되돌리기 정보 when inverse하면 then 원래 값으로 복원하는 변경분(키 삭제 포함)이 나온다")
  void inverseRestores() {
    Map<String, String> before = new HashMap<>(Map.of("designPreset", "EXAM", "introHeading", "예전 제목"));
    Snapshot s = new Snapshot("예전 소개", before, Map.of("sec-videos", true), null);
    Applied a = AssistantPatchApplier.apply(s, List.of(
        Op.of("setPreset", null, "DARK"), Op.of("setText", "introHeading", "새 제목"), Op.of("setText", "ctaTitle", "상담해요"),
        new Op("setIntro", null, "새 소개", null, null, null), Op.section("hideSection", "sec-videos")));
    // 적용된 상태를 스냅샷으로 만든다
    Map<String, String> after = new HashMap<>(before);
    a.draftPatch().forEach((k, v) -> { if (v == null) after.remove(k); else after.put(k, v); });
    Snapshot now = new Snapshot(a.intro(), after, a.visibility(), a.order());

    Applied inv = AssistantPatchApplier.inverse(now, a.undo());

    assertThat(inv.draftPatch()).containsEntry("designPreset", "EXAM").containsEntry("introHeading", "예전 제목").containsEntry("ctaTitle", null);
    assertThat(inv.intro()).isEqualTo("예전 소개");
    assertThat(inv.visibility()).containsEntry("sec-videos", true);
  }

  @Test
  @DisplayName("given 카탈로그 when 서버 허용 목록과 비교하면 then 프리셋·문구 키·섹션이 서로 일치한다")
  void catalogMatchesServerAllowlists() {
    assertThat(AssistantDesignCatalog.PRESETS.keySet()).containsExactlyInAnyOrderElementsOf(AcademyAiCreateService.VALID_PRESETS);
    assertThat(AssistantDesignCatalog.TEXT_KEYS.keySet()).containsExactlyInAnyOrderElementsOf(AcademyAiContextService.EDITABLE_TEXT_TARGETS);
    assertThat(AcademyService.DEFAULT_SECTION_ORDER).containsAll(AssistantDesignCatalog.SECTIONS.keySet());
    assertThat(AssistantDesignCatalog.SECTIONS).doesNotContainKey("sec-hero");
  }
}
