package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 질문에서 "핵심 걱정 · 진행 단계 · 우선 검색할 주제"를 규칙으로 뽑는다(LLM 호출 없음, 결정적).
 *
 * <p>목적은 지피지기 답변 순서를 바꾸는 것이 아니다 — 운영원칙 그대로 <i>질문 의도 → 운영자 경험·전자책 → 사례 → 현재 공식자료</i>
 * 순서를 지키되, ① 불안이 큰 질문에서 규정 설명보다 "지금 무엇을 걱정해야 하고 무엇은 아직 아닌지"가 먼저 나오게 하고
 * ② 질문이 가리키는 실제 진행 주제(예: 원장초기화 → Credit4U 장기연체 → 부실차주)를 retrieval 키워드로 확장하며
 * ③ STEP 2가 질문과 직접 관련된 공식자료만 쓰게 하는 기준(focus terms)을 제공한다.
 *
 * <p>특정 게시글에 맞춘 하드코딩이 아니다: 아래 규칙표는 주제(압류·추심 / 원장정리 / 대부업 협약 / 준비 단계)별 일반 규칙이고,
 * 신호가 없는 질문에서는 아무 것도 붙이지 않는다(무관한 규정·키워드를 "안전을 위해" 덧붙이지 않는다).
 */
public final class JipijigiQuestionIntent {

  /** 불안 표현. 이 중 하나라도 있으면 STEP 1 첫 부분은 규정 설명이 아니라 "지금의 위험도 판단"이 된다. */
  private static final Pattern ANXIETY = Pattern.compile(
      "압류될|압류가|집이 걱정|걱정됩니다|걱정이|너무 걱정|추심이 무섭|무섭|막막|어떻게 해야 할지 모르|어떡해|불안|그 사이.{0,10}문제가 생|문제가 생길까|잘못될까");

  /** 주제 규칙: 단서 → (걱정 문장, 진행 단계 단서, 검색 확장 키워드). */
  private record Topic(String id, Pattern trigger, String concern, String stage, List<String> expansions) {}

  private static final List<Topic> TOPICS = List.of(
      new Topic("COLLECTION_SEIZURE",
          Pattern.compile("압류|가압류|추심|독촉|경매|강제집행"),
          "추심·압류가 들어와 재산(집·급여·통장 등)에 문제가 생길지",
          null,
          List.of("추심", "압류", "금감원", "민원")),
      new Topic("LEDGER_RESET",
          Pattern.compile("원장\\s?초기화|원장\\s?정리|회생\\s?폐지|파산\\s?폐지|면책\\s?취소"),
          null,
          "회생·파산 폐지 후 원장정리(원장초기화)를 거쳐 장기연체 등록·부실차주 신청으로 넘어가는 단계",
          List.of("원장초기화", "원장정리", "회생 폐지", "장기연체", "Credit4U", "부실차주")),
      new Topic("LENDER_COVERAGE",
          Pattern.compile("대부|캐피탈|저축은행|카드사|협약"),
          null,
          "채권사(대부·캐피탈 등)가 새출발기금 협약기관인지에 따라 처리 경로가 갈리는 단계",
          List.of("협약기관", "비협약", "개인워크아웃", "신복위")),
      new Topic("PREP_PERIOD",
          Pattern.compile("준비하는 동안|준비 중|준비중|신청 전|신청하기 전|접수 전"),
          null,
          "새출발기금 신청 준비 중(접수 전)",
          List.of()));

  /** 질문 어디에나 나오는 도메인 일반어 — focus term 에서 제외(이것만 겹치는 공식자료는 질문과 직접 관련이 없다). */
  private static final Set<String> GENERIC = Set.of(
      "새출발기금", "새출발", "부실차주", "채무", "채무조정", "신청", "준비", "진행", "문의", "질문", "대출", "감면", "지원", "관련",
      "궁금", "궁금합니다", "해야", "어떻게", "하는데", "있는데", "합니다", "입니다", "때문", "경우", "그런데", "그리고", "생길까", "있습니다",
      "하려고", "중입니다", "동안", "혹시", "정말", "너무", "집까지", "문제가", "봐", "안되면", "되면");

  private static final String[] PARTICLES = {
      "으로부터", "에서는", "이라고", "으로", "에서", "에게", "부터", "까지", "처럼", "인지", "이나", "이면", "라면", "하고",
      "를", "을", "은", "는", "이", "가", "의", "에", "도", "만", "로", "과", "와", "고", "며"};

  private static final JipijigiQuestionIntent NONE =
      new JipijigiQuestionIntent(false, List.of(), List.of(), List.of(), List.of(), List.of());

  private final boolean anxious;
  private final List<String> concerns;
  private final List<String> stages;
  private final List<String> expansions;
  private final List<String> focusTerms;
  /** 질문 본문에서 뽑은 토큰(주제 확장 제외) — 어간 일치는 이 용어에만 적용한다. */
  private final List<String> questionTerms;

  private JipijigiQuestionIntent(boolean anxious, List<String> concerns, List<String> stages, List<String> expansions,
      List<String> focusTerms, List<String> questionTerms) {
    this.anxious = anxious;
    this.concerns = concerns;
    this.stages = stages;
    this.expansions = expansions;
    this.focusTerms = focusTerms;
    this.questionTerms = questionTerms;
  }

