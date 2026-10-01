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

  Optional<NewStartThanksRoster> findByLinkedUserId(Long linkedUserId);
}
