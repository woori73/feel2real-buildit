package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기간 단위로 조회하는 기관 어댑터(은행 거래내역 등). 수집 서비스가 [from, to]를 기관 문서의 1회 최대
 * 조회 기간으로 나눠 구간마다 호출하고, 구간의 장부 저장이 커밋된 뒤에만 커서를 그 구간 끝으로 옮긴다.
 *
 * <p>구현체는 외부 호출만 담당하고 DB에 쓰지 않는다. 자격증명(비밀번호·인증서·OTP·API 비밀키)은 인자로
 * 받지 않는다 — {@code connectionRef}는 기관 연결 참조자(connectedId 등), {@code providerConfig}는
 * 기관코드·계좌번호 같은 비민감 조회 조건 JSON이다.
 */
public interface EasyPeriodCollectionAdapter extends EasyCollectionAdapter {

  /** 1회 조회 최대 일수(기관 문서 기준). */
  int maxPeriodDays();

  /** 처음 수집할 때 오늘부터 거슬러 올라갈 일수. */
  int initialLookbackDays();

  /**
   * [from, to] 구간을 조회한다. 결과 행의 externalId는 기관이 준 고유번호가 있으면 그 값을, 없으면
   * 거래를 구분할 수 있는 원문 필드(일시·금액·거래 후 잔액 등)로 만든 결정적 키여야 한다.
   */
  List<CollectionParsedRow> fetchPeriod(String connectionRef, String providerConfig,
      LocalDate from, LocalDate to, int limit);

  /** 기간 어댑터는 {@link #fetchPeriod}만 쓴다. 기존 SPI 호출은 조건 없이 막는다. */
  @Override
  default List<CollectionParsedRow> fetch(String connectionRef, LocalDateTime since, int limit) {
    throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
        "Period adapters must be called through fetchPeriod.");
  }
}
