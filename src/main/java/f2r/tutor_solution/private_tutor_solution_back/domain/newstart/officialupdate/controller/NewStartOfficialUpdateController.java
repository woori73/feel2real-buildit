package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/newstart/updates")
public class NewStartOfficialUpdateController {

  private final NewStartOfficialUpdateService service;

  @GetMapping
  public ResponseEntity<List<NewStartOfficialUpdateResponse>> list(
      @RequestParam(required = false) SourceType sourceType) {
    return ResponseEntity.ok(service.publicUpdates(sourceType));
  }
}
