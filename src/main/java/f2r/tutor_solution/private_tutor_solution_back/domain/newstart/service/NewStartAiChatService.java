package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import org.springframework.stereotype.Service;

/**
 * NewStart AI 질문 — 기존 BuildIt Wiki AI Router를 그대로 재사용하는 Q&A service.
 *
 * <p>현재 NewStart에는 Knowledge DB/RAG가 없다. 그래서 이 service는 (1) NewStart 역할에 맞는
 * 최소 system instruction을 붙이고 (2) 공용 {@link WikiAiRouterClient}로 Wiki Router
 * (provider=auto → 현재 기본 모델)에 위임하는 일만 한다. 카페 사례·공식자료 검색을 하는 척하지
 * 않는다 — retrieval layer가 생기면 이 service의 prompt 조립 단계에 끼워 넣으면 된다.
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
          + " 사용자가 추심 피해(집중 전화, 욕설, 협박), 채권사 대응, 금감원 민원 작성이나 접수를"
          + " 언급하면 새출발기금 홈의 추심 대응 - 금감원 민원 도우미 화면(/newstart/fss-helper)을"
          + " 안내한다. 이 도우미에서 상황을 단계별로 정리하고 민원 초안과 증거 타임라인을 만들고"
          + " 금감원 접수 단계 안내를 받을 수 있다."
          + " 다만 민원 제출과 본인인증은 반드시 금융감독원 공식 사이트에서 사용자 본인이 직접 하며,"
          + " 위법 여부나 민원 결과는 단정하지 않는다.";

  private final WikiAiRouterClient wikiAiRouterClient;

  public NewStartAiChatService(WikiAiRouterClient wikiAiRouterClient) {
    this.wikiAiRouterClient = wikiAiRouterClient;
  }

  /** 질문 하나를 Wiki AI Router로 전달하고 답변을 돌려준다. 실패는 예외 없이 result-object로 보고한다. */
  public NewStartAiAnswer ask(String rawQuestion) {
    String question = rawQuestion == null ? "" : rawQuestion.strip();
    if (question.isEmpty()) {
      return NewStartAiAnswer.failed("질문을 입력해 주세요.");
    }
    if (question.length() > MAX_QUESTION_CHARS) {
      return NewStartAiAnswer.failed("질문이 너무 깁니다. " + MAX_QUESTION_CHARS + "자 이내로 입력해 주세요.");
    }
    WikiAiRouterClient.Completion completion =
        wikiAiRouterClient.complete(SYSTEM_PROMPT, question, MAX_ANSWER_CHARS);
    if (!completion.ok()) {
      return NewStartAiAnswer.failed(completion.errorMessage());
    }
    return new NewStartAiAnswer(true, completion.text(), null, completion.model());
  }

  /**
   * 호출 결과. 성공 시 {@code answer}만 의미 있고 실패 시 항상 빈 문자열이며 {@code errorMessage}만
   * 의미 있다. {@code model}은 Wiki Router가 반환한 식별자(검증/로깅용)로, 실패 시 null이다.
   */
  public record NewStartAiAnswer(boolean ok, String answer, String errorMessage, String model) {
    public static NewStartAiAnswer failed(String errorMessage) {
      return new NewStartAiAnswer(false, "", errorMessage, null);
    }
  }
}
