package f2r.tutor_solution.private_tutor_solution_back.domain.source.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;

/**
 * Phase 9 — 파일 첨부 모달의 폼 데이터.
 *
 * <p>학생이 파일을 첨부할 때 자료 타입과 시험지인 경우의 메타 정보를 함께 전달한다.
 * 분류 LLM 호출을 제거하고 학생이 직접 자료 종류를 명시하도록 한 것이 핵심 변경.
 *
 * <ul>
 *   <li>contentKind: GENERAL / EXAM_BLANK (필수). 학생 폼의 "일반 자료" / "시험지" 라벨에 1:1 매핑.
 *       EXAM_GRADED는 학생이 직접 보낼 수 없고, 분석 중 채점 흔적 감지 시 서버가 promote한다.
 *       UNCLASSIFIED는 폼이 비어 들어왔을 때의 폴백.</li>
 *   <li>schoolName/grade: 학생 정보로 default가 채워져 들어오며, 학생이 수정할 수 있다.
 *       (학생 entity의 값을 그대로 쓰고 싶으면 null로 보내도 됨 — 서버에서 자동 보충)</li>
 *   <li>examType/examYear/examSemester: nullable. 비어 있는 필드는 LLM이 유추한다.</li>
 * </ul>
 *
 * <p>GENERAL일 때는 시험 관련 필드(examType 이하)가 무시된다.
 */
public record SourceUploadRequest(
    ContentKind contentKind,
    String schoolName,
    Byte grade,
    ExamType examType,
    Short examYear,
    Byte examSemester
) {
  /** 폼이 비어 들어왔을 때(예: 호환성을 위한 fallback)의 기본값. GENERAL로 간주. */
  public static SourceUploadRequest defaultGeneral() {
    return new SourceUploadRequest(ContentKind.GENERAL, null, null, null, null, null);
  }
}
