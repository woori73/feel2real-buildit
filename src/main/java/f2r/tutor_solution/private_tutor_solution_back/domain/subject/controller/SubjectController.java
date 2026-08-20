package f2r.tutor_solution.private_tutor_solution_back.domain.subject.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectRepository subjectRepository;

    @GetMapping
    public ResponseEntity<List<Subject>> getSubjects() {
        return ResponseEntity.ok(subjectRepository.findAll());
    }
}
