package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Top10 실제 차주 질문의 근거 충분성 검사 — ADMIN preview 수준.
 *
 * <p>AI가 답을 생성하지 않는다. REVIEW DB에 적재된 item + research-official evidence만으로
 * 각 질문이 ANSWERABLE / PARTIAL / UNKNOWN / CONFLICT 중 어디에 해당하는지 판정하고,
 * 매칭된 knowledge/evidence/sourceType을 함께 보여준다.
 */
public class NewstartTop10Answerability {

    public enum Verdict { ANSWERABLE, PARTIAL, UNKNOWN, CONFLICT }

    public record ItemView(String title, String tags, String relatedRuleIds, String sourceType) {}

    public record Answerability(String question, Verdict verdict,
            List<String> matchedKnowledge, List<String> matchedEvidence, List<String> sourceTypes) {}

    public static List<Answerability> check(JsonNode top10, List<ItemView> items) {
        List<Answerability> out = new ArrayList<>();
        JsonNode questions = top10.get("questions");
        if (questions == null || !questions.isArray()) {
            return out;
        }
        for (JsonNode q : questions) {
            String question = q.has("q") ? q.get("q").asText("") : "";
            List<String> needs = new ArrayList<>();
            if (q.has("needs") && q.get("needs").isArray()) {
                q.get("needs").forEach(n -> needs.add(n.asText("")));
            }
            List<String> matchedKnowledge = new ArrayList<>();
            List<String> matchedEvidence = new ArrayList<>();
            List<String> sourceTypes = new ArrayList<>();
            boolean conflict = false;
            int needsMet = 0;
            for (String need : needs) {
                boolean met = false;
                for (ItemView item : items) {
                    if (matches(item, need)) {
                        met = true;
                        if (!matchedKnowledge.contains(item.title())) {
                            matchedKnowledge.add(item.title());
                        }
                        if (!sourceTypes.contains(item.sourceType())) {
                            sourceTypes.add(item.sourceType());
                        }
                        if (item.tags() != null && item.tags().contains("CONFLICT_REVIEW")) {
                            conflict = true;
                        }
                    }
                }
                if (met) {
                    needsMet++;
                    matchedEvidence.add(need);
                }
            }
            Verdict verdict;
            if (needs.isEmpty() || needsMet == 0) {
                verdict = Verdict.UNKNOWN;
            } else if (conflict) {
                verdict = Verdict.CONFLICT;
            } else if (needsMet == needs.size()) {
                verdict = Verdict.ANSWERABLE;
            } else {
                verdict = Verdict.PARTIAL;
            }
            out.add(new Answerability(question, verdict, matchedKnowledge, matchedEvidence, sourceTypes));
        }
        return out;
    }

    static boolean matches(ItemView item, String need) {
        if (need == null || need.isEmpty()) {
            return false;
        }
        String hay = (item.title() == null ? "" : item.title()) + "\n"
                + (item.tags() == null ? "" : item.tags()) + "\n"
                + (item.relatedRuleIds() == null ? "" : item.relatedRuleIds());
        return hay.contains(need);
    }
}
