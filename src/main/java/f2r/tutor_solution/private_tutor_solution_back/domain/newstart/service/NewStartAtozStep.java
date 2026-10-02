package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import java.util.List;
import java.util.Locale;

/**
 * 새출발기금 A to Z 14단계의 단일 출처(§2·§7). 단계명·순서는 A to Z 템플릿(step-0..step-13)과
 * 정확히 일치하며, <b>새 단계를 만들지 않는다</b>.
 *
 * <p>두 곳에서 읽기 전용으로 쓴다:
 * <ul>
 *   <li>지피지기 STEP 1 — 질문을 결정적 키워드로 단계에 연결해 프롬프트에 "A to Z 단계 연결" 블록을 붙이고,
 *       응답에 해당 단계 이동 링크를 내려준다(§7). LLM 호출·AI 판단이 아니다.</li>
 *   <li>{@link NewStartAtozCaseService} — 운영 DB 사례를 같은 키워드 표로 단계에 그룹핑(§3).</li>
 * </ul>
 *
 * <p>키워드 표를 한 곳만 두어 "질문이 가리키는 단계"와 "그 단계에 걸린 사례"가 어긋나지 않게 한다.
 * 신호가 없는 질문에는 어떤 단계도 붙이지 않는다(무관한 단계를 안전 명목으로 끼우지 않는다).
 */
public final class NewStartAtozStep {

  private NewStartAtozStep() {}

  /** @param no 단계 번호(0..13). @param title A to Z 실제 단계명. @param summary 단계 핵심 1~2문장(기존 본문 요약). */
  public record Step(int no, String title, String summary) {
    public String anchor() {
      return "step-" + no;
    }

    public String url() {
      return "/newstart/a-to-z#step-" + no;
    }
  }

  private static final List<Step> STEPS = List.of(
      new Step(0, "협약기관 확인",
          "내 채무의 채권금융기관이 새출발기금 협약기관인지 먼저 확인한다. 협약 미가입 대출은 지원대상에서 제외될 수 있다."),
      new Step(1, "연체 시작 · 추심 대응",
          "납부가 밀려 독촉·추심을 받기 시작한 단계. 추심 내용을 기록하고 위법·부당추심은 금감원 상담·민원을 검토한다."),
      new Step(2, "지급명령 · 전자소송",
          "법원에서 지급명령 등 문서를 받은 단계. 송달 후 2주 이내 이의신청 기한을 확인하고 전자소송을 준비한다."),
      new Step(3, "Credit4U · 장기연체",
          "연체가 길어져 내 채무·연체정보가 어떻게 등록됐는지 Credit4U에서 확인하는 단계."),
      new Step(4, "신청 자격 · 공식 신청",
          "신청 자격을 확인하고 실제로 새출발기금을 신청하는 단계. 신청은 1회만 가능하다."),
      new Step(5, "원장 초기화",
          "자격조회·신청 과정에서 조회/원장 상태를 다시 정리해야 했던 단계."),
      new Step(6, "지원 가능한 대출",
          "내 어떤 채무가 조정 대상이 되는지 확인하는 단계. 사업·영업 관련 대출이 대상이며 최대 15억원(담보 10억 + 무담보 5억)."),
      new Step(7, "부실우려 → 부실차주",
          "부실우려차주로 진행 중이거나 약정이 실효되어 부실차주 전환을 고려하는 단계."),
      new Step(8, "부동산 담보",
          "주택·상가 등 부동산 담보 대출이 채무에 포함된 단계. 담보채무는 신복위 중개형 경로다."),
      new Step(9, "차량 담보",
          "차량 대출(할부·담보)이 채무에 포함된 단계. 사업용인지 개인 구입목적 가계대출인지가 중요하다."),
      new Step(10, "임시안 → 확정안",
          "신청 후 임시안/확정안을 기다리거나 심사 단계에 있는 단계. 감면율은 개별 심사 결과다."),
      new Step(11, "교육 이수 · 추가감면",
          "채무조정 중이거나 확정받은 폐업 부실차주가 취·창업 프로그램 이수로 추가감면을 검토하는 단계."),
      new Step(12, "성실상환 이후 지원",
          "약정 성실상환 중이거나 완료한 단계. 조기상환 인센티브와 재기지원 등 금융지원을 확인한다."),
      new Step(13, "신청결과 · 진행상태",
          "신청을 완료하고 결과와 다음 단계를 확인하는 단계."));

