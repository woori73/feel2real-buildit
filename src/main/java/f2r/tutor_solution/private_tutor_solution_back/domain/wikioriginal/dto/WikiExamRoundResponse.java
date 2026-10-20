package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamRound;
import java.time.LocalDateTime;

public record WikiExamRoundResponse(
    Long wikiExamRoundId,
    Integer examYear,
    Integer examMonth,
    String title,
    String subtitle,
    String bannerPath,
    boolean isCurrent,
    boolean isPublished,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
  public static WikiExamRoundResponse of(WikiExamRound round) {
    return new WikiExamRoundResponse(
        round.getWikiExamRoundId(),
        round.getExamYear(),
        round.getExamMonth(),
        round.getTitle(),
        round.getSubtitle(),
        round.getBannerPath(),
        round.isCurrent(),
        round.isPublished(),
        round.getCreatedAt(),
        round.getUpdatedAt()
    );
  }
}
