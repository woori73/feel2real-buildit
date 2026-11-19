package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.TestPrepService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채점 참고용 정답·해설(2026-11-19 사용자 지시): 홈페이지 「디지털 내신문제」(/naesintest, tp_exams kind=NAESIN)의 문항별 정답·해설을
 * AI가 읽는다. 채점할 때 정답지 사진이 없으면 이것과 내신 Wiki 자료에서 같은 문항을 찾아 대조한다. 관리자 전용·읽기 전용.
 */
@Service
public class CoordAnswerKeyService {

  static final String[] CIRCLED = {"①", "②", "③", "④", "⑤"};
  static final int EXPLANATION_MAX = 600;

  private final TpExamRepository exams;
  private final TpQuestionRepository questions;

  public CoordAnswerKeyService(TpExamRepository exams, TpQuestionRepository questions) {
    this.exams = exams;
    this.questions = questions;
  }

  /** examCode가 없으면 공개된 디지털 내신 시험 목록(query로 제목 거르기), 있으면 그 시험의 문항별 정답·해설. */
  @Transactional(readOnly = true)
  public Map<String, Object> lookup(AuthenticatedPrincipal p, String examCode, String query) {
    CoordinatorService.requireAdmin(p);
    Map<String, Object> out = new LinkedHashMap<>();
    if (examCode == null || examCode.isBlank()) {
      String q = query == null ? "" : query.strip();
      List<Map<String, Object>> list = new ArrayList<>();
      for (TpExam e : exams.findByKindAndStatusOrderByExamIdAsc(TestPrepService.KIND_NAESIN, "PUBLISHED")) {
        if (!q.isEmpty() && !(e.getTitle() + " " + (e.getSubtitle() == null ? "" : e.getSubtitle())).contains(q)) continue;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examCode", e.getExamCode());
        m.put("title", e.getTitle());
        m.put("subtitle", e.getSubtitle());
        m.put("questionCount", questions.countByExamId(e.getExamId()));
        list.add(m);
      }
      out.put("exams", list);
      out.put("note", "채점할 시험과 같은 학교·학년·범위의 examCode로 다시 부르면 문항별 정답·해설을 줍니다.");
      return out;
    }
    TpExam e = exams.findByExamCodeAndStatus(examCode.strip(), "PUBLISHED")
        .filter(x -> TestPrepService.KIND_NAESIN.equals(x.getKind()))
        .orElseThrow(() -> new IllegalArgumentException("디지털 내신 시험을 찾을 수 없습니다: " + examCode));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (TpQuestion q : questions.findByExamIdOrderByOrderNoAsc(e.getExamId())) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("no", q.getOrderNo());
      m.put("answer", circled(q.getAnswerKey()));
      m.put("points", q.getPoints());
      String ex = q.getExplanation();
      m.put("explanation", ex == null ? null : ex.length() > EXPLANATION_MAX ? ex.substring(0, EXPLANATION_MAX) + "…" : ex);
      rows.add(m);
    }
    out.put("examCode", e.getExamCode());
    out.put("title", e.getTitle());
    out.put("subtitle", e.getSubtitle());
    out.put("questions", rows);
    out.put("note", "답지 문항 번호와 이 시험 문항 번호가 같은지(같은 회차·같은 문제지인지) 먼저 확인한 뒤 대조하세요.");
    return out;
  }

  /** "B" → "②", "A,C" → "①,③". 객관식 글자가 아니면 그대로(서술형 정답 등). */
  static String circled(String key) {
    if (key == null || key.isBlank()) return key;
    List<String> parts = new ArrayList<>();
    for (String k : key.split("[,\\s]+")) {
      String t = k.strip().toUpperCase(Locale.ROOT);
      if (t.length() == 1 && t.charAt(0) >= 'A' && t.charAt(0) <= 'E') parts.add(CIRCLED[t.charAt(0) - 'A']);
      else return key;
    }
    return String.join(",", parts);
  }
}
