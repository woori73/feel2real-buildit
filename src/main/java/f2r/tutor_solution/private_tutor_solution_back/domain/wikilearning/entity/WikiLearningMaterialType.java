package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 모의고사 학습 자료 종류: 어휘 / Workbook / 변형문제 / 기타. */
public enum WikiLearningMaterialType {
  VOCAB("어휘"),
  WORKBOOK("Workbook"),
  VARIANT("변형문제"),
  ETC("기타");

  private final String label;

  WikiLearningMaterialType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** 대소문자 무시 파싱. 알 수 없으면 400. */
  public static WikiLearningMaterialType parse(String value) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자료 종류를 선택해주세요.");
    }
    try {
      return WikiLearningMaterialType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 자료 종류입니다: " + value);
    }
  }
}
