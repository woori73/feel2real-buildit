package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyAccountRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyAccountResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMappingRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyMappingResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyRuleRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyRuleResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAccountService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy 계정 master + 매핑 + 자동분류 규칙 — {@code /api/admin/**} → ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/easy/accounts")
@RequiredArgsConstructor
public class EasyAccountController {

  private final EasyAccountService accountService;

  @GetMapping
  public ResponseEntity<List<EasyAccountResponse>> list(
      @RequestParam(value = "direction", required = false) String direction) {
    return ResponseEntity.ok(accountService.listAccounts(direction));
  }

  @PostMapping
  public ResponseEntity<EasyAccountResponse> create(@RequestBody EasyAccountRequest request) {
    return ResponseEntity.ok(accountService.createAccount(request));
  }

  @PutMapping("/{accountId}")
  public ResponseEntity<EasyAccountResponse> update(
      @PathVariable Long accountId, @RequestBody EasyAccountRequest request) {
    return ResponseEntity.ok(accountService.updateAccount(accountId, request));
  }

  @GetMapping("/mappings")
  public ResponseEntity<List<EasyMappingResponse>> listMappings() {
    return ResponseEntity.ok(accountService.listMappings());
  }

  @PostMapping("/mappings")
  public ResponseEntity<EasyMappingResponse> createMapping(@RequestBody EasyMappingRequest request) {
    return ResponseEntity.ok(accountService.createMapping(request));
  }

  @DeleteMapping("/mappings/{mappingId}")
  public ResponseEntity<Void> deleteMapping(@PathVariable Long mappingId) {
    accountService.deleteMapping(mappingId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/rules")
  public ResponseEntity<List<EasyRuleResponse>> listRules() {
    return ResponseEntity.ok(accountService.listRules());
  }

  @PostMapping("/rules")
  public ResponseEntity<EasyRuleResponse> createRule(@RequestBody EasyRuleRequest request) {
    return ResponseEntity.ok(accountService.createRule(request));
  }

  @PutMapping("/rules/{ruleId}")
  public ResponseEntity<EasyRuleResponse> updateRule(
      @PathVariable Long ruleId, @RequestBody EasyRuleRequest request) {
    return ResponseEntity.ok(accountService.updateRule(ruleId, request));
  }

  @DeleteMapping("/rules/{ruleId}")
  public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
    accountService.deleteRule(ruleId);
    return ResponseEntity.noContent().build();
  }
}
