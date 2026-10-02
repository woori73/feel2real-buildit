package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A to Z 단계별 "이 단계의 실제 사례" 목록(설계 §3) — <b>읽기 전용 표시 계층</b>.
 *
 * <p>운영 DB({@code newstart_knowledge_items})의 ACTIVE 항목 중 전자책·카페·상담·운영 경험·
 * 확인된 사례 출처만 읽어, <b>결정적 키워드 매핑</b>으로 14개 A to Z 단계에 그룹핑한다.
 * AI 판단·재분류·DB write는 일절 없다(§10: DB 원본 훼손·AI 재분류 금지). 매핑은 표시용일 뿐
 * 원본 row의 category/status를 바꾸지 않는다.
 *
 * <p>단계 키워드 표는 A to Z 템플릿의 실제 단계명/순서(SOURCE OF TRUTH)를 그대로 따른다.
 * 새 단계를 만들지 않고, 기존 14단계(0..13)에만 항목을 걸친다.
 */
@Service
public class NewStartAtozCaseService {

  /** A to Z 단계 개수 — 템플릿 step-0..step-13과 일치(§2: 단계는 창작 금지). */
  static final int STEP_COUNT = 14;
  /** 단계당 노출 사례 상한 — 카드 과적 금지(§5). */
  static final int MAX_PER_STEP = 6;

  /** 사례로 노출할 출처(전자책·카페·상담·운영 경험·확인된 사례). 공식 규칙/도출 규칙은 사례가 아니다. */
  static final Set<SourceType> CASE_SOURCE_TYPES = Set.of(
      SourceType.EBOOK, SourceType.CAFE_ARTICLE, SourceType.CAFE_COMMENT, SourceType.CASE,
      SourceType.OPERATOR_KNOWLEDGE, SourceType.AUDIT,
      SourceType.CHAT_DISCOVERY, SourceType.REQUEST_DISCOVERY);

  /**
   * 단계(0..13) → 키워드. A to Z 실제 단계명·본문 어휘에서 뽑았으며, 소문자 비교한다.
   * 순서는 우선순위가 아니다 — 가장 많이 일치한 단계로 배정하고 동점이면 낮은 번호가 이긴다.
   */
  private static final List<String[]> STEP_KEYWORDS = List.of(
      /* 0 협약기관 */ new String[] {"협약", "비협약", "협약기관", "협약금융", "가입 채권"},
      /* 1 연체·추심 */ new String[] {"추심", "독촉", "압류", "가압류", "경매", "강제집행", "금감원", "민원", "연체 시작", "위법추심"},
      /* 2 지급명령·전자소송 */ new String[] {"지급명령", "전자소송", "이의신청", "법원", "소송", "독촉절차", "전자송달", "송달"},
      /* 3 Credit4U·장기연체 */ new String[] {"credit4u", "장기연체", "신용정보", "신용도판단", "kcredit", "채권자 변동"},
      /* 4 신청 자격·공식 신청 */ new String[] {"신청 자격", "신청방법", "신청 방법", "접수", "준비물", "자격조회", "신청자격", "부실차주 신청"},
      /* 5 원장 초기화 */ new String[] {"원장", "초기화", "조회기록", "원장정리"},
      /* 6 지원 가능한 대출 */ new String[] {"대상 채무", "지원대상", "지원 대상", "지원 가능한 대출", "15억", "대상채무"},
      /* 7 부실우려→부실차주 */ new String[] {"부실우려", "실효", "중개형", "부실차주 전환", "약정 실효"},
      /* 8 부동산 담보 */ new String[] {"부동산", "주택", "담보", "후순위", "근저당", "상가", "선순위"},
      /* 9 차량 담보 */ new String[] {"차량", "자동차", "승합차", "할부", "공매", "차량담보"},
      /* 10 임시안→확정안 */ new String[] {"임시안", "확정안", "감면율", "심사", "재산심사", "도덕적 해이", "재산은닉", "명의이전"},
      /* 11 교육·추가감면 */ new String[] {"교육", "추가감면", "재취업", "재창업", "희망리턴", "국민취업", "폴리텍", "iap", "이수증", "멘토"},
      /* 12 성실상환 이후 */ new String[] {"성실상환", "인센티브", "조기상환", "재기지원", "전환보증", "다시서기", "재도전", "상환유예"},
      /* 13 신청결과·진행상태 */ new String[] {"신청결과", "진행상태", "결과조회", "신청 결과", "진행 상태"});

  /** 기계 직렬화 제목 접두("[사례] 5866 ...", "[전자책] EBK-9-1 ... :: ...")를 사람 제목으로 정리. */
  private static final Pattern MACHINE_PREFIX =
      Pattern.compile("^\\s*\\[(?:사례|전자책|카페[^\\]]*|운영[^\\]]*)\\]\\s*");
  /**
   * 앞의 순수 숫자 id 또는 "EBK-9-1" 형태의 ALL-CAPS 구분자 id만 벗긴다.
   * "Credit4U" 같은 혼합 대소문자 단어는 갉아먹지 않도록, 구분자(-._)를 포함하는 대문자·숫자
   * 연속일 때만 id로 본다.
   */
  private static final Pattern LEADING_ID =
      Pattern.compile("^\\s*(?:\\d+|[A-Z0-9]+(?:[-._][A-Z0-9]+)+)\\s*(?:::)?\\s*");

