package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.MidtermExamScopeProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCampaignScope;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 캠페인 Wiki candidate discovery 의 <b>(학교, 학년) 별 exam scope</b> 판정.
 *
 * <ul>
 *   <li>기본 scope = 캠페인 (examYear, semester, examType).</li>
 *   <li>{@link MidtermExamScopeProperties} override 가 있는 (학교, 학년) 은 그 override exam 만 허용.</li>
 *   <li>gateway 를 각 scope 로 조회해 union 하고, 각 candidate 는 <b>그 (학교, 학년) 이 허용하는
 *       exam 과 정확히 일치</b>할 때만 통과한다(#232 fail-closed — 임의 scope 통과 없음).</li>
 * </ul>
 * exam 문자열은 Wiki(Python) 정규화 규칙(중간→중간고사, 기말→기말고사)에 맞춰 비교한다.
 */
@Component
@RequiredArgsConstructor
public class MidtermExamScopePolicy {

  private static final Pattern GRADE = Pattern.compile("\\d+");

  private final MidtermExamScopeProperties properties;
  private final MidtermWikiSourceGateway gateway;

  /** gateway candidate 를 모든 허용 scope 로 모으고, (학교, 학년) exam 정책으로 필터링한 목록. */
  public List<WikiSourceRow> discover(
      AuthenticatedPrincipal tutor, String handle, MidtermCampaign campaign) {
    Map<String, WikiSourceRow> byKey = new LinkedHashMap<>();
    for (WikiCampaignScope scope : scopes(handle, campaign)) {
      for (WikiSourceRow row : gateway.listCandidates(tutor, scope)) {
        byKey.putIfAbsent(row.kind() + ":" + row.id(), row);
      }
    }
    String defaultExam = campaign.getExamType();
    return byKey.values().stream()
        .filter(row -> allowedExam(handle, row.school(), gradeNumber(row.grade()), defaultExam)
            .equals(normalizeExam(row.exam())))
        .toList();
  }

  /** gateway 에 질의할 distinct scope 목록(기본 + override). */
  public List<WikiCampaignScope> scopes(String handle, MidtermCampaign campaign) {
    Set<String> exams = new LinkedHashSet<>();
    exams.add(normalizeExam(campaign.getExamType()));
    properties.overridesFor(handle).forEach(o -> exams.add(normalizeExam(o.getExam())));
    return exams.stream()
        .filter(e -> !e.isEmpty())
        .map(exam -> new WikiCampaignScope(campaign.getExamYear(), campaign.getSemester(), exam))
        .toList();
  }

  /** 이 (학교, 학년) 이 이번 캠페인에서 준비 중인 시험(정규화). override 없으면 캠페인 기본값. */
  public String allowedExam(String handle, String school, Integer grade, String campaignDefault) {
    String normSchool = normalizeSchool(school);
    int g = grade == null ? Integer.MIN_VALUE : grade;
    return properties.overridesFor(handle).stream()
        .filter(o -> normalizeSchool(o.getSchool()).equals(normSchool) && o.getGrade() == g)
        .map(o -> normalizeExam(o.getExam()))
        .filter(e -> !e.isEmpty())
        .findFirst()
        .orElse(normalizeExam(campaignDefault));
  }

  static String normalizeExam(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.equals("중간")) {
      return "중간고사";
    }
    if (s.equals("기말")) {
      return "기말고사";
    }
    return s;
  }

  /** "압구정중학교" / "압구정중" 을 같게 본다. */
  static String normalizeSchool(String raw) {
    String s = raw == null ? "" : raw.trim();
    return s.endsWith("학교") ? s.substring(0, s.length() - 2) : s;
  }

  static Integer gradeNumber(String value) {
    if (value == null) {
      return null;
    }
    Matcher m = GRADE.matcher(value);
    return m.find() ? Integer.parseInt(m.group()) : null;
  }
}
