package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewStartThanksRosterRepository extends JpaRepository<NewStartThanksRoster, Long> {

  long countBySnapshotDate(LocalDate snapshotDate);

  long countByMatchStatus(String matchStatus);

  Optional<NewStartThanksRoster> findBySnapshotDateAndCafeMemberTag(LocalDate snapshotDate, String cafeMemberTag);

  List<NewStartThanksRoster> findBySnapshotDateOrderByRosterIdAsc(LocalDate snapshotDate);

  /** 닉네임 후보 조회(DB collation 비교 — 호출 측이 정확 일치를 한 번 더 확인한다). 미연결 행만. */
  List<NewStartThanksRoster> findByNicknameAndMatchStatus(String nickname, String matchStatus);

  /** 닉네임 후보(연결 여부 무관) — 이미 연결된 명단 충돌 탐지용. 호출 측이 정확 일치를 한 번 더 확인한다. */
  List<NewStartThanksRoster> findByNickname(String nickname);

  Optional<NewStartThanksRoster> findByLinkedUserId(Long linkedUserId);

  /** 신규 사전등록 identity 중복 확인(roster 503명과 겹치면 별도 identity 를 만들지 않는다). */
  boolean existsByCafeMemberTag(String cafeMemberTag);

  /** source별 명단 (운영자 화면용 — 수동 등록분 조회). */
  List<NewStartThanksRoster> findBySourceOrderByRosterIdDesc(String source);

}
