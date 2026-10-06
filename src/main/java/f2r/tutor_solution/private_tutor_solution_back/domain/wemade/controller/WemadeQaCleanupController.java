package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QA 테스트 계정 정리용 일회성 엔드포인트 (ADMIN 전용).
 * wemade_qa_test01~05만 삭제한다. 다른 계정은 절대 건드리지 않는다.
 * 각 계정이 ROLE_STUDENT인지 확인하고, 아니면 건너뛴다.
 * FK 제약이 있는 경우 삭제가 실패하고 롤백된다 (안전 실패).
 * 사용 후 제거할 것.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/wemade")
@RequiredArgsConstructor
public class WemadeQaCleanupController {

    private static final List<String> QA_TEST_IDS = List.of(
            "wemade_qa_test01",
            "wemade_qa_test02",
            "wemade_qa_test03",
            "wemade_qa_test04",
            "wemade_qa_test05"
    );

    private final UserRepository userRepository;

    @DeleteMapping("/qa-test-accounts")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteQaTestAccounts() {
        List<String> deleted = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        for (String loginId : QA_TEST_IDS) {
            var opt = userRepository.findByLoginId(loginId);
            if (opt.isEmpty()) {
                notFound.add(loginId);
                continue;
            }
            User user = opt.get();
            // STUDENT가 아니면 절대 삭제하지 않는다
            if (user.getRole() != Role.STUDENT) {
                log.warn("WEMADE QA cleanup skipped non-STUDENT account: {} role={}", loginId, user.getRole());
                skipped.add(loginId + " (role=" + user.getRole() + ")");
                continue;
            }
            userRepository.delete(user);
            deleted.add(loginId);
            log.info("WEMADE QA cleanup deleted test account: {}", loginId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("skipped", skipped);
        result.put("notFound", notFound);
        return ResponseEntity.ok(result);
    }
}
