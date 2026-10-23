package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant.UnifiedAssistantService;
import java.time.YearMonth;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class McpFinanceController {
  private final UnifiedAssistantService assistant;
  private final EasyTutorAccessService access;
  private final EasyWorkspaceService workspace;

  @GetMapping("/api/mcp/finance/summary")
  public Object summary(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
      jakarta.servlet.http.HttpServletResponse response) {
    assistant.actor(p);
    access.requireAllowedBusiness(p, businessId);
    var m = workspace.monthly(businessId, month == null ? YearMonth.now() : month);
    response.setHeader("Cache-Control", "no-store");
    return Map.of(
        "businessId",
        businessId,
        "month",
        m.month(),
        "income",
        m.income(),
        "expense",
        m.expense(),
        "profit",
        m.profit(),
        "receivable",
        m.receivable(),
        "ledgerSavedCount",
        m.ledgerSavedCount(),
        "openReviewCount",
        m.openReviewCount());
  }
}
