package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.SetInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetService;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetService.Detail;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetService.Row;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetService.SaveRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수업 준비 › 학습지 API. 원장(TUTOR)·관리자(ADMIN) 공용(/api/tutor/easy/**), 모든 요청에 ?businessId=.
 * 사업체 권한은 EasyTutorAccessService가 서버에서 다시 확인한다. 화면: /dashboard/worksheets.
 */
@RestController
@RequestMapping("/api/tutor/easy/worksheets")
public class WorksheetController {

  private final EasyTutorAccessService access;
  private final WorksheetService service;
  private final WorksheetQuestionBank bank;

  public WorksheetController(EasyTutorAccessService access, WorksheetService service, WorksheetQuestionBank bank) {
    this.access = access;
    this.service = service;
    this.bank = bank;
  }

  /** 내가 쓸 수 있는 사업체(화면 첫 선택). */
  @GetMapping("/businesses")
  public List<EasyBusinessResponse> businesses(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return access.allowedBusinesses(p);
  }

  @GetMapping
  public List<Row> list(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @RequestParam(defaultValue = "ACADEMY") String tab) {
    access.requireAllowedBusiness(p, businessId);
    return service.list(businessId, tab, p);
  }

  /** 범위 고르기에 쓰는 묶음(내신 회차·어법 단원). */
  @GetMapping("/bank/sets")
  public List<SetInfo> sets(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId) {
    access.requireAllowedBusiness(p, businessId);
    return bank.sets(p);
  }

  /** 고른 묶음에서 조건에 맞는 문항과 유형별 문항 수. */
  @GetMapping("/bank/questions")
  public Map<String, Object> questions(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @RequestParam List<String> sets, @RequestParam(required = false) List<String> types,
      @RequestParam(required = false) List<String> levels) {
    access.requireAllowedBusiness(p, businessId);
    List<Question> inSets = bank.search(p, sets, null, null);
    return Map.of("typeCounts", WorksheetQuestionBank.countByType(inSets), "questions", bank.search(p, sets, types, levels));
  }

  @GetMapping("/{worksheetId}")
  public Detail detail(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId) {
    access.requireAllowedBusiness(p, businessId);
    return service.detail(businessId, worksheetId, p);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Row create(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @RequestBody SaveRequest req) {
    access.requireAllowedBusiness(p, businessId);
    return service.create(businessId, req, p);
  }

  @PutMapping("/{worksheetId}")
  public Row update(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId, @RequestBody SaveRequest req) {
    access.requireAllowedBusiness(p, businessId);
    return service.update(businessId, worksheetId, req, p);
  }

  /** 휴지통으로 보낸다(soft delete). */
  @DeleteMapping("/{worksheetId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void trash(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId) {
    access.requireAllowedBusiness(p, businessId);
    service.trash(businessId, worksheetId, p);
  }

  @PostMapping("/{worksheetId}/restore")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void restore(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId) {
    access.requireAllowedBusiness(p, businessId);
    service.restore(businessId, worksheetId, p);
  }

  /** 영구 삭제(휴지통에 있는 것만). */
  @DeleteMapping("/{worksheetId}/purge")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void purge(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId) {
    access.requireAllowedBusiness(p, businessId);
    service.purge(businessId, worksheetId, p);
  }

  @PutMapping("/{worksheetId}/favorite")
  public Map<String, Boolean> favorite(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId, @RequestParam boolean on) {
    access.requireAllowedBusiness(p, businessId);
    return Map.of("favorite", service.favorite(businessId, worksheetId, on, p));
  }
}
