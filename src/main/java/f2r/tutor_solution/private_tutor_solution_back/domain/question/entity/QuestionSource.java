package f2r.tutor_solution.private_tutor_solution_back.domain.question.entity;

/**
 * 문제(question)의 출처. 1차 단계에서는 {@link #READFILE} 하나만 사용된다.
 *
 * <ul>
 *   <li>{@link #READFILE} — 시험지/문제지 파일을 LLM이 읽고 추출한 문제.
 *       1차 작업의 {@code SourceAnalysisService.insertQuestions}에서 INSERT.</li>
 *   <li>{@link #GENERATED} — LLM이 학생 자료를 참고해서 "새로 만들어낸" 문제 (예: 진단 세션, 유사 문제).
 *       1차에서는 미사용. 후속 단계에서 진단 흐름 도입 시 사용.</li>
 *   <li>{@link #MANUAL} — Admin/강사가 폼에서 직접 입력한 문제. 후속 단계에서 사용.</li>
 * </ul>
 */
public enum QuestionSource {
  READFILE,
  GENERATED,
  MANUAL
}
