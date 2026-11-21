package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 내신 실전 연습(디지털 영어 기말고사) 이용 범위.
 * <ul>
 *   <li>로그인한 일반 회원: 학교마다 1회(시험 코드 끝이 -R01)만 풀고 해설을 볼 수 있다.</li>
 *   <li>관리자(ADMIN)와 압구정에듀홀릭 재원생: 전 회차. 재원생은 대시보드 출결 학생부(easy_students)의
 *       재원(ENROLLED) 학생과 회원 이름이 같거나, 회원 휴대전화가 학생·학부모 번호와 같으면 재원생으로 본다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class NaesinAccessService {

  public static final String OPEN_ROUND_SUFFIX = "-R01";
  public static final String LOCKED_MESSAGE =
      "1회는 로그인한 회원 누구나 풀 수 있습니다. 2회부터는 압구정에듀홀릭 재원생과 관리자만 풀 수 있습니다.";

  private final UserRepository userRepository;
  private final EasyStudentRepository easyStudentRepository;

  public static boolean isOpenRound(String examCode) {
    return examCode != null && examCode.endsWith(OPEN_ROUND_SUFFIX);
  }

  /** 전 회차를 풀 수 있는지(관리자 또는 재원생). */
  @Transactional(readOnly = true)
  public boolean hasFullAccess(AuthenticatedPrincipal principal) {
    if (principal == null || principal.userId() == null) return false;
    if ("ADMIN".equals(principal.role())) return true;
    // 재원생 앱 코드 로그인(원장이 발급한 재원생). 앱은 원장이 배정한 시험만 시작하게 StudentExamAppService가 막는다.
    if ("STUDENT_APP".equals(principal.role())) return true;
    User user = userRepository.findById(principal.userId()).orElse(null);
    if (user == null) return false;
    String name = trim(user.getName());
    String phone = digits(user.getPhone());
    for (EasyStudent s : easyStudentRepository.findAllByStatus(EasyStudentStatus.ENROLLED)) {
      if (!name.isEmpty() && name.equals(trim(s.getName()))) return true;
      if (phone.length() >= 10 && (phone.equals(digits(s.getPhone())) || phone.equals(digits(s.getParentPhone())))) return true;
    }
    return false;
  }

  /** 이 회원이 이 시험을 풀 수 있는지. 1회는 누구나, 나머지는 전 회차 이용자만. */
  public boolean canTake(AuthenticatedPrincipal principal, String examCode) {
    return isOpenRound(examCode) || hasFullAccess(principal);
  }

  public void requireCanTake(AuthenticatedPrincipal principal, String examCode) {
    if (!canTake(principal, examCode)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, LOCKED_MESSAGE);
  }

  static String trim(String v) {
    return v == null ? "" : v.replaceAll("\\s+", "");
  }

  /** 숫자만 남기고 +82는 0으로 바꾼다(010-1234-5678, +82 10-1234-5678 → 01012345678). */
  static String digits(String v) {
    if (v == null) return "";
    String d = v.replaceAll("[^0-9]", "");
    if (d.startsWith("82") && d.length() >= 11) d = "0" + d.substring(2);
    return d;
  }
}
