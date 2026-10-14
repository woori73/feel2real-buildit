package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.EducatorReference;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BuildIt Chart에 공개된 교육자 카드 중 학습 가능한 59건(retrievable.json)에서만 사례를 찾는
 * 정적 검색 계층. DB·외부 호출·주 단위 자동 갱신 없이 <b>배포 시점에 고정된 classpath 리소스</b>를
 * 한 번 읽는다(순위 기준일 rankAsOf·수집일 data/chart-educators/retrievable.json에 기록).
 *
 * <p>검색 결과는 어디까지나 <b>다른 교육자의 참고 사례</b>다. 의뢰 학원의 사실로 채택되는 경로는
 * 사용자 승인({@code AcademyAiProposalService} 통과 → Builder 적용)뿐이며, 프롬프트 단계의
 * 인용 금지({@link AcademyAiPromptBuilder})와 target allowlist가 이중 방어한다.
 *
 * <p>리소스 손상·무검색 시 항상 빈 목록을 돌려주며 예외를 던지지 않는다 — 기존 홈페이지
 * 제작 기능은 사례 없이도 이전과 똑같이 동작해야 한다. 순수 로직이라 컨테이너 없이 테스트한다.
 */
@Service
public class EducatorKnowledgeService {

  static final String RESOURCE = "data/chart-educators/retrievable.json";
  static final int DEFAULT_LIMIT = 3;

  private static final Logger log = LoggerFactory.getLogger(EducatorKnowledgeService.class);
  private static final int TAG_SCORE = 3;
  private static final int MAX_TOKENS = 40;
  /**
   * 태그·지역 신호 없이 원문 키워드만일 때의 최저 일치 수. 영어학원 사례 풀 특성상
   * "영어·수업·커리큘럼" 같은 일반 어휘만 겹치는 무관 업종(요리·제과 등)의 요청을 배제한다.
   */
  private static final int KEYWORD_ONLY_MIN_HITS = 4;

  /** 검색 문구에 포함된 키워드 → Chart 카드 분류 태그(수집 프로젝트의 TRIGGER 매핑과 동일). */
  private static final Map<String, String> TAG_TRIGGERS = buildTagTriggers();

