package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCampaignScope;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import java.util.List;
import java.util.Optional;

/** Signed TUTOR Wiki 서버-서버 경계. Python 응답에서는 body만 사용한다. */
public interface MidtermWikiSourceGateway {

  /**
   * signed tutor workspace 안에서 campaign year/semester/exam과 정확히 일치하는 eligible HTML 후보.
   * school/grade는 query authority가 아니라 upstream metadata다.
   */
  List<WikiSourceRow> listCandidates(AuthenticatedPrincipal tutor, WikiCampaignScope scope);

  Optional<byte[]> fetchHtml(AuthenticatedPrincipal tutor, String kind, long id);
}
