package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Op;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 규칙 기반 수정 의도 해석기. AI(LLM)가 응답하지 않거나 형식이 깨졌을 때의 폴백이면서, 엔진 없이 측정 가능한 기준선이다.
 *
 * <p>다루는 요청: 분위기(고급·친근·깔끔·활기·신뢰·다크 등) → 프리셋+글꼴+레이아웃 복합 변경, 색상 이름 → 주색,
 * 섹션 이름 + 숨겨/보여/위로/아래로. 문구를 새로 써 달라는 요청은 규칙으로 쓰지 않는다(AI 전용 — 지어내기 방지).
 * 순수 로직이라 단위 테스트한다.
 */
final class AssistantRuleInterpreter {

  /** 해석 결과. ops가 비어 있으면 question(되묻기)이 의미를 가진다. */
  record Result(List<Op> ops, String reply, String question) {}

  private AssistantRuleInterpreter() {}

  /** 분위기 → {프리셋, 타이포, 상단 레이아웃(선택), 카드 스타일(선택)}. 앞에서부터 먼저 걸린 분위기를 쓴다. */
  private static final Map<String[], String[]> MOODS = new LinkedHashMap<>();

  static {
    MOODS.put(new String[] {"다크", "어둡", "어두운", "블랙", "시크"}, new String[] {"DARK", "MODERN", "gradient", "minimal"});
    MOODS.put(new String[] {"미니멀", "깔끔", "심플", "단정", "군더더기"}, new String[] {"MINIMAL", "CLEAN", "center", "minimal"});
    MOODS.put(new String[] {"고급", "프리미엄", "품격", "격조", "고풍", "차분"}, new String[] {"DARK", "PREMIUM", "center", "minimal"});
    MOODS.put(new String[] {"따뜻", "감성", "포근", "아늑"}, new String[] {"WARM", "FRIENDLY", "visual", "rounded"});
    MOODS.put(new String[] {"친근", "밝", "경쾌", "부드럽", "귀여"}, new String[] {"ENGLISH", "FRIENDLY", "split", "rounded"});
    MOODS.put(new String[] {"역동", "활기", "활동적", "에너지", "트렌디", "젊"}, new String[] {"DYNAMIC", "MODERN", "split", "rounded"});
    MOODS.put(new String[] {"신뢰", "전문", "입시", "진지", "학구"}, new String[] {"EXAM", "ACADEMIC", "center", "bordered"});
    MOODS.put(new String[] {"온라인", "테크", "모던", "세련"}, new String[] {"ONLINE", "MODERN", "gradient", "minimal"});
    MOODS.put(new String[] {"공부방", "소규모", "교습소"}, new String[] {"STUDY", "CLEAN", "visual", "bordered"});
    MOODS.put(new String[] {"예체능", "갤러리", "예술"}, new String[] {"ARTS", "MODERN", "visual", "media"});
    MOODS.put(new String[] {"사진", "이미지 중심", "비주얼"}, new String[] {"PHOTO", "MODERN", "visual", "media"});
  }

  private static final Map<String, String> COLORS = new LinkedHashMap<>();

  static {
    COLORS.put("빨간", "#dc2626"); COLORS.put("빨강", "#dc2626"); COLORS.put("레드", "#dc2626");
    COLORS.put("주황", "#ea580c"); COLORS.put("오렌지", "#ea580c");
    COLORS.put("노란", "#eab308"); COLORS.put("노랑", "#eab308"); COLORS.put("옐로", "#eab308");
    COLORS.put("초록", "#16a34a"); COLORS.put("녹색", "#16a34a"); COLORS.put("그린", "#16a34a");
    COLORS.put("민트", "#0d9488"); COLORS.put("청록", "#0d9488");
    COLORS.put("파란", "#2563eb"); COLORS.put("파랑", "#2563eb"); COLORS.put("블루", "#2563eb");
    COLORS.put("남색", "#1e3a8a"); COLORS.put("네이비", "#1e3a8a");
    COLORS.put("보라", "#7c3aed"); COLORS.put("퍼플", "#7c3aed");
    COLORS.put("분홍", "#ec4899"); COLORS.put("핑크", "#ec4899");
    COLORS.put("갈색", "#92400e"); COLORS.put("브라운", "#92400e");
    COLORS.put("검정", "#111111"); COLORS.put("검은", "#111111");
  }

