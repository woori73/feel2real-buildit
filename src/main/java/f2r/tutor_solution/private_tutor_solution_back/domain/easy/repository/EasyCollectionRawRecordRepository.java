package f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCollectionRawRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EasyCollectionRawRecordRepository
    extends JpaRepository<EasyCollectionRawRecord, Long> {

  Optional<EasyCollectionRawRecord> findByDedupeKey(String dedupeKey);

  List<EasyCollectionRawRecord> findAllByRun_RunIdOrderByRawIdAsc(Long runId);

  /** 화면 표시용: 실행의 첫 원본 1건(직접 로그인 수집의 기관 코드를 읽는다). */
  Optional<EasyCollectionRawRecord> findFirstByRun_RunIdOrderByRawIdAsc(Long runId);

  List<EasyCollectionRawRecord>
      findByBusiness_BusinessIdAndSourceOccurredOnBetweenOrderByRawIdDesc(
          Long businessId, LocalDate from, LocalDate to);

  /** 이 연결로 들어온 원본 거래 중 가장 늦은 거래일(수집이 어디까지 되었는지 보여 줄 때 쓴다). 없으면 null. */
  @org.springframework.data.jpa.repository.Query(
      "select max(r.sourceOccurredOn) from EasyCollectionRawRecord r where r.connection.connectionId = :connectionId")
  LocalDate findLatestOccurredOn(
      @org.springframework.data.repository.query.Param("connectionId") Long connectionId);
}
