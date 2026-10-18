package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

/** 문제 형식 (출제 의도 기준 분류). */
public enum GrammarQuestionFormat {
  MULTIPLE_CHOICE,      // 객관식
  CORRECT_SENTENCE,     // 어법상 옳은 문장
  INCORRECT_SENTENCE,   // 어법상 틀린 문장
  UNDERLINE,            // 밑줄 어법 판단
  BLANK_FILL,           // 빈칸 완성
  SENTENCE_ORDER,       // 문장 배열
  WRITING,              // 영작·서술형
  SENTENCE_TRANSFORM,   // 문장 전환
  ERROR_CORRECTION,     // 오류 수정
  OTHER;

  /** 추출 데이터의 자유 형식 문자열을 매핑한다. 매핑 실패 시 OTHER. */
  public static GrammarQuestionFormat fromRaw(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String v = raw.strip().toLowerCase();
    if (v.contains("객관") || v.contains("multiple") || v.contains("choice") || v.equals("mc")) {
      return MULTIPLE_CHOICE;
    }
    if (v.contains("옳은") || v.contains("바른") || v.contains("correct")) {
      return CORRECT_SENTENCE;
    }
    if (v.contains("틀린") || v.contains("incorrect") || v.contains("not")) {
      return INCORRECT_SENTENCE;
    }
    if (v.contains("밑줄") || v.contains("underline")) {
      return UNDERLINE;
    }
    if (v.contains("빈칸") || v.contains("blank")) {
      return BLANK_FILL;
    }
    if (v.contains("배열") || v.contains("order")) {
      return SENTENCE_ORDER;
    }
    if (v.contains("영작") || v.contains("서술") || v.contains("writing") || v.contains("short")) {
      return WRITING;
    }
    if (v.contains("전환") || v.contains("변환") || v.contains("transform") || v.contains("rewrite")) {
      return SENTENCE_TRANSFORM;
    }
    if (v.contains("오류") || v.contains("수정") || v.contains("error")) {
      return ERROR_CORRECTION;
    }
    return OTHER;
  }
}
