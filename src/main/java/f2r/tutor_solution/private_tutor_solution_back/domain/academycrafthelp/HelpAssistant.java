package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 도움말 근거만으로 답하는 읽기 전용 비서. LLM 호출·DB 조회·쓰기 실행이 없다(= 학원 데이터 접근 자체가 없어 다른 학원 데이터가 섞일 수 없다).
 * 근거가 임계값 미만이면 추측하지 않고 "근거 없음"으로 답한다.
 */
public class HelpAssistant {

  public record Source(String docId, String title, String link, String appliesTo, String lastVerified, String featureState) {}

  public record Answer(String intent, String text, List<Source> sources, boolean grounded, boolean writeBlocked) {}

  static final double MIN_SCORE = 2.5;
  private static final java.util.Set<String> SUMMARY_CATEGORIES = java.util.Set.of("FAQ", "역할");
  private static final Pattern WRITE_REQUEST =
      Pattern.compile("(해\\s?줘|해주세요|대신|자동으로|삭제해|등록해|생성해|수정해|저장해)");
  private static final Pattern DATA_LOOKUP = Pattern.compile("(다른|타)\\s?학원|명단|전화번호|연락처|비밀번호|성적표|개인정보");
  private static final Pattern AI_WRITE_TOPIC = Pattern.compile("(AI|에이전트|agent|비서).*(승인|실행|쓰기|계획|미리보기)|쓰기 승인", Pattern.CASE_INSENSITIVE);

  private final HelpCatalog catalog;
  private final java.util.function.Function<String, HelpSearch> searchFactory;

  public HelpAssistant(HelpCatalog catalog) {
    this.catalog = catalog;
    this.searchFactory = role -> new HelpSearch(catalog.aiSources(role));
  }

  public Answer ask(String role, String question) {
    String q = question == null ? "" : question.strip();
    if (q.isEmpty()) {
      return new Answer("EMPTY", "질문을 입력해 주세요.", List.of(), false, false);
    }
    if (DATA_LOOKUP.matcher(q).find()) {
      return new Answer("DATA_LOOKUP", "도움말 AI 비서는 학원·학생 데이터를 조회하지 않습니다. 다른 학원 정보는 볼 수 없고, 우리 학원 정보는 각 화면에서 직접 확인해 주세요.",
          List.of(new Source("INDEX", "도움말 목록", "/help/academycraft", "", "", "")), false, false);
    }
    List<HelpDoc> docs = catalog.aiSources(role);
    String intent = detectIntent(q);
    if (docs.isEmpty()) {
      return noEvidence(intent, false);
    }
    // FAQ·역할 가이드는 요약본이라 제목이 질문과 겹쳐 1위가 되기 쉽다. 상세 기능 문서를 먼저 찾고, 근거가 부족할 때만 요약본을 쓴다.
    var detail = new HelpSearch(docs.stream().filter(d -> !SUMMARY_CATEGORIES.contains(d.category())).toList()).search(q, 3);
    var hits = !detail.isEmpty() && detail.get(0).score() >= MIN_SCORE ? detail : searchFactory.apply(role).search(q, 3);
    if (hits.isEmpty() || hits.get(0).score() < MIN_SCORE) {
      return noEvidence(intent, WRITE_REQUEST.matcher(q).find());
    }
    var best = hits.get(0).section();
    StringBuilder text = new StringBuilder();
    boolean writeAsk = WRITE_REQUEST.matcher(q).find();
    if (writeAsk) {
      text.append("저는 데이터를 직접 바꾸지 않고 사용법만 안내합니다. 아래 방법으로 직접 처리해 주세요.\n\n");
    }
    if (AI_WRITE_TOPIC.matcher(q).find()) {
      HelpDoc ai = docs.stream().filter(d -> d.slug().equals("ai-assistant")).findFirst().orElse(null);
      if (ai != null && !"OPERATING".equals(ai.featureState())) {
        text.append("[").append(ai.featureStateLabel()).append("] AI 쓰기 승인 기능은 운영 화면에서 사용할 수 없습니다.\n\n");
      }
    }
    if (!"OPERATING".equals(best.doc().featureState())) {
      text.append("[").append(best.doc().featureStateLabel()).append("] ");
    }
    text.append(best.heading()).append("\n").append(best.text().replace("**", ""));
    List<Source> sources = new ArrayList<>();
    for (var h : hits) {
      HelpDoc d = h.section().doc();
      if (sources.stream().noneMatch(s -> s.docId().equals(d.id()))) {
        sources.add(new Source(d.id(), d.title(), "/help/academycraft/" + d.slug(), d.appliesTo(), d.lastVerified(), d.featureStateLabel()));
      }
    }
    return new Answer(intent, text.toString(), sources, true, writeAsk);
  }

  private Answer noEvidence(String intent, boolean writeAsk) {
    String msg = "도움말에서 확인된 근거를 찾지 못했습니다. 추측해서 답하지 않습니다. 도움말 목록에서 직접 찾아보거나 관리자에게 문의해 주세요.";
    return new Answer(intent, msg, List.of(new Source("INDEX", "도움말 목록", "/help/academycraft", "", "", "")), false, writeAsk);
  }

  /** 질문 키워드로 주제를 분류한다(검색 보조·로그용). */
  static String detectIntent(String q) {
    String[][] map = {
        {"AI", "AI|에이전트|비서|승인"}, {"STUDENT", "학생"}, {"CLASS", "반|수업|시간표"},
        {"ATTENDANCE", "출결|출석|결석"}, {"CONSULT", "상담"}, {"NOTICE", "공지"}, {"TROUBLE", "오류|안 돼|안돼|403|로그인"}};
    for (String[] m : map) {
      if (Pattern.compile(m[1], Pattern.CASE_INSENSITIVE).matcher(q).find()) return m[0];
    }
    return "GENERAL";
  }
}
