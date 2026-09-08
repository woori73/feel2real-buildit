package f2r.tutor_solution.private_tutor_solution_back.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 내신 LIVE 캠페인에서 <b>(학교, 학년) 별 "이번에 준비 중인 실제 시험"</b> override.
 *
 * <p>명시하지 않은 (학교, 학년)은 캠페인의 {@code exam_type}(예: 중간고사)만 허용한다. override 는
 * "이 (학교, 학년)만 다른 시험(예: 기말고사)을 준비 중" 을 <b>명시적으로</b> 선언한다 —
 * wildcard / cross-exam fallback 이 아니다(#232 fail-closed 유지). override 의 {@code exam} 은
 * 실제 Wiki 자료의 {@code exam} 문자열과 정확히 일치해야 노출된다(불일치 시 "준비 중" 폴백).
 *
 * <pre>
 * app:
 *   midterm:
 *     exam-scope-overrides:
 *       eduholic:
 *         - { school: "압구정중", grade: 3, exam: "기말고사" }
 *         - { school: "신사중",   grade: 3, exam: "기말고사" }
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.midterm")
public class MidtermExamScopeProperties {

  /** academy handle → 그 캠페인의 (학교, 학년) 시험 override 목록. */
  private Map<String, List<Override>> examScopeOverrides = new HashMap<>();

  public Map<String, List<Override>> getExamScopeOverrides() {
    return examScopeOverrides;
  }

  public void setExamScopeOverrides(Map<String, List<Override>> examScopeOverrides) {
    this.examScopeOverrides = examScopeOverrides == null ? new HashMap<>() : examScopeOverrides;
  }

  public List<Override> overridesFor(String handle) {
    return examScopeOverrides.getOrDefault(handle, List.of());
  }

  public static class Override {
    private String school = "";
    private int grade;
    private String exam = "";

    public String getSchool() {
      return school;
    }

    public void setSchool(String school) {
      this.school = school == null ? "" : school;
    }

    public int getGrade() {
      return grade;
    }

    public void setGrade(int grade) {
      this.grade = grade;
    }

    public String getExam() {
      return exam;
    }

    public void setExam(String exam) {
      this.exam = exam == null ? "" : exam;
    }
  }
}
