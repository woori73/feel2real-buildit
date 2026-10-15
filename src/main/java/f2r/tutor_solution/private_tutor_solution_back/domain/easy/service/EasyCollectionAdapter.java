package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.CollectionParsedRow;
import java.util.List;

/**
 * 수집 어댑터 SPI — 외부 소스(파일/API/스크래핑 등)를 공통 파이프라인에 연결한다.
 * 구현체는 {@link EasyCollectionIngestService#ingestParsedRows}를 재사용해
 * 3단계 dedupe → raw 보존 → fingerprint 제외 → import staging 인계까지 동일하게 수행한다.
 * 절대 {@code easy_transactions}에 직접 INSERT하지 않는다.
 * 자동 collect ≠ 자동 confirm.
 */
public interface EasyCollectionAdapter {

    /**
     * 어댑터 고유 코드 (예: "CARDSales", "HOME_TAX", "BANK_CSV").
     * {@link EasyCollectionRun#adapterCode}에 기록돼 출처 추적에 쓰인다.
     */
    String adapterCode();

    /**
     * 어댑터 버전 (예: "v1", "2026.09"). 런 기록에 저장돼 호환성 판단 근거가 된다.
     */
    String adapterVersion();

    /**
     * 연결/인증에 필요한 설정 키 목록 (예: clientId, merchantId, connectedAccountId).
     * 값은 저장하지 않고 키 이름만 노출해 프론트가 입력 폼을 구성하도록 한다.
     * 실제 값은 {@link EasyCollectionConnection#connectionRef}에 opaque reference로만 보관.
     */
    List<String> configKeys();

    /**
     * 외부 소스에서 원본 레코드를 수집해 정규화 전 {@link CollectionParsedRow} 리스트로 변환한다.
     * 구현체는 API 호출·파일 파싱·스크래핑 등 구체 수단을 담당한다.
     * 동일 레코드 재수집 시 {@link CollectionParsedRow#externalId()}가 같으면
     * 1차 dedupe(EXT 키)에 의해 중복 제외된다.
     *
     * @param connectionRef {@link EasyCollectionConnection#connectionRef}에 저장된 opaque reference
     * @param since         증분 수집 기준 시점 (null이면 전체)
     * @param limit         최대 수집 건수 (하드 캡, 초과 시 예외)
     * @return 정규화 전 파싱 행 리스트 (error 필드로 실패 행 구분, 원본은 rawJson에 보존)
     */
    List<CollectionParsedRow> fetch(String connectionRef, java.time.LocalDateTime since, int limit);

    /**
     * 연결 유효성 검증 — 저장된 connectionRef로 실제 접근 가능 여부만 확인.
     * 네트워크 호출을 최소화(HEAD/ping 수준)하고 예외를 삼키지 않고 던진다.
     */
    void validateConnection(String connectionRef);
}