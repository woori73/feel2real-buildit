package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiExamContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import java.util.List;
import java.util.Optional;

/**
 * TUTOR Wiki(Python) 백엔드로 나가는 서버-서버 호출 경계. {@code academy 강사 신원}을 HMAC 서명해
 * 그 강사의 workspace 를 조회한다({@code BuildItWikiProxyController} 와 같은 서명 로직).
 *
 * <p>구현체는 Python 응답에서 <b>body 만</b> 취하고 응답 헤더는 버린다. 이 도메인 밖으로
 * storageKey / 원본 URL / download endpoint 를 넘기지 않는다.
 */
public interface MidtermWikiSourceGateway {

  /**
   * 정확히 이 컨텍스트({@code school, grade, year, semester, exam})에 속한 HTML 자료만.
   * Python 이 없거나 오류면 빈 목록(fail-closed).
   */
  List<WikiSourceRow> listByContext(AuthenticatedPrincipal tutor, WikiExamContext context);

  /**
   * 자료 1건의 HTML 본문 bytes. 404/빈 본문 → {@link Optional#empty()}.
   *
   * @param kind {@code "school-print"} 또는 {@code "final"}
   * @throws org.springframework.web.server.ResponseStatusException 전송 실패/타임아웃/5xx 시 502·504
   */
  Optional<byte[]> fetchHtml(AuthenticatedPrincipal tutor, String kind, long id);
}
