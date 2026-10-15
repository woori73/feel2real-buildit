package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoverySettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import org.springframework.stereotype.Component;

@Component
public class ChartPromptFactory {

  public String contentDiscoveryPrompt(ChartDiscoveryZone zone, ChartDiscoverySettings settings) {
    String schoolClause = zone.getSchoolTerms().isEmpty() ? "" : "\n학교명 기반 콘텐츠도 함께 활용하세요: " + String.join("、", zone.getSchoolTerms())
        + " 관련 영어 내신/시험분석/기출분석/교육칼럼 콘텐츠.";
    return """
        당신은 "%s" 생활권(강남구 내 세부 지역)에서 학생을 직접 가르치는 '개인 영어교육자'(원장 또는 강사)를 Google 검색으로 발굴하는 리서치 어시스턴트입니다.

        절대 원칙 — CONTENT FIRST, PERSON SECOND:
        - 사람 이름 목록을 먼저 찾지 말고, 검색권역(%s)과 관련된 실제 교육활동 콘텐츠를 먼저 찾으세요.
        - 포함할 활동: %s
        - 그 콘텐츠(블로그 글, 학원/개인 홈페이지, 교육칼럼, 인터뷰, YouTube, YouTube Shorts 등)를 실제로 작성했거나 출연한 사람이 누구인지 역추적해서 후보로 만드세요.
        - 학원 자체는 후보가 아닙니다. 결과의 주체는 항상 사람입니다.%s

        제외대상/주의: %s
        추가 검색 지시: %s

        후보는 최대 %d명만 반환하세요. academyName은 명확히 확인되지 않으면 null로 두세요. sourceUrl은 실제 검색 근거 URL이어야 합니다.
        JSON 배열만 출력하세요:
        [{"name":"실명","role":"원장|대표|강사|선생님","academyName":"학원명 또는 null","activityRegion":"확인된 활동 지역","contentType":"글|칼럼|수업|YouTube|Shorts 등","contentSummary":"발견된 교육활동 콘텐츠 내용","sourceUrl":"https://...","discoveryReason":"콘텐츠에서 이 사람을 후보로 역추적한 이유","isAmbiguous":false,"isLargeFranchiseSignal":false,"isMultiSiteSignal":false,"isOnlineOnlySignal":false}]
        검색 결과가 없으면 []만 출력하세요. 결과를 지어내지 마세요.
        """.formatted(
        zone.getLabel(), String.join("、", zone.getSearchAreas()), settings.getIncludeActivities(), schoolClause,
        settings.getExcludedTargets(), settings.getExtraInstruction(), settings.getCandidateLimit());
  }

  public String deepSearchPrompt(String name, String role, String academyName, ChartDiscoveryZone zone, ChartDiscoverySettings settings) {
    String academyClause = academyName == null || academyName.isBlank() ? "(소속 미확인)" : "(제보된 소속: " + academyName + ")";
    return """
        "%s"(%s) %s이(가) "%s" 지역에서 실제로 학생을 직접 가르치는 영어교육자인지 Google 검색으로 심층 확인(Deep Search)하세요.

        확인할 것:
        1. 현재 원장/강사 역할과 현재 소속
        2. 단발성 광고가 아니라 수업/내신분석/교육칼럼/YouTube/Shorts 등 서로 다른 공개 교육활동 근거
        3. 제외대상(%s)에 해당하는지 여부
        4. 지역 독립 교육자인지 여부

        JSON 하나만 출력하세요:
        {"currentRoleConfirmed":true,"currentRole":"원장|대표|강사|선생님 또는 null","currentAcademyName":"학원명 또는 null","isLargeFranchiseSignal":false,"isMultiSiteSignal":false,"isOnlineOnlySignal":false,"activities":[{"contentType":"글|칼럼|수업|YouTube|Shorts 등","contentSummary":"1-2문장","sourceUrl":"https://..."}]}
        activities는 실제 근거만 최대 5건까지 포함하세요. sourceUrl을 지어내지 마세요.
        """.formatted(name, role, academyClause, zone.getLabel(), settings.getExcludedTargets());
  }
}