  /**
   * 단계(0..13) → 키워드. A to Z 실제 단계명·본문 어휘에서 뽑았으며 소문자 비교한다.
   * 가장 많이 일치한 단계로 배정하고, 동점이면 낮은 번호가 이긴다. 일치 0건이면 어떤 단계도 붙이지 않는다.
   */
  private static final List<String[]> KEYWORDS = List.of(
      /* 0 */ new String[] {"협약", "비협약", "협약기관", "협약금융", "가입 채권"},
      /* 1 */ new String[] {"추심", "독촉", "압류", "가압류", "경매", "강제집행", "금감원", "민원", "연체 시작", "위법추심"},
      /* 2 */ new String[] {"지급명령", "전자소송", "이의신청", "법원", "소송", "독촉절차", "전자송달", "송달"},
      /* 3 */ new String[] {"credit4u", "장기연체", "신용정보", "신용도판단", "kcredit", "채권자 변동"},
      /* 4 */ new String[] {"신청 자격", "신청방법", "신청 방법", "접수", "준비물", "자격조회", "신청자격", "부실차주 신청"},
      /* 5 */ new String[] {"원장", "초기화", "조회기록", "원장정리"},
      /* 6 */ new String[] {"대상 채무", "지원대상", "지원 대상", "지원 가능한 대출", "15억", "대상채무"},
      /* 7 */ new String[] {"부실우려", "실효", "중개형", "부실차주 전환", "약정 실효"},
      /* 8 */ new String[] {"부동산", "주택", "담보", "후순위", "근저당", "상가", "선순위"},
      /* 9 */ new String[] {"차량", "자동차", "승합차", "할부", "공매", "차량담보"},
      /* 10 */ new String[] {"임시안", "확정안", "감면율", "심사", "재산심사", "도덕적 해이", "재산은닉", "명의이전"},
      /* 11 */ new String[] {"교육", "추가감면", "재취업", "재창업", "희망리턴", "국민취업", "폴리텍", "iap", "이수증", "멘토"},
      /* 12 */ new String[] {"성실상환", "인센티브", "조기상환", "재기지원", "전환보증", "다시서기", "재도전", "상환유예"},
      /* 13 */ new String[] {"신청결과", "진행상태", "결과조회", "신청 결과", "진행 상태"});

  public static List<Step> all() {
    return STEPS;
  }

  /** 단계 번호(0..13)로 조회. 범위 밖이면 null. 상담 화면이 저장된 atoz_step을 단계명/링크로 되찾는다. */
  public static Step byNo(int no) {
    return (no < 0 || no >= STEPS.size()) ? null : STEPS.get(no);
  }

  /** 텍스트(질문 또는 지식 항목)에서 가장 많이 일치한 단계. 신호가 없으면 null. */
  public static Step detect(String text) {
    if (text == null || text.isBlank()) return null;
    String haystack = text.toLowerCase(Locale.ROOT);
    Step best = null;
    int bestHits = 0;
    for (int i = 0; i < KEYWORDS.size(); i++) {
      int hits = 0;
      for (String kw : KEYWORDS.get(i)) {
        if (haystack.contains(kw.toLowerCase(Locale.ROOT))) hits++;
      }
      if (hits > bestHits) {
        bestHits = hits;
        best = STEPS.get(i);
      }
    }
    return bestHits == 0 ? null : best;
  }

  /** {@link #detect}의 단계 번호 판. 신호가 없으면 -1. {@link NewStartAtozCaseService}가 같은 표를 쓴다. */
  public static int detectStepNo(String text) {
    Step s = detect(text);
    return s == null ? -1 : s.no();
  }
}
