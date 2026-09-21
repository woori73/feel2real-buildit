package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService.RagContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartKnowledgeContextService.SourceInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * NewStart AI 질문 — 기존 BuildIt Wiki AI Router를 재사용하는 RAG Q&A service.
 *
 * <p>질문을 그대로 router에 넘기지 않는다. {@link NewStartKnowledgeContextService}가 ACTIVE
 * Knowledge(OFFICIAL/EBOOK/CASE)만 조회해 근거 block을 조립하면, 그 block을 질문 앞에 붙여
 * router에 위임한다. Knowledge가 하나도 없어도(현재 production처럼 ACTIVE가 0건인 경우 포함)
 * "근거 없음"을 명시한 block이 항상 붙으므로 grounding 규칙은 그대로 유지된다.
 *
 * <p>모델 정책은 Wiki Router 단일 출처를 따른다. NewStart에 별도 모델 설정이나 model ID
 * hard-code는 두지 않는다.
 */
@Service
public class NewStartAiChatService {
  /** 질문 길이 상한. 과대 프롬프트 남용을 막는 최소한의 가드다. */
  static final int MAX_QUESTION_CHARS = 2000;
  /** 답변 출력 상한. Wiki Router의 max_output_chars로 그대로 위임된다. */
  static final int MAX_ANSWER_CHARS = 4000;

  private static final String SYSTEM_PROMPT =
      "너는 새출발기금·부실차주·채무조정 정보를 도우는 한국어 안내자다. 이해하기 쉬운 문장으로 답한다."
          + " 확인되지 않은 정책 내용을 확정적으로 말하지 않고, 모르는 내용은 모른다고 말한다."
          + " 개인 재무정보를 임의로 만들어내지 않으며, 중요한 판단은 관련 기관 안내를 확인하도록 안내한다."
          + " 카페 경험담이나 근거 없는 자료를 공식 정책처럼 말하지 않는다."
          + " 사용자 메시지 앞에 [근거 자료] block이 있으면 그 안의 내용만 근거로 답하고, 근거에 없는"
          + " 숫자·조건·결과를 지어내지 않는다. [OFFICIAL]은 공식 제도/기준, [EBOOK]은 운영자의 실무"
          + " 설명, [CASE]는 실제 차주 개별 사례로 구분해서 설명한다. [CASE] 내용은 그 사람의 결과일"
          + " 뿐이며 사용자에게 동일하게 적용된다고 단정하지 않는다. [근거 자료]에 근거가 없다고 나오면"
          + " 그 사실을 사용자에게 알리고, 근거 없이 확정적인 수치나 결과를 만들어내지 않는다.";

  private final WikiAiRouterClient wikiAiRouterClient;
  private final NewStartKnowledgeContextService contextService;

  public NewStartAiChatService(
      WikiAiRouterClient wikiAiRouterClient, NewStartKnowledgeContextService contextService) {
    this.wikiAiRouterClient = wikiAiRouterClient;
    this.contextService = contextService;
  }

  /**
   * 질문 하나에 ACTIVE Knowledge 근거를 붙여 Wiki AI Router로 전달하고 답변을 돌려준다. 실패는
   * 예외 없이 result-object로 보고한다.
   */
  public NewStartAiAnswer ask(String rawQuestion) {
    String question = rawQuestion == null ? "" : rawQuestion.strip();
    if (question.isEmpty()) {
      return NewStartAiAnswer.failed("질문을 입력해 주세요.");
    }
    if (question.length() > MAX_QUESTION_CHARS) {
      return NewStartAiAnswer.failed("질문이 너무 깁니다. " + MAX_QUESTION_CHARS + "자 이내로 입력해 주세요.");
    }

    RagContext context = contextService.retrieve(question);
    String prompt = contextService.toPromptBlock(context) + "\n\n[사용자 질문]\n" + question;

    WikiAiRouterClient.Completion completion =
        wikiAiRouterClient.complete(SYSTEM_PROMPT, prompt, MAX_ANSWER_CHARS);
    if (!completion.ok()) {
      return NewStartAiAnswer.failed(completion.errorMessage());
    }
    return new NewStartAiAnswer(
        true, completion.text(), null, completion.model(), contextService.toSources(context));
  }

  /**
   * 호출 결과. 성공 시 {@code answer}만 의미 있고 실패 시 항상 빈 문자열이며 {@code errorMessage}만
   * 의미 있다. {@code model}은 Wiki Router가 반환한 식별자(검증/로깅용)로, 실패 시 null이다.
   * {@code sources}는 답변에 실제로 근거로 쓰인 ACTIVE Knowledge 목록(성공 시에만 채워짐, 근거가
   * 없었다면 빈 리스트).
   */
  public record NewStartAiAnswer(
      boolean ok, String answer, String errorMessage, String model, List<SourceInfo> sources) {
    public static NewStartAiAnswer failed(String errorMessage) {
      return new NewStartAiAnswer(false, "", errorMessage, null, List.of());
    }
  }
}
