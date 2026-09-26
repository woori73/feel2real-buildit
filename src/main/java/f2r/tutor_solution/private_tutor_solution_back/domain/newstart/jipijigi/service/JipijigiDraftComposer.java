package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 질문 답변 원문을 §8 고정 템플릿으로 조립한다.
 * LLM 산문 생성은 V1 범위 밖: 저장된 근거만 인용해 사람이 검증 가능한 형태로 만든다.
 * (운영자 검토 단계에서 답변 문장을 보강한 뒤 승인한다.)
 */
@Service
public class JipijigiDraftComposer {

    public static final String AI_DISCLAIMER =
            "이 글은 지피지기 Agent가 자료를 조사·정리하고, 카페 운영자 지피지기가 확인하여 게시합니다.";

    public static final String NO_PREDICTION_NOTICE =
            "감면율·승인 여부·법원 판단·채무조정 결과는 확정적으로 예측하지 않습니다. "
                    + "아래 사례는 실제 결과의 보장이 아니라 참고용입니다.";

    public String composeAnswerTitle(JipijigiQuestion q) {
        String t = q.getTitle();
        if (t.length() > 60) t = t.substring(0, 60) + "…";
        return "지피지기 Agent가 답합니다 — " + t;
    }

    public String composeAnswerBody(ResearchPack pack, JipijigiQuestion q) {
        StringBuilder sb = new StringBuilder();
        sb.append("[질문 요약]\n");
        sb.append(q != null ? summarizeQuestion(q) : firstLine(pack.getQuestion())).append("\n\n");

        sb.append("[지피지기 Agent 답변]\n");
        sb.append(answerCore(pack)).append("\n\n");

        sb.append("[공식 기준]\n").append(section(pack.getOfficialHits(), "게시된 공식 자료(새출발기금/신복위/금융위 등 저장본)")).append("\n");
        sb.append("[우리 DB에서 찾은 관련 자료]\n").append(section(pack.getInternalHits(), "NewStart 지식베이스 ACTIVE 항목")).append("\n");

        sb.append("[비슷한 사례]\n");
        sb.append("- 신복위 카페/공식자료 계열:\n").append(section(pack.getCcrsHits(), null));
        sb.append("- 부실차주 카페 사례:\n").append(section(pack.getBorrowerHits(), null)).append("\n");

        sb.append("[사례와 현재 질문의 차이]\n");
        sb.append("위 사례들은 채무액·가구 구성·소득·연령·신청 시점이 질문자와 동일하지 않을 수 있습니다. "
                + "하나의 사례를 일반적인 결과로 일반화하지 마세요.\n\n");

        sb.append("[추가로 확인할 사항]\n");
        if (pack.getGapNotes().isEmpty() && pack.getExtraAgencyHits().isEmpty()) {
            sb.append("- 없음\n");
        } else {
            pack.getGapNotes().forEach(g -> sb.append("- ").append(g).append("\n"));
            pack.getExtraAgencyHits().forEach(e -> sb.append("- ").append(e.getNote()).append("\n"));
        }
        sb.append("\n");

        sb.append("[관련 링크 / 출처]\n").append(links(pack)).append("\n");

        sb.append("[주의사항]\n").append(NO_PREDICTION_NOTICE).append("\n\n");
        sb.append("---\n").append(AI_DISCLAIMER).append("\n");
        return sb.toString();
    }

    String answerCore(ResearchPack pack) {
        StringBuilder sb = new StringBuilder();
        if (!pack.getInternalHits().isEmpty()) {
            sb.append("NewStart 내부 DB에서 이 질문과 관련된 저장 자료를 ").append(pack.getInternalHits().size())
                    .append("건 확인했습니다. 아래 공식 기준·사례·출처는 모두 저장된 실제 자료이며, Agent가 새로 만든 내용이 아닙니다.\n");
        } else {
            sb.append("NewStart 내부 DB에 이 질문을 직접 뒷받침하는 ACTIVE 자료가 아직 부족합니다. "
                    + "확정 답변 없이 공식 기준과 가장 가까운 사례·추가 확인 사항만 정리합니다.\n");
        }
        if (pack.internalInsufficient()) {
            sb.append("내부 근거가 부족해 외부 사례조사(신복위 카페 우선) 기록을 아래에 남겼습니다.\n");
        }
        return sb.toString();
    }

    String section(List<Evidence> list, String emptyHint) {
        if (list == null || list.isEmpty()) {
            return "  (해당 없음" + (emptyHint != null ? " — " + emptyHint : "") + ")\n";
        }
        StringBuilder sb = new StringBuilder();
        for (Evidence e : list) {
            sb.append("  * [").append(e.getSourceClass()).append("] ").append(e.getTitle());
            if (e.getExcerpt() != null && !e.getExcerpt().isBlank()) {
                sb.append(" — ").append(e.getExcerpt().replace("\n", " "));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    String links(ResearchPack pack) {
        StringBuilder sb = new StringBuilder();
        appendLinks(sb, pack.getInternalHits());
        appendLinks(sb, pack.getCcrsHits());
        appendLinks(sb, pack.getBorrowerHits());
        appendLinks(sb, pack.getOfficialHits());
        if (sb.length() == 0) return "  (저장된 원문 링크 없음)\n";
        return sb.toString();
    }

    private void appendLinks(StringBuilder sb, List<Evidence> list) {
        for (Evidence e : list) {
            if (e.getRefUrl() != null && !e.getRefUrl().isBlank()) {
                sb.append("  - ").append(e.getSourceClass()).append(": ").append(e.getRefUrl()).append("\n");
            }
        }
    }

    String summarizeQuestion(JipijigiQuestion q) {
        if (q.getBodyStatus() == JipijigiQuestion.BodyStatus.UNREADABLE || q.getBody() == null || q.getBody().isBlank()) {
            return q.getTitle() + " (본문 UNREADABLE — 수집 시 원문을 읽지 못해 제목 기준만 정리)";
        }
        return firstLine(q.getBody());
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String line = s.strip().split("\\R", 2)[0];
        return line.length() > 200 ? line.substring(0, 200) + "…" : line;
    }
}
