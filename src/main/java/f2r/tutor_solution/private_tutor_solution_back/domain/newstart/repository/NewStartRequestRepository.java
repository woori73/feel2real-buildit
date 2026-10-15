package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 질문요청 게시판 repository. 목록 조회는 항상 {@code deleted=false}(회원 Withdraw 시 원문 파기).
 *
 * <p>정렬은 "최근 작성 순". kind/status 필터는 화면의 2개 인박스 탭과 운영자 검토 큐에 대응한다.
 */
public interface NewStartRequestRepository extends JpaRepository<NewStartRequest, Long> {

  Page<NewStartRequest> findByDeletedFalseOrderByRequestIdDesc(Pageable pageable);

  Page<NewStartRequest> findByKindAndDeletedFalseOrderByRequestIdDesc(NewStartRequestKind kind,
      Pageable pageable);

  List<NewStartRequest> findByStatusAndDeletedFalseOrderByRequestIdAsc(NewStartRequestStatus status);
}
