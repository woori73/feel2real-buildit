package f2r.tutor_solution.private_tutor_solution_back.domain.source.entity;

public enum SourceType {
  STUDENT_MATERIAL,
  HOMEWORK_PROBLEM,
  HOMEWORK_ANSWER,
  /** 강사/Admin이 학생에게 올리는 '제출 불필요' 시험대비 참고자료. ContentKind=GENERAL 고정. */
  TUTOR_MATERIAL,
  /** 강사가 학생에게 돌려주는 채점/피드백 PDF. */
  HOMEWORK_FEEDBACK,
  /** 학생/강사/Admin이 conversation 오답노트에 수동 업로드한 사진/PDF. 학습자료 대시보드에는 노출하지 않음. */
  WRONG_NOTE,
  /** 강사/Admin이 conversation 대시보드에 직접 올리는 상담 피드백 자료. 숙제/시험 매칭 없음. 학생 R, 강사/Admin C·D. */
  CONSULTATION
}
