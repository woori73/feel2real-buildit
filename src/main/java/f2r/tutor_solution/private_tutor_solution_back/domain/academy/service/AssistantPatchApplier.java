package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 대화 수정 턴의 patch(연산 목록)를 검증하고 계산한다. 순수 로직이라 DB·Spring 없이 테스트한다.
 *
 * <p>AI가 만든 연산은 신뢰하지 않는다. {@link #plan}이 허용 목록({@link AssistantDesignCatalog})과 값 형식으로 거르고,
 * 사용자가 말하지 않은 연락처·금액·숫자 주장은 막는다(허위 정보 금지). {@link #apply}는 현재 작업본 스냅샷에 연산을 적용한
 * 결과(변경분·변경 요약·되돌리기 정보)만 계산하며 저장은 호출자가 작업본 서비스로 한다 — 공개 데이터를 직접 쓰지 않는다.
 */
final class AssistantPatchApplier {

  static final int MAX_OPS = 12;
  static final int SHORT_TEXT_MAX = 120;
  static final int LONG_TEXT_MAX = 600;
  private static final Set<String> LONG_TEXT_KEYS = Set.of(
      "introMotto", "introBodySecondary", "philosophyTitle", "principalDesc", "principalQuote",
      "featuredDescription", "ctaSubtitle");
  private static final Pattern HEX = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
  private static final Pattern PHONE = Pattern.compile("\\d{2,4}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");
  private static final Pattern MONEY = Pattern.compile("\\d[\\d,]*\\s*(만\\s*)?원");
  private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.|[\\w.+-]+@[\\w-]+\\.)");
  private static final Pattern NUMBER = Pattern.compile("\\d{2,}");
  private static final Pattern DANGEROUS = Pattern.compile("(?i)<\\s*script|javascript:|onerror\\s*=|onload\\s*=|<\\s*iframe");

  private AssistantPatchApplier() {}

  /** AI 또는 규칙 해석기가 낸 연산 한 개. 쓰지 않는 필드는 null. */
  record Op(String op, String key, String value, String id, String before, String after) {
    static Op of(String op, String key, String value) {
      return new Op(op, key, value, null, null, null);
    }

    static Op section(String op, String id) {
      return new Op(op, null, null, id, null, null);
    }
  }

  /** 현재 작업본 상태(읽기 전용 스냅샷). */
  record Snapshot(
      String intro, Map<String, String> draft, Map<String, Boolean> visibility, List<String> order,
      Set<String> imageSlots) {
    Snapshot {
      draft = draft == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(draft));
      visibility = visibility == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(visibility));
      order = order == null ? List.of() : List.copyOf(order);
      imageSlots = imageSlots == null ? Set.of() : Set.copyOf(imageSlots);
    }

    /** 이미지 슬롯 정보 없이 만든다(이미지 연산을 쓰지 않는 호출·테스트용). */
    Snapshot(String intro, Map<String, String> draft, Map<String, Boolean> visibility, List<String> order) {
      this(intro, draft, visibility, order, null);
    }
  }

  record Plan(List<Op> accepted, List<String> skipped) {}

  /** 변경 한 건(변경 요약 카드·되돌리기 안내용). sectionId는 미리보기를 그 섹션으로 스크롤할 때 쓴다. */
  record Change(String label, String before, String after, String sectionId) {}

  /** 되돌리기 정보: 건드린 키의 이전 값(없었으면 null)과 이전 표시·순서·소개. */
  record Undo(
      boolean introChanged, String introBefore, Map<String, String> draftBefore,
      Map<String, Boolean> visibilityBefore, List<String> orderBefore) {}

  record Applied(
      String intro, Map<String, String> draftPatch, Map<String, Boolean> visibility, List<String> order,
      List<Change> changes, Undo undo, List<String> mediaClears) {
    Applied(
        String intro, Map<String, String> draftPatch, Map<String, Boolean> visibility, List<String> order,
        List<Change> changes, Undo undo) {
      this(intro, draftPatch, visibility, order, changes, undo, List.of());
    }

    boolean isEmpty() {
      return changes.isEmpty();
    }
  }

  // ───────────────────────── 검증 ─────────────────────────

  /**
   * 연산을 거른다. 허용되지 않은 값은 통째로 버리고 이유를 skipped에 남긴다(사용자에게 그대로 안내할 수 있는 문장).
   * 같은 대상을 여러 번 바꾸면 마지막 연산만 남는다.
   */
  static Plan plan(List<Op> ops, Snapshot snap, String userMessage) {
    List<Op> accepted = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    if (ops == null) return new Plan(accepted, skipped);
    int index = 0;
    for (Op raw : ops) {
      if (raw == null || raw.op() == null) continue;
      if (++index > MAX_OPS) {
        skipped.add("한 번에 바꿀 수 있는 항목은 최대 " + MAX_OPS + "개예요. 나머지는 다음 요청으로 알려 주세요.");
        break;
      }
      String problem = check(raw, snap, userMessage, accepted);
      if (problem != null) skipped.add(problem);
    }
    return new Plan(dedupe(accepted), skipped);
  }

  private static String check(Op op, Snapshot snap, String userMessage, List<Op> accepted) {
    switch (op.op()) {
      case "setPreset": {
        String v = upper(op.value());
        if (!AssistantDesignCatalog.PRESETS.containsKey(v)) return "알 수 없는 디자인이라 건너뛰었어요: " + shorten(op.value());
        accepted.add(Op.of("setPreset", null, v));
        return null;
      }
      case "setColor": {
        String k = op.key();
        if (k == null || !AssistantDesignCatalog.COLOR_KEYS.containsKey(k)) return "바꿀 수 없는 색상 항목이라 건너뛰었어요: " + shorten(k);
        String v = op.value() == null ? "" : op.value().trim();
        if (!HEX.matcher(v).matches()) return "색상은 #RRGGBB 형식이어야 해서 건너뛰었어요: " + shorten(op.value());
        accepted.add(Op.of("setColor", k, v.toLowerCase()));
        return null;
      }
      case "setTypography": {
        String v = upper(op.value());
        if (!AssistantDesignCatalog.TYPOGRAPHY.containsKey(v)) return "알 수 없는 글꼴 분위기라 건너뛰었어요: " + shorten(op.value());
        accepted.add(Op.of("setTypography", null, v));
        return null;
      }
      case "setFont": {
        String k = op.key();
        String v = upper(op.value());
        if (!"headingFont".equals(k) && !"bodyFont".equals(k)) return "바꿀 수 없는 글꼴 항목이라 건너뛰었어요: " + shorten(k);
        if (!AssistantDesignCatalog.FONTS.contains(v)) return "등록되지 않은 글꼴이라 건너뛰었어요: " + shorten(op.value());
        accepted.add(Op.of("setFont", k, v));
        return null;
      }
      case "setHeroLayout": {
        String v = lower(op.value());
        if (!AssistantDesignCatalog.HERO_LAYOUTS.contains(v)) return "알 수 없는 상단 레이아웃이라 건너뛰었어요: " + shorten(op.value());
        accepted.add(Op.of("setHeroLayout", null, v));
        return null;
      }
      case "setCardStyle": {
        String v = lower(op.value());
        if (!AssistantDesignCatalog.CARD_STYLES.contains(v)) return "알 수 없는 카드 스타일이라 건너뛰었어요: " + shorten(op.value());
        accepted.add(Op.of("setCardStyle", null, v));
        return null;
      }
      case "clearImage": {
        String slot = lower(op.value());
        String[] meta = AssistantDesignCatalog.IMAGE_SLOTS.get(slot);
        if (meta == null) return "알 수 없는 이미지 자리라 건너뛰었어요: " + shorten(op.value());
        if (!snap.imageSlots().contains(slot)) return meta[0] + "은(는) 이미 비어 있어요.";
        accepted.add(Op.of("clearImage", null, slot));
        return null;
      }
      case "setText": {
        String k = op.key();
        if (k == null || !AssistantDesignCatalog.TEXT_KEYS.containsKey(k)) return "바꿀 수 없는 문구 항목이라 건너뛰었어요: " + shorten(k);
        String v = cleanText(op.value());
        int max = LONG_TEXT_KEYS.contains(k) ? LONG_TEXT_MAX : SHORT_TEXT_MAX;
        String label = AssistantDesignCatalog.TEXT_KEYS.get(k)[0];
        if (v.isEmpty()) return label + " 문구가 비어 있어 건너뛰었어요.";
        if (v.length() > max) return label + " 문구가 너무 길어(" + max + "자 이하) 건너뛰었어요.";
        String fact = factProblem(v, userMessage, snap.draft().get(k));
        if (fact != null) return label + ": " + fact;
        accepted.add(Op.of("setText", k, v));
        return null;
      }
      case "setIntro": {
        String v = cleanText(op.value());
        if (v.isEmpty()) return "학원 소개 문단이 비어 있어 건너뛰었어요.";
        if (v.length() > LONG_TEXT_MAX) return "학원 소개 문단이 너무 길어(" + LONG_TEXT_MAX + "자 이하) 건너뛰었어요.";
        String fact = factProblem(v, userMessage, snap.intro());
        if (fact != null) return "학원 소개 문단: " + fact;
        accepted.add(Op.of("setIntro", null, v));
        return null;
      }
      case "showSection":
      case "hideSection": {
        if (!AssistantDesignCatalog.SECTIONS.containsKey(op.id())) return "바꿀 수 없는 섹션이라 건너뛰었어요: " + shorten(op.id());
        accepted.add(Op.section(op.op(), op.id()));
        return null;
      }
      case "moveSection": {
        String id = op.id();
        if (!AssistantDesignCatalog.SECTIONS.containsKey(id)) return "옮길 수 없는 섹션이라 건너뛰었어요: " + shorten(id);
        String mode = lower(op.value());
        if ("first".equals(mode) || "last".equals(mode)) {
          accepted.add(new Op("moveSection", null, mode, id, null, null));
          return null;
        }
        if (op.before() != null && AssistantDesignCatalog.SECTIONS.containsKey(op.before()) && !op.before().equals(id)) {
          accepted.add(new Op("moveSection", null, "before", id, op.before(), null));
          return null;
        }
        if (op.after() != null && AssistantDesignCatalog.SECTIONS.containsKey(op.after()) && !op.after().equals(id)) {
          accepted.add(new Op("moveSection", null, "after", id, null, op.after()));
          return null;
        }
        return "섹션을 어디로 옮길지 알 수 없어 건너뛰었어요: " + AssistantDesignCatalog.SECTIONS.get(id);
      }
      default:
        return "지원하지 않는 변경이라 건너뛰었어요: " + shorten(op.op());
    }
  }

  /** 사용자 말·기존 값에 없던 연락처·금액·URL·숫자 주장을 막는다. 문제가 없으면 null. */
  static String factProblem(String value, String userMessage, String currentValue) {
    String basis = (userMessage == null ? "" : userMessage) + "\n" + (currentValue == null ? "" : currentValue);
    if (DANGEROUS.matcher(value).find()) return "허용되지 않은 내용이 있어 건너뛰었어요.";
    if (PHONE.matcher(value).find() && !basisContains(basis, PHONE, value)) return "전화번호는 직접 알려 주셔야 넣을 수 있어요.";
    if (MONEY.matcher(value).find() && !basisContains(basis, MONEY, value)) return "수강료 같은 금액은 직접 알려 주셔야 넣을 수 있어요.";
    if (URL.matcher(value).find() && !basisContains(basis, URL, value)) return "주소·이메일은 직접 알려 주셔야 넣을 수 있어요.";
    java.util.regex.Matcher m = NUMBER.matcher(value);
    while (m.find()) {
      if (!basis.contains(m.group())) return "실적·인원 같은 숫자는 직접 알려 주셔야 넣을 수 있어요.";
    }
    return null;
  }

  private static boolean basisContains(String basis, Pattern p, String value) {
    java.util.regex.Matcher m = p.matcher(value);
    while (m.find()) {
      if (!basis.contains(m.group().trim())) return false;
    }
    return true;
  }

  private static List<Op> dedupe(List<Op> ops) {
    Map<String, Op> last = new LinkedHashMap<>();
    for (Op o : ops) {
      String k = switch (o.op()) {
        case "setColor", "setFont", "setText" -> o.op() + ":" + o.key();
        case "moveSection" -> "move:" + o.id();
        case "clearImage" -> "clear:" + o.value();
        case "showSection", "hideSection" -> "vis:" + o.id();
        default -> o.op();
      };
      last.remove(k);
      last.put(k, o);
    }
    // 프리셋 교체는 같은 턴의 개별 색·글꼴·레이아웃 지정보다 먼저 적용돼야 "다크로 바꾸고 주색은 파랑" 같은 복합 요청이 맞게 된다.
    List<Op> result = new ArrayList<>(last.values());
    result.sort((a, b) -> Boolean.compare(!"setPreset".equals(a.op()), !"setPreset".equals(b.op())));
    return result;
  }

  // ───────────────────────── 적용 계산 ─────────────────────────

  static Applied apply(Snapshot snap, List<Op> ops) {
    Map<String, String> draft = new HashMap<>(snap.draft());
    Map<String, Boolean> vis = new HashMap<>(snap.visibility());
    List<String> order = normalizedOrder(snap.order());
    String intro = snap.intro();
    List<String> moves = new ArrayList<>();
    List<String> mediaClears = new ArrayList<>();
    Set<String> touched = new java.util.LinkedHashSet<>();

    for (Op o : ops) {
      switch (o.op()) {
        case "setPreset" -> {
          draft.put("designPreset", o.value());
          touched.add("designPreset");
          for (String k : AssistantDesignCatalog.STYLE_OVERRIDE_KEYS) {
            if (draft.remove(k) != null) touched.add(k);
          }
        }
        case "setColor", "setFont" -> {
          draft.put(o.key(), o.value());
          touched.add(o.key());
        }
        case "setTypography" -> {
          draft.put("typographyPreset", o.value());
          touched.add("typographyPreset");
          for (String k : List.of("headingFont", "bodyFont")) {
            if (draft.remove(k) != null) touched.add(k);
          }
        }
        case "setHeroLayout" -> {
          draft.put("heroLayout", o.value());
          touched.add("heroLayout");
        }
        case "setCardStyle" -> {
          draft.put("cardStyle", o.value());
          touched.add("cardStyle");
        }
        case "setText" -> {
          draft.put(o.key(), o.value());
          touched.add(o.key());
        }
        case "setIntro" -> intro = o.value();
        case "clearImage" -> mediaClears.add(o.value());
        case "showSection" -> vis.put(o.id(), true);
        case "hideSection" -> vis.put(o.id(), false);
        case "moveSection" -> {
          order.remove(o.id());
          switch (o.value()) {
            case "first" -> order.add(Math.min(1, order.size()), o.id());
            case "last" -> order.add(o.id());
            case "before" -> order.add(Math.max(1, order.indexOf(o.before())), o.id());
            default -> order.add(Math.min(order.size(), order.indexOf(o.after()) + 1), o.id());
          }
          moves.add(AssistantDesignCatalog.SECTIONS.get(o.id()));
        }
        default -> { }
      }
    }

    List<Change> changes = new ArrayList<>();
    boolean presetChanged = !Objects.equals(draft.get("designPreset"), snap.draft().get("designPreset"));
    Map<String, String> draftPatch = new LinkedHashMap<>();
    Map<String, String> draftBefore = new HashMap<>();
    boolean overridesReset = false;
    for (String key : touched) {
      String before = snap.draft().get(key);
      String after = draft.get(key);
      if (Objects.equals(before, after)) continue;
      draftPatch.put(key, after);
      draftBefore.put(key, before);
      if (presetChanged && after == null && AssistantDesignCatalog.STYLE_OVERRIDE_KEYS.contains(key)) {
        overridesReset = true;
        continue;
      }
      changes.add(draftChange(key, before, after));
    }
    if (presetChanged) {
      Change c = new Change("디자인", AssistantDesignCatalog.presetLabel(snap.draft().get("designPreset")),
          AssistantDesignCatalog.presetLabel(draft.get("designPreset")) + (overridesReset ? " (이전 색·레이아웃 설정은 초기화)" : ""), null);
      changes.removeIf(x -> "디자인".equals(x.label()));
      changes.add(0, c);
    }

    for (String slot : mediaClears) {
      String[] meta = AssistantDesignCatalog.IMAGE_SLOTS.get(slot);
      changes.add(new Change("사진 삭제: " + meta[0], "있음", "없음", meta[1]));
    }

    boolean introChanged = !Objects.equals(intro, snap.intro());
    if (introChanged) changes.add(new Change("학원 소개 문단", shorten(snap.intro()), shorten(intro), "sec-intro"));

    Map<String, Boolean> visOut = null;
    for (String id : AssistantDesignCatalog.SECTIONS.keySet()) {
      boolean b = visible(snap.visibility(), id);
      boolean a = visible(vis, id);
      if (a != b) {
        changes.add(new Change("섹션 표시: " + AssistantDesignCatalog.SECTIONS.get(id), b ? "표시" : "숨김", a ? "표시" : "숨김", a ? id : null));
        visOut = vis;
      }
    }
    List<String> orderOut = null;
    if (!order.equals(normalizedOrder(snap.order()))) {
      orderOut = order;
      String moved = String.join(", ", new java.util.LinkedHashSet<>(moves));
      changes.add(new Change("섹션 순서", "이전 순서", moved + " 위치 변경", moves.isEmpty() ? null : lastMovedId(ops)));
    }

    Undo undo = new Undo(
        introChanged, snap.intro(), Collections.unmodifiableMap(draftBefore),
        visOut == null ? null : new HashMap<>(snap.visibility()),
        orderOut == null ? null : normalizedOrder(snap.order()));
    return new Applied(
        introChanged ? intro : null, draftPatch,
        visOut == null ? null : new HashMap<>(visOut), orderOut, changes, undo, List.copyOf(mediaClears));
  }

  private static String lastMovedId(List<Op> ops) {
    String id = null;
    for (Op o : ops) if ("moveSection".equals(o.op())) id = o.id();
    return id;
  }

  /** 섹션이 지금 보이는가. opt-in 섹션은 명시적 true일 때만, 나머지는 명시적 false가 아닐 때 보인다. */
  static boolean visible(Map<String, Boolean> vis, String id) {
    Boolean v = vis.get(id);
    return AssistantDesignCatalog.OPT_IN_SECTIONS.contains(id) ? Boolean.TRUE.equals(v) : !Boolean.FALSE.equals(v);
  }

  /** 저장된 순서에 빠진 섹션을 기본 위치에 채워 항상 전체 목록으로 만든다(sec-hero는 맨 앞). */
  static List<String> normalizedOrder(List<String> stored) {
    List<String> base = new ArrayList<>(AcademyService.DEFAULT_SECTION_ORDER);
    if (stored == null || stored.isEmpty()) return base;
    List<String> result = new ArrayList<>();
    for (String id : stored) if (base.contains(id) && !result.contains(id)) result.add(id);
    for (String id : base) {
      if (result.contains(id)) continue;
      int pos = result.size();
      for (int i = base.indexOf(id) + 1; i < base.size(); i++) {
        int existing = result.indexOf(base.get(i));
        if (existing >= 0) {
          pos = existing;
          break;
        }
      }
      result.add(pos, id);
    }
    if (!"sec-hero".equals(result.get(0))) {
      result.remove("sec-hero");
      result.add(0, "sec-hero");
    }
    return result;
  }

  private static Change draftChange(String key, String before, String after) {
    String label;
    String b = before;
    String a = after;
    String section = null;
    switch (key) {
      case "designPreset" -> {
        label = "디자인";
        b = AssistantDesignCatalog.presetLabel(before);
        a = AssistantDesignCatalog.presetLabel(after);
      }
      case "typographyPreset" -> {
        label = "글꼴 분위기";
        b = before == null ? "기본" : AssistantDesignCatalog.TYPOGRAPHY.getOrDefault(before, before);
        a = after == null ? "기본" : AssistantDesignCatalog.TYPOGRAPHY.getOrDefault(after, after);
      }
      case "headingFont", "bodyFont" -> label = "headingFont".equals(key) ? "제목 글꼴" : "본문 글꼴";
      case "heroLayout" -> {
        label = "상단 레이아웃";
        b = heroLabel(before);
        a = heroLabel(after);
        section = "sec-hero";
      }
      case "cardStyle" -> {
        label = "카드 스타일";
        b = cardLabel(before);
        a = cardLabel(after);
      }
      default -> {
        if (AssistantDesignCatalog.COLOR_KEYS.containsKey(key)) {
          label = "색상: " + AssistantDesignCatalog.COLOR_KEYS.get(key).split("\\(")[0];
          if ("colorHeroBg".equals(key) || "colorHeroText".equals(key)) section = "sec-hero";
        } else if (AssistantDesignCatalog.TEXT_KEYS.containsKey(key)) {
          label = AssistantDesignCatalog.TEXT_KEYS.get(key)[0];
          section = AssistantDesignCatalog.TEXT_KEYS.get(key)[1];
          b = shorten(before);
          a = shorten(after);
        } else {
          label = key;
        }
      }
    }
    return new Change(label, b == null ? "(없음)" : b, a == null ? "(기본값)" : a, section);
  }

  private static String heroLabel(String v) {
    if (v == null) return "기본";
    return switch (v) {
      case "center" -> "가운데 정렬";
      case "split" -> "좌우 분할";
      case "visual" -> "이미지 중심";
      case "gradient" -> "그라데이션";
      default -> v;
    };
  }

  private static String cardLabel(String v) {
    if (v == null) return "기본";
    return switch (v) {
      case "bordered" -> "테두리형";
      case "rounded" -> "둥근형";
      case "media" -> "이미지형";
      case "minimal" -> "미니멀형";
      default -> v;
    };
  }

  // ───────────────────────── 되돌리기 ─────────────────────────

  /** 되돌리기 정보를 작업본에 다시 쓸 변경분으로 바꾼다(null 값은 키 삭제). */
  static Applied inverse(Snapshot current, Undo undo) {
    Map<String, String> draftPatch = new LinkedHashMap<>(undo.draftBefore());
    List<Change> changes = new ArrayList<>();
    for (Map.Entry<String, String> e : undo.draftBefore().entrySet()) {
      changes.add(draftChange(e.getKey(), current.draft().get(e.getKey()), e.getValue()));
    }
    if (undo.introChanged()) changes.add(new Change("학원 소개 문단", shorten(current.intro()), shorten(undo.introBefore()), "sec-intro"));
    if (undo.visibilityBefore() != null) changes.add(new Change("섹션 표시", "이전 상태로", "복원", null));
    if (undo.orderBefore() != null) changes.add(new Change("섹션 순서", "이전 순서로", "복원", null));
    return new Applied(
        undo.introChanged() ? undo.introBefore() : null, draftPatch,
        undo.visibilityBefore(), undo.orderBefore(), changes, null);
  }

  // ───────────────────────── 문자열 도구 ─────────────────────────

  private static String upper(String v) {
    return v == null ? "" : v.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private static String lower(String v) {
    return v == null ? "" : v.trim().toLowerCase(java.util.Locale.ROOT);
  }

  static String cleanText(String v) {
    if (v == null) return "";
    return v.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").replaceAll("\\s*\\n\\s*", "\n").trim();
  }

  static String shorten(String v) {
    if (v == null) return "";
    String s = v.replace('\n', ' ').trim();
    return s.length() > 60 ? s.substring(0, 60) + "…" : s;
  }
}
