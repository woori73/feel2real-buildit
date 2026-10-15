package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 관리자 작업본과 (그 사이 바뀐) 공개 내용의 필드 단위 3자 병합.
 *
 * <p>기준(base) = 작업본을 만든 시점의 공개 내용, mine = 관리자 작업본, live = 현재 공개 내용.
 * 필드마다: 관리자가 안 바꿨으면 공개 값, 공개가 안 바뀌었으면 관리자 값, 같은 값이면 그 값. 양쪽이 서로 다르게 바꾼
 * <b>충돌</b>은 공개 쪽 값을 유지하고 충돌 목록으로 알린다 — 어느 쪽 수정도 조용히 덮어쓰지 않는다.
 */
final class AdminDraftMerger {

  private AdminDraftMerger() {}

  record Result(String intro, AcademyPageSettings settings, List<String> conflicts, List<String> keptMine) {}

  static Result merge(
      String baseIntro, AcademyPageSettings base,
      String mineIntro, AcademyPageSettings mine,
      String liveIntro, AcademyPageSettings live,
      boolean baseKnown) {
    List<String> conflicts = new ArrayList<>();
    List<String> keptMine = new ArrayList<>();

    String intro = pick("intro", baseIntro, mineIntro, liveIntro, baseKnown, conflicts, keptMine);

    Map<String, String> bd = draft(base);
    Map<String, String> md = draft(mine);
    Map<String, String> ld = draft(live);
    Set<String> keys = new TreeSet<>();
    keys.addAll(bd.keySet());
    keys.addAll(md.keySet());
    keys.addAll(ld.keySet());
    Map<String, String> mergedDraft = new HashMap<>();
    for (String k : keys) {
      String v = pick("builderDraft." + k, bd.get(k), md.get(k), ld.get(k), baseKnown, conflicts, keptMine);
      if (v != null) mergedDraft.put(k, v);
    }

    Object blog = pick("blogUrl", s(base, "blogUrl"), s(mine, "blogUrl"), s(live, "blogUrl"), baseKnown, conflicts, keptMine);
    Object store = pick("storeUrl", s(base, "storeUrl"), s(mine, "storeUrl"), s(live, "storeUrl"), baseKnown, conflicts, keptMine);
    Object map = pick("naverMapUrl", s(base, "naverMapUrl"), s(mine, "naverMapUrl"), s(live, "naverMapUrl"), baseKnown, conflicts, keptMine);
    Object yt = pick("heroYoutubeUrl", s(base, "heroYoutubeUrl"), s(mine, "heroYoutubeUrl"), s(live, "heroYoutubeUrl"), baseKnown, conflicts, keptMine);
    Object vis = pickObj("sectionVisibility", f(base, "vis"), f(mine, "vis"), f(live, "vis"), baseKnown, conflicts, keptMine);
    Object order = pickObj("sectionOrder", f(base, "order"), f(mine, "order"), f(live, "order"), baseKnown, conflicts, keptMine);
    Object biz = pickObj("businessInfo", f(base, "biz"), f(mine, "biz"), f(live, "biz"), baseKnown, conflicts, keptMine);

    @SuppressWarnings("unchecked")
    AcademyPageSettings merged = new AcademyPageSettings(
        (String) blog, (String) store, (String) map, (String) yt,
        (Map<String, Boolean>) vis, (List<String>) order,
        mergedDraft.isEmpty() ? null : mergedDraft,
        (f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyBusinessInfo) biz);
    return new Result(intro, merged, conflicts, keptMine);
  }

  private static String pick(
      String name, String b, String m, String l, boolean baseKnown, List<String> conflicts, List<String> keptMine) {
    return (String) pickObj(name, b, m, l, baseKnown, conflicts, keptMine);
  }

  private static Object pickObj(
      String name, Object b, Object m, Object l, boolean baseKnown, List<String> conflicts, List<String> keptMine) {
    if (Objects.equals(m, l)) return l;
    if (!baseKnown) { // 기준을 모르면 공개 쪽을 우선하고 다른 값은 충돌로 알린다
      conflicts.add(name);
      return l;
    }
    boolean mineChanged = !Objects.equals(b, m);
    boolean liveChanged = !Objects.equals(b, l);
    if (!mineChanged) return l;
    if (!liveChanged) {
      keptMine.add(name);
      return m;
    }
    conflicts.add(name);
    return l;
  }

  private static Map<String, String> draft(AcademyPageSettings s) {
    return s == null || s.builderDraft() == null ? Map.of() : s.builderDraft();
  }

  private static String s(AcademyPageSettings p, String f) {
    if (p == null) return null;
    return switch (f) {
      case "blogUrl" -> p.blogUrl();
      case "storeUrl" -> p.storeUrl();
      case "naverMapUrl" -> p.naverMapUrl();
      case "heroYoutubeUrl" -> p.heroYoutubeUrl();
      default -> null;
    };
  }

  private static Object f(AcademyPageSettings p, String f) {
    if (p == null) return null;
    return switch (f) {
      case "vis" -> p.sectionVisibility();
      case "order" -> p.sectionOrder();
      case "biz" -> p.businessInfo();
      default -> null;
    };
  }

  /** 테스트·감사용: 병합 결과에서 충돌이 하나라도 있는가. */
  static boolean hasConflicts(Result r) {
    return !new HashSet<>(r.conflicts()).isEmpty();
  }
}