  private static Map<String, String> buildTagTriggers() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("말하기", "communication_first");
    m.put("회화", "communication_first");
    m.put("발화", "communication_first");
    m.put("암기", "anti_roterism");
    m.put("정답", "anti_roterism");
    m.put("실력", "anti_roterism");
    m.put("습관", "habit_structure");
    m.put("반복", "habit_structure");
    m.put("시스템", "habit_structure");
    m.put("자기주도", "student_agency");
    m.put("스스로", "student_agency");
    m.put("소수", "individual_custom");
    m.put("1:1", "individual_custom");
    m.put("개별", "individual_custom");
    m.put("원서", "input_output_loop");
    m.put("파닉스", "input_output_loop");
    m.put("쉐도잉", "input_output_loop");
    m.put("내신", "results_public");
    m.put("수능", "results_public");
    m.put("적중", "results_public");
    m.put("직강", "solo_direct_teaching");
    m.put("원장", "solo_direct_teaching");
    m.put("1인", "solo_direct_teaching");
    m.put("학부모", "parent_communication");
    m.put("상담", "parent_communication");
    m.put("AI", "ai_edsuptech");
    m.put("프로그램", "named_programs");
    m.put("체험", "named_programs");
    m.put("지역", "regional_mission");
    m.put("설명회", "parent_communication");
    m.put("테스트", "diagnose_placement");
    m.put("레벨", "diagnose_placement");
    m.put("단계", "stage_pathway");
    m.put("로드맵", "stage_pathway");
    m.put("교재", "own_materials");
    m.put("기출", "own_materials");
    m.put("즐거", "mind_growth");
    m.put("흥미", "mind_growth");
    m.put("자신감", "mind_growth");
    m.put("태도", "mind_growth");
    return Map.copyOf(m);
  }

  /** retrievable.json의 한 카드. 원문 필드만 담고 판정(tag)은 수집 시점에 이미 고정되어 있다. */
  record Card(long id, String region, String province, List<String> tags, EducatorReference reference) {

    String searchText() {
      return String.join(
          " ", nullToEmpty(reference.philosophy()),
          nullToEmpty(reference.curriculum()), nullToEmpty(reference.teaching()));
    }

    private static String nullToEmpty(String s) {
      return s == null ? "" : s;
    }
  }

  private final List<Card> pool;

  public EducatorKnowledgeService() {
    this(loadFromResource());
  }

  EducatorKnowledgeService(List<Card> pool) {
    this.pool = List.copyOf(pool);
  }

  /** 검색 가능한 사례 건수(배포 데이터 검증용). */
  public int poolSize() {
    return pool.size();
  }

  /**
   * 현재 Academy 컨텍스트 + 사용자 입력 문구에서 유사 사례를 최대 3건 찾는다.
   * 점수: 태그 일치 3점/개, 지역(도) 일치 +2, 지역(시군구) 일치 +3, 카드 원문 키워드 일치 1점/개.
   * 동점 id 오름차순 — 항상 결정적. 검색 실패·데이터 없음은 항상 빈 목록.
   */
  public List<EducatorReference> search(AcademyAiContext context, String instructions) {
    return search(queryText(context, instructions), DEFAULT_LIMIT);
  }

  List<EducatorReference> search(String query, int limit) {
    if (pool.isEmpty() || query == null || query.isBlank()) {
      return List.of();
    }
    Set<String> wantTags = new LinkedHashSet<>();
    for (Map.Entry<String, String> trigger : TAG_TRIGGERS.entrySet()) {
      if (query.contains(trigger.getKey())) {
        wantTags.add(trigger.getValue());
      }
    }
    List<String> tokens = tokenize(query);

    List<Card> ranked = new ArrayList<>();
    Map<Long, Integer> scores = new LinkedHashMap<>();
    for (Card card : pool) {
      int score = 0;
      int tagHits = 0;
      boolean regionHit = false;
      int kwHits = 0;
      for (String tag : card.tags()) {
        if (wantTags.contains(tag)) {
          tagHits++;
          score += TAG_SCORE;
        }
      }
      if (card.province() != null && query.contains(card.province())) {
        score += 2;
        regionHit = true;
      }
      if (card.region() != null && query.contains(card.region())) {
        score += 3;
        regionHit = true;
      }
      String text = card.searchText();
      for (String token : tokens) {
        if (text.contains(token)) {
          kwHits++;
          score += 1;
        }
      }
      // 일반 어휘만 겹치는 무관 업종 요청은 사례로 끌어오지 않는다(태그·지역 또는 충분한 키워드 신호 필요).
      if (score > 0 && (tagHits > 0 || regionHit || kwHits >= KEYWORD_ONLY_MIN_HITS)) {
        ranked.add(card);
        scores.put(card.id(), score);
      }
    }
    ranked.sort(Comparator.comparingInt((Card c) -> -scores.get(c.id())).thenComparingLong(Card::id));
    return ranked.stream()
        .limit(Math.max(0, limit))
        .map(Card::reference)
        .toList();
  }

  private static String queryText(AcademyAiContext context, String instructions) {
    List<String> parts = new ArrayList<>();
    if (instructions != null && !instructions.isBlank()) {
      parts.add(instructions.trim());
    }
    if (context != null) {
      addIfPresent(parts, context.intro());
      addAllIfPresent(parts, context.subjects());
      addAllIfPresent(parts, context.targetGroups());
      addAllIfPresent(parts, context.philosophyItems());
      addIfPresent(parts, context.principalDesc());
    }
    return String.join(" ", parts);
  }

  private static void addIfPresent(List<String> parts, String value) {
    if (value != null && !value.isBlank()) parts.add(value.trim());
  }

  private static void addAllIfPresent(List<String> parts, List<String> values) {
    if (values == null) return;
    for (String v : values) addIfPresent(parts, v);
  }

  private static List<String> tokenize(String query) {
    List<String> tokens = new ArrayList<>();
    for (String raw : query.split("[\\s,/·()]+")) {
      String token = raw.trim();
      if (token.length() >= 2 && !tokens.contains(token)) {
        tokens.add(token);
        if (tokens.size() >= MAX_TOKENS) break;
      }
    }
    return tokens;
  }

  private static List<Card> loadFromResource() {
    try (InputStream in = EducatorKnowledgeService.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (in == null) {
        log.warn("Chart 교육자 지식 리소스가 없습니다 — 사례 검색 없이 기존 기능만 동작: {}", RESOURCE);
        return List.of();
      }
      return parse(new ObjectMapper().readTree(in));
    } catch (Exception e) {
      log.warn("Chart 교육자 지식을 불러오지 못했습니다 — 사례 검색 없이 기존 기능만 동작", e);
      return List.of();
    }
  }

  static List<Card> parse(JsonNode root) {
    List<Card> cards = new ArrayList<>();
    for (JsonNode n : root.path("educators")) {
      long id = n.path("id").asLong();
      String region = text(n, "region");
      List<String> tags = new ArrayList<>();
      for (JsonNode t : n.path("tags")) tags.add(t.asText());
      JsonNode card = n.path("card");
      EducatorReference reference = new EducatorReference(
          id, text(n, "name"), text(n, "role"), text(n, "academyName"), region,
          text(n, "rankAsOf"),
          text(card, "educationPhilosophy"), text(card, "curriculum"), text(card, "distinctiveTeaching"),
          List.copyOf(tags), text(n, "sourceCardUrl"));
      cards.add(new Card(id, region, text(n, "province"), List.copyOf(tags), reference));
    }
    return List.copyOf(cards);
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isTextual() ? v.asText() : null;
  }
}