  public static JipijigiQuestionIntent none() {
    return NONE;
  }

  public static JipijigiQuestionIntent analyze(String question) {
    if (question == null || question.isBlank()) return NONE;
    boolean anxious = ANXIETY.matcher(question).find();
    List<String> concerns = new ArrayList<>();
    List<String> stages = new ArrayList<>();
    Set<String> expansions = new LinkedHashSet<>();
    for (Topic t : TOPICS) {
      if (!t.trigger().matcher(question).find()) continue;
      if (t.concern() != null) concerns.add(t.concern());
      if (t.stage() != null) stages.add(t.stage());
      expansions.addAll(t.expansions());
    }
    List<String> questionTerms = tokens(question);
    Set<String> focus = new LinkedHashSet<>(questionTerms);
    focus.addAll(expansions);
    return new JipijigiQuestionIntent(anxious, List.copyOf(concerns), List.copyOf(stages), List.copyOf(expansions),
        List.copyOf(focus), questionTerms);
  }

  /** 불안 표현이 있는 질문인가. */
  public boolean anxious() {
    return anxious;
  }

  public List<String> concerns() {
    return concerns;
  }

  public List<String> stages() {
    return stages;
  }

  /** retrieval 에 추가할 키워드(질문이 해당 주제를 가리킬 때만 존재). */
  public List<String> expansions() {
    return expansions;
  }

  /** 질문과 직접 관련됨을 판정하는 용어(질문 토큰 + 주제 확장, 일반어 제외). */
  public List<String> focusTerms() {
    return focusTerms;
  }

  /** 프롬프트에 넣을 분석 블록이 필요한가(신호가 하나도 없으면 아무 것도 붙이지 않는다). */
  public boolean hasSignal() {
    return anxious || !concerns.isEmpty() || !stages.isEmpty();
  }

  /**
   * 근거 텍스트가 질문과 직접 관련된 정도(focus term 일치 수). 4글자 이상 용어는 앞 2글자 어간도 일치로 센다
   * (예: 질문 "담보대출" ↔ 자료 "담보채무", 질문 본문 용어에만 적용). 일반어는 focus term 에서 이미 빠져 있다.
   */
  public int relevance(String... fields) {
    StringBuilder sb = new StringBuilder();
    for (String f : fields) {
      if (f != null) sb.append(f).append(' ');
    }
    String text = sb.toString();
    int hits = 0;
    for (String term : focusTerms) {
      boolean stem = term.length() >= 4 && questionTerms.contains(term) && term.charAt(0) >= '가'
          && text.contains(term.substring(0, 2));
      if (text.contains(term) || stem) hits++;
    }
    return hits;
  }

  /** STEP 1 system prompt 에 붙는 분석 블록(규칙 기반 추정임을 밝히고, 불안 질문의 답변 목적을 지시한다). */
  public String promptBlock() {
    if (!hasSignal()) return "";
    StringBuilder sb = new StringBuilder("[질문 분석 — 규칙 기반 추정, 질문 원문으로 다시 확인할 것]\n");
    if (!concerns.isEmpty()) sb.append("- 질문자의 큰 걱정: ").append(String.join(" / ", concerns)).append('\n');
    if (!stages.isEmpty()) sb.append("- 현재 진행 단계 단서: ").append(String.join(" / ", stages)).append('\n');
    if (anxious) {
      sb.append("- 불안 표현이 있는 질문이다. 답변 첫 부분의 목적은 규정 설명이 아니라 '지금 무엇을 걱정해야 하고 무엇은 아직 걱정하지"
          + " 않아도 되는지'를 알려주는 것이다. 근거에 운영자 직접 경험이나 충분히 유사한 사례가 있을 때에만 그 경험에 근거해 말하고,"
          + " 근거가 없으면 위로하지 말고 확인 방법을 안내한다.\n");
    }
    return sb.toString();
  }

  private static List<String> tokens(String text) {
    Set<String> out = new LinkedHashSet<>();
    for (String raw : text.replaceAll("[^가-힣a-zA-Z0-9]", " ").split("\\s+")) {
      String t = stripParticle(raw);
      if (t.length() < 2 || GENERIC.contains(t) || GENERIC.contains(raw) || isQuestionEnding(t)) continue;
      out.add(t);
    }
    return List.copyOf(out);
  }

  private static final String[] QUESTION_ENDINGS = {"인가요", "하나요", "되나요", "나요", "까요", "세요", "어요", "아요", "습니까", "습니다", "는지", "할까"};

  /** '무엇인가요'처럼 질문 어미로 끝나는 말은 주제어가 아니다 — 이것만으로 공식자료 관련도를 판정하지 않는다. */
  private static boolean isQuestionEnding(String t) {
    for (String e : QUESTION_ENDINGS) {
      if (t.endsWith(e)) return true;
    }
    return false;
  }

  private static String stripParticle(String token) {
    for (String p : PARTICLES) {
      if (token.length() - p.length() >= 2 && token.endsWith(p)) return token.substring(0, token.length() - p.length());
    }
    return token;
  }
}