  static Result interpret(String raw, Map<String, String> currentDraft) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) return new Result(List.of(), null, "어떻게 바꿀까요? 예: \"더 고급스럽게 바꿔 줘\", \"주색을 파란색으로\", \"수업 영상 섹션 숨겨 줘\"");
    String t = text.replaceAll("\\s+", "");
    List<Op> ops = new ArrayList<>();
    List<String> said = new ArrayList<>();

    // 섹션 표시·순서
    for (Map.Entry<String, String> s : AssistantDesignCatalog.SECTIONS.entrySet()) {
      for (String name : sectionNames(s.getKey(), s.getValue())) {
        int at = t.indexOf(name);
        if (at < 0) continue;
        String after = t.substring(at + name.length());
        String head = after.length() > 14 ? after.substring(0, 14) : after;
        if (containsAny(head, "숨겨", "숨기", "없애", "빼", "삭제", "끄", "안보이", "제거")) {
          ops.add(Op.section("hideSection", s.getKey()));
          said.add(s.getValue() + " 숨김");
        } else if (containsAny(head, "보여", "보이게", "추가", "넣어", "켜", "표시")) {
          ops.add(Op.section("showSection", s.getKey()));
          said.add(s.getValue() + " 표시");
        } else if (containsAny(head, "위로", "앞으로", "맨위", "맨앞", "먼저", "상단")) {
          ops.add(new Op("moveSection", null, "first", s.getKey(), null, null));
          said.add(s.getValue() + " 위로");
        } else if (containsAny(head, "아래로", "뒤로", "맨아래", "맨뒤", "마지막", "하단")) {
          ops.add(new Op("moveSection", null, "last", s.getKey(), null, null));
          said.add(s.getValue() + " 아래로");
        }
        break;
      }
    }

    // 색상: "주색/메인 색/버튼 색 ... 파란색"
    String colorHex = null;
    for (Map.Entry<String, String> c : COLORS.entrySet()) {
      if (t.contains(c.getKey())) {
        colorHex = c.getValue();
        break;
      }
    }
    boolean colorWord = containsAny(t, "색", "컬러", "톤");
    if (colorHex != null && colorWord) {
      if (containsAny(t, "배경")) {
        ops.add(Op.of("setColor", "colorHeroBg", colorHex));
        said.add("상단 배경색");
      } else {
        ops.add(Op.of("setColor", "colorPrimary", colorHex));
        if (containsAny(t, "포인트", "강조")) ops.add(Op.of("setColor", "colorAccent", colorHex));
        said.add("주색");
      }
    }

    // 디자인 자산 — 요청에 자산의 고유 키워드("학교형", "랜딩", "서서히 등장" 등)가 직접 있으면 그 자산을 적용한다.
    // 자산 키워드는 아래 분위기 단어와 겹치지 않게 잡혀 있어 기존 분위기 해석은 그대로다.
    AssistantAssetCatalog.Asset asset = AssistantAssetCatalog.byKeyword(t);
    boolean recipeHit = asset != null && asset.recipe();
    if (asset != null) {
      ops.add(Op.section("applyAsset", asset.id()));
      said.add("'" + asset.label() + "'");
    }
    if (containsAny(t, "스크롤", "등장", "나타나", "애니메이션", "움직임", "모션")
        && containsAny(t, "없애", "꺼", "끄", "빼", "멈춰", "움직이지", "정적")) {
      ops.add(Op.of("setMotion", null, "none"));
      said.add("등장 효과 끄기");
    }

    // 분위기 복합 변경 — 색을 직접 지정한 요청은 프리셋을 건드리지 않는다(요청 범위를 넘지 않기 위해).
    // 섹션 표시·순서 요청은 분위기 요청과 함께 올 수 있다("더 고급스럽게, 영상은 숨겨 줘").
    boolean colorOps = ops.stream().anyMatch(o -> "setColor".equals(o.op()));
    if (!recipeHit && (!colorOps || containsAny(t, "분위기", "느낌", "스타일", "디자인", "테마"))) {
      for (Map.Entry<String[], String[]> m : MOODS.entrySet()) {
        if (containsAny(t, m.getKey())) {
          String[] v = m.getValue();
          ops.add(0, Op.of("setPreset", null, v[0]));
          ops.add(Op.of("setTypography", null, v[1]));
          if (v[2] != null) ops.add(Op.of("setHeroLayout", null, v[2]));
          if (v[3] != null) ops.add(Op.of("setCardStyle", null, v[3]));
          said.add(m.getKey()[0] + " 분위기의 디자인");
          break;
        }
      }
    }

    // 글꼴
    if (containsAny(t, "명조", "세리프")) {
      ops.add(Op.of("setFont", "headingFont", "NOTO_SERIF_KR"));
      said.add("제목 글꼴");
    }
    if (containsAny(t, "고딕") && containsAny(t, "제목", "글꼴", "폰트")) {
      ops.add(Op.of("setFont", "headingFont", "PRETENDARD"));
      said.add("제목 글꼴");
    }

    // 상단 레이아웃·카드
    if (containsAny(t, "가운데정렬", "중앙정렬")) ops.add(Op.of("setHeroLayout", null, "center"));
    if (containsAny(t, "좌우분할", "양옆")) ops.add(Op.of("setHeroLayout", null, "split"));

    if (ops.isEmpty()) {
      return new Result(List.of(), null,
          "어떻게 바꾸면 좋을지 조금 더 구체적으로 알려 주세요. 예: \"더 고급스럽게\", \"밝고 친근한 분위기로\", "
              + "\"주색을 초록색으로\", \"수업 영상 섹션 숨겨 줘\", \"상담 신청을 맨 위로\".");
    }
    return new Result(ops, String.join(", ", said) + " 요청을 반영했어요.", null);
  }

  private static List<String> sectionNames(String id, String label) {
    List<String> names = new ArrayList<>();
    names.add(label.replaceAll("[\\s()·]", "").replaceAll("\\(.*\\)", ""));
    switch (id) {
      case "sec-videos" -> names.addAll(List.of("수업영상", "영상섹션", "영상"));
      case "sec-intro" -> names.addAll(List.of("학원소개", "소개섹션"));
      case "sec-principal" -> names.addAll(List.of("원장소개", "원장님소개", "원장"));
      case "sec-teachers" -> names.addAll(List.of("강사진", "강사소개", "선생님소개"));
      case "sec-philosophy" -> names.addAll(List.of("학습철학", "교육철학", "철학"));
      case "sec-minilectures" -> names.addAll(List.of("미니강의", "미니특강"));
      case "sec-lessons" -> names.addAll(List.of("커리큘럼", "수업상품", "수업안내", "수업섹션"));
      case "sec-cta" -> names.addAll(List.of("상담신청", "상담버튼", "상담섹션"));
      case "sec-social" -> names.addAll(List.of("블로그", "지도", "외부연동"));
      case "sec-featured" -> names.addAll(List.of("시즌프로그램", "시즌", "특강프로그램"));
      case "sec-digitaltwin" -> names.addAll(List.of("AI교육", "AI과정"));
      default -> { }
    }
    return names;
  }

  private static boolean containsAny(String text, String... words) {
    for (String w : words) if (text.contains(w)) return true;
    return false;
  }
}
