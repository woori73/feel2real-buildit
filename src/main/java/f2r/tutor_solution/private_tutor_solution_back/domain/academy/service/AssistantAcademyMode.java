package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAssistantMessageRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 학원이 'AI 제작 학원'인지(대화형 화면·대화 API 대상) 기존 방식 학원인지를 가른다.
 *
 * <p>대화 기록이 있다는 사실만으로는 판정하지 않는다. 대화 API를 직접 호출하면 수정·되돌리기 기록이 쌓이는데, 그것만으로
 * 기존 학원의 화면 모드가 바뀌어선 안 되기 때문이다. AI 제작 학원은 다음 둘 중 하나가 있는 학원이다.
 *
 * <ul>
 *   <li>{@link #KIND_RESULT}: 대화로 최초 생성했을 때 남는 결과 기록
 *   <li>{@link #KIND_ADOPTED}: 기존 학원에서 사용자가 'AI로 이어서 만들기'를 확인하고 전환했을 때 남는 기록
 * </ul>
 */
@Component
public class AssistantAcademyMode {

  public static final String KIND_RESULT = AcademyAssistantService.KIND_RESULT;
  public static final String KIND_ADOPTED = "ADOPTED";
  private static final List<String> ASSISTANT_KINDS = List.of(KIND_RESULT, KIND_ADOPTED);

  private final AcademyAssistantMessageRepository messages;

  public AssistantAcademyMode(AcademyAssistantMessageRepository messages) {
    this.messages = messages;
  }

  public boolean isAssistantAcademy(Long academyId) {
    return academyId != null && messages.existsByAcademyIdAndKindIn(academyId, ASSISTANT_KINDS);
  }
}
