package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import java.time.LocalDateTime;
import java.util.List;

public interface OfficialUpdateDiscoveryAdapter {

  SourceType getSourceType();

  String getSourceName();

  /**
   * 공식 출처에서 최신 공고/보도자료 목록을 조회한다.
   *
   * @param since 이 시각 이후에 게시된 항목만 조회 (최초 실행 시 null 가능)
   * @return 발견된 후보 리스트
   */
  List<DiscoveredCandidate> discover(LocalDateTime since);

  /**
   * 발견된 후보를 기반으로 Official Update 엔티티 생성에 필요한 정보를 구성한다.
   * 상세 페이지 조회가 필요한 경우 여기서 수행한다.
   *
   * @param candidate 목록에서 발견된 후보
   * @return 저장 가능한 엔티티 정보 또는 null (관련 없음/파싱 실패 등)
   */
  DiscoveredCandidate enrich(DiscoveredCandidate candidate);

  /**
   * 후보의 관련성(priority)을 판정한다.
   *
   * @param candidate 상세 정보가 포함된 후보
   * @return 관련성 등급
   */
  RelevancePriority evaluateRelevance(DiscoveredCandidate candidate);

  /**
   * 후보가 이미 처리된 것인지(중복) 확인하기 위한 키를 생성한다.
   * officialDocumentId > normalized URL > sourceType+title+date 순위 사용.
   */
  String buildDedupKey(DiscoveredCandidate candidate);

  /**
   * 발견된 후보 정보
   */
  record DiscoveredCandidate(
      String title,
      String summary,
      String originalUrl,
      String officialDocumentId,
      LocalDateTime publishedAt,
      LocalDateTime effectiveFrom,
      LocalDateTime effectiveTo,
      NewStartOfficialUpdate.Category category,
      String detailHtml
  ) {}
}