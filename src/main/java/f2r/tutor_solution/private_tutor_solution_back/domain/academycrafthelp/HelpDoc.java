package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import java.util.List;

/** 도움말 문서 1건. 메타데이터는 front matter에서, 본문은 markdown 그대로 보관한다. */
public record HelpDoc(
    String id,
    String slug,
    String title,
    String category,
    int order,
    String audience,
    String visibility,
    String status,
    String featureState,
    String appliesTo,
    String lastModified,
    String lastVerified,
    String source,
    List<String> related,
    String body) {

  /** 운영 상태 한글 라벨. 미구현 기능을 사용 가능한 것처럼 보이지 않게 화면·답변에 항상 붙인다. */
  public String featureStateLabel() {
    return switch (featureState) {
      case "OPERATING" -> "운영 중";
      case "DEV_DONE_UNDEPLOYED" -> "개발 완료·미배포";
      case "IN_DEVELOPMENT" -> "개발 중";
      case "NOT_IMPLEMENTED" -> "미구현";
      default -> "검증 필요";
    };
  }
}
