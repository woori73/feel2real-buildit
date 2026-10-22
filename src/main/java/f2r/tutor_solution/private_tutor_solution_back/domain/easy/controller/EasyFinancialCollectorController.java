package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFinancialCollectorDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialCollectorService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialFileParser;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Independent Task A tool boundary. Enabled explicitly on local; absent on prd. */
@RestController
@RequestMapping("/api/admin/easy/financial-collector")
@Profile("local & !prd")
@ConditionalOnProperty(name = "app.easy.financial-collector.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EasyFinancialCollectorController {
  private final EasyFinancialCollectorService service;

  @GetMapping("/capabilities")
  public List<Map<String, Object>> capabilities() {
    return Arrays.stream(Dataset.values()).map(dataset -> Map.<String, Object>of(
        "institution", dataset.institution, "dataset", dataset.name(),
        "manualUpload", true, "officialApi", switch (dataset.institution) {
          case "BANK" -> "AVAILABLE_SUBJECT_TO_CONTRACT";
          case "CARD" -> "PARTNER_SPECIFIC_REQUIRES_VERIFICATION";
          default -> "GENERAL_PURPOSE_API_NOT_VERIFIED";
        },
        "browserDownloadEnabled", false, "postingRoute", dataset.postingRoute,
        "formats", List.of("csv", "xls", "xlsx", "pdf-text-template"))).toList();
  }

  @PostMapping("/previews")
  @ResponseStatus(HttpStatus.CREATED)
  public Status upload(@RequestParam Long businessId, @RequestParam Dataset dataset,
      @RequestParam String accountRef, @RequestParam String from, @RequestParam String to,
      @RequestParam(required = false) Integer expectedCount,
      @RequestParam(required = false) Long expectedTotal,
      @RequestParam(defaultValue = "false") boolean userApprovedUpload,
      @RequestParam MultipartFile file, @AuthenticationPrincipal AuthenticatedPrincipal principal)
      throws IOException {
    if (file.getSize() == 0 || file.getSize() > EasyFinancialFileParser.MAX_BYTES) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_INVALID");
    }
    return service.upload(businessId, new Scope(dataset, accountRef, from, to, expectedCount, expectedTotal),
        file.getOriginalFilename(), file.getBytes(), userApprovedUpload, principal);
  }

  @GetMapping("/previews/{id}")
  public Status status(@PathVariable String id, @RequestParam Long businessId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return service.get(businessId, id, principal);
  }

  @PostMapping("/previews/{id}/stage")
  public Status stage(@PathVariable String id, @RequestParam Long businessId,
      @RequestBody Review request, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return service.stage(businessId, id, request.reviewed(), principal);
  }

  public record Review(boolean reviewed) {}

  @PostMapping("/previews/{id}/approve-and-commit")
  public Status approveAndCommit(@PathVariable String id, @RequestParam Long businessId,
      @RequestBody Commit request, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return service.approveAndCommit(businessId, id, request.userApprovedLedgerCommit(), request.reviewHash(), principal);
  }

  public record Commit(boolean userApprovedLedgerCommit, String reviewHash) {}
}
