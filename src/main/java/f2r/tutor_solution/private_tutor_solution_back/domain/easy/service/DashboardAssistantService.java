package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceTransactionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * BuildIt 대시보드 AI 비서(읽기 전용). Muse Spark 1.3 Contributor만 호출하고 다른 모델로 대체하지 않는다.
 *
 * <p>데이터는 {@link EasyTutorAccessService}로 접근 권한을 확인한 사업체의 월 요약({@link EasyWorkspaceService#monthly})만
 * 문맥으로 전달한다. 쓰기 도구는 연결하지 않는다.
 */
@Service
public class DashboardAssistantService {

  static final int MAX_MESSAGE_CHARS = 1000;
  static final int MAX_RECENT = 10;
  static final int MAX_OVERDUE = 10;

  static final String SYSTEM = """
      당신은 BuildIt 학원 운영 대시보드의 읽기 전용 AI 비서다.
      아래 "서버 문맥"에 있는 숫자와 항목만 근거로 한국어로 간결하게 답한다.
      문맥에 없는 값은 추측하지 말고 "현재 대시보드 데이터에서 확인할 수 없습니다"라고 말한다.
      장부를 수정·삭제하거나 거래를 등록하지 않는다. 그런 요청에는 장부 화면(이지장부/에듀홀릭 장부)에서 직접 하도록 안내한다.
      이 재무 해설 문맥에는 출결 데이터가 없다. 출결 질문에는 대시보드 상단 통합 비서의 학원 현황·출결 메뉴에서 확인하도록 안내한다.
      BuildIt 기능 안내 질문에는 대시보드의 서비스 카드(에듀홀릭 장부, 이지장부, 홈페이지 빌더, 내신위키, TESTPREP)를 기준으로 설명한다.
      """;

  private final EasyTutorAccessService accessService;
  private final EasyWorkspaceService workspaceService;
  private final MetaMuseSparkLlmTransport transport;

  public DashboardAssistantService(
      EasyTutorAccessService accessService,
      EasyWorkspaceService workspaceService,
      MetaMuseSparkLlmTransport transport) {
    this.accessService = accessService;
    this.workspaceService = workspaceService;
    this.transport = transport;
  }

  /** 답변 결과. ok=false면 text는 사용자에게 보여줄 오류 안내다. */
  public record Answer(boolean ok, String text, String model) {}

  public Answer ask(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long businessId, YearMonth month, String message) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("질문을 입력해 주세요.");
    }
    if (message.length() > MAX_MESSAGE_CHARS) {
      throw new IllegalArgumentException("질문은 " + MAX_MESSAGE_CHARS + "자 이하로 입력해 주세요.");
    }
    EasyBusiness business = accessService.requireAllowedBusiness(principal, businessId);
    YearMonth target = month == null ? YearMonth.now() : month;
    EasyWorkspaceMonthlyResponse monthly = workspaceService.monthly(business.getBusinessId(), target);
    NewStartLlmCompletion completion =
        transport.complete(SYSTEM, buildContext(monthly) + "\n사용자 질문:\n" + message.strip(), 4000);
    if (!completion.ok()) {
      return new Answer(false, completion.errorMessage(), null);
    }
    return new Answer(true, completion.text(), completion.model());
  }

  static String buildContext(EasyWorkspaceMonthlyResponse m) {
    StringBuilder sb = new StringBuilder("서버 문맥:\n");
    sb.append("사업체: ").append(m.businessName()).append(" / 기준월: ").append(m.month()).append('\n');
    sb.append("수입 ").append(m.income()).append("원, 지출 ").append(m.expense()).append("원, 수지 ")
        .append(m.profit()).append("원, 미수금 ").append(m.receivable()).append("원, 원생 수 ")
        .append(m.studentCount()).append("명, 확인 필요 항목 ").append(m.openReviewCount()).append("건\n");
    List<EasyWorkspaceTransactionResponse> recent = m.recentTransactions() == null ? List.of() : m.recentTransactions();
    sb.append("최근 거래(최대 ").append(MAX_RECENT).append("건):\n");
    recent.stream().limit(MAX_RECENT).forEach(t -> {
      var x = t.transaction();
      sb.append("- ").append(x.occurredOn()).append(' ').append(x.direction()).append(' ').append(x.amount())
          .append("원 ").append(nz(x.counterparty())).append(' ').append(nz(x.description())).append('\n');
    });
    var overdue = m.overdueStudents() == null ? List.<f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse.EasyOverdueStudent>of()
        : m.overdueStudents();
    sb.append("미납 원생(최대 ").append(MAX_OVERDUE).append("명):\n");
    overdue.stream().limit(MAX_OVERDUE).forEach(s ->
        sb.append("- ").append(s.name()).append(' ').append(nz(s.school())).append(" 미납 ").append(s.balance()).append("원\n"));
    return sb.toString();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