  private final NewstartKnowledgeItemRepository knowledgeRepository;

  public NewStartAtozCaseService(NewstartKnowledgeItemRepository knowledgeRepository) {
    this.knowledgeRepository = knowledgeRepository;
  }

  /**
   * 단계 번호(0..13) → 사례 목록. ACTIVE·비만료·사례 출처만, 결정적 키워드로 배정한다.
   * 항목이 없으면 빈 맵(템플릿이 섹션을 감춘다). 호출 실패는 빈 맵으로 안전 퇴피 — A to Z 본문 렌더를 막지 않는다.
   */
  @Transactional(readOnly = true)
  public Map<Integer, List<AtozCase>> casesByStep() {
    List<NewstartKnowledgeItem> active;
    try {
      active = knowledgeRepository.findAllActive();
    } catch (RuntimeException e) {
      return Map.of();
    }
    if (active == null || active.isEmpty()) return Map.of();

    LocalDate today = LocalDate.now();
    Map<Integer, List<Scored>> buckets = new LinkedHashMap<>();
    for (NewstartKnowledgeItem item : active) {
      if (item.getStatus() != KnowledgeStatus.ACTIVE) continue;
      if (item.getSourceType() == null || !CASE_SOURCE_TYPES.contains(item.getSourceType())) continue;
      if (isExpired(item, today)) continue;
      int step = matchStep(item);
      if (step < 0) continue;
      buckets.computeIfAbsent(step, k -> new ArrayList<>()).add(new Scored(score(item), toCase(item)));
    }

    Map<Integer, List<AtozCase>> result = new LinkedHashMap<>();
    for (Map.Entry<Integer, List<Scored>> e : buckets.entrySet()) {
      List<AtozCase> cases = e.getValue().stream()
          .sorted(Comparator.comparingInt((Scored s) -> s.score).reversed())
          .limit(MAX_PER_STEP)
          .map(s -> s.caze)
          .toList();
      if (!cases.isEmpty()) result.put(e.getKey(), cases);
    }
    return result;
  }

  private static boolean isExpired(NewstartKnowledgeItem item, LocalDate today) {
    return item.getEffectiveTo() != null && item.getEffectiveTo().isBefore(today);
  }

  /** 항목 텍스트(제목·요약·태그·분류)에서 가장 많이 일치한 단계. 일치 0건이면 -1(어떤 단계에도 걸치지 않음). */
  static int matchStep(NewstartKnowledgeItem item) {
    String haystack = text(item).toLowerCase(Locale.ROOT);
    if (haystack.isBlank()) return -1;
    int best = -1;
    int bestHits = 0;
    for (int step = 0; step < STEP_KEYWORDS.size(); step++) {
      int hits = 0;
      for (String kw : STEP_KEYWORDS.get(step)) {
        if (haystack.contains(kw.toLowerCase(Locale.ROOT))) hits++;
      }
      if (hits > bestHits) {
        bestHits = hits;
        best = step;
      }
    }
    return bestHits == 0 ? -1 : best;
  }

  private static int score(NewstartKnowledgeItem item) {
    String haystack = text(item).toLowerCase(Locale.ROOT);
    int step = matchStep(item);
    if (step < 0) return 0;
    int hits = 0;
    for (String kw : STEP_KEYWORDS.get(step)) {
      if (haystack.contains(kw.toLowerCase(Locale.ROOT))) hits++;
    }
    return hits;
  }

  private static String text(NewstartKnowledgeItem item) {
    StringBuilder sb = new StringBuilder();
    append(sb, item.getTitle());
    append(sb, item.getSummary());
    append(sb, item.getTags());
    append(sb, item.getCategory());
    return sb.toString();
  }

  private static void append(StringBuilder sb, String v) {
    if (v != null && !v.isBlank()) sb.append(v).append(' ');
  }

  private static AtozCase toCase(NewstartKnowledgeItem item) {
    return new AtozCase(item.getKnowledgeId(), humanTitle(item.getTitle()), item.getSummary(),
        NewStartGalmuriService.labelOf(item.getSourceType()),
        NewStartGalmuriService.toneOf(item.getSourceType()),
        item.getSourceUrl());
  }

  /** 기계 직렬화 접두를 벗겨 사람이 읽는 사례 제목만 남긴다(§3 "사례 제목" 목록). */
  static String humanTitle(String raw) {
    if (raw == null) return "";
    String t = MACHINE_PREFIX.matcher(raw).replaceFirst("");
    t = LEADING_ID.matcher(t).replaceFirst("");
    t = t.strip();
    return t.isEmpty() ? raw.strip() : t;
  }

  private record Scored(int score, AtozCase caze) {}

  /**
   * @param title 사람용 사례 제목(기계 접두 제거). @param summary 짧은 요약(없을 수 있음).
   * @param label 출처 성격 라벨(갈무리와 동일 규범). @param tone official|operator|case.
   * @param url 원출처 링크(없을 수 있음).
   */
  public record AtozCase(Long id, String title, String summary, String label, String tone, String url) {}
}
