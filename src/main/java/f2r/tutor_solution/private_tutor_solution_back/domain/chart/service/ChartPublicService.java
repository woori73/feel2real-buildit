package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartActivityResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartEducatorResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateActivity;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartEducator;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateActivityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartEducatorRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChartPublicService {

  private final ChartEducatorRepository educatorRepository;
  private final ChartCandidateRepository candidateRepository;
  private final ChartCandidateActivityRepository activityRepository;

  public List<ChartEducatorResponse> topEducators(String zoneId) {
    ChartDiscoveryZone.fromId(zoneId);
    List<ChartEducator> educators = educatorRepository.findByZoneId(zoneId, Sort.by(Sort.Direction.DESC, "approvedAt"));
    int limit = Math.min(100, educators.size());
    return java.util.stream.IntStream.range(0, limit)
        .mapToObj(index -> toResponse(index + 1, educators.get(index)))
        .toList();
  }

  public List<ChartEducatorResponse> defaultTopEducators() {
    return topEducators(ChartDiscoveryZone.APGUJEONG_SINSA.getId());
  }

  private ChartEducatorResponse toResponse(int rank, ChartEducator educator) {
    List<ChartActivityResponse> activities = candidateRepository.findById(educator.getApprovedFromCandidateId())
        .map(this::activitiesFor)
        .orElse(List.of());
    return ChartEducatorResponse.of(rank, educator, activities);
  }

  private List<ChartActivityResponse> activitiesFor(ChartCandidate candidate) {
    List<ChartCandidateActivity> activities = activityRepository.findByCandidate_CandidateIdOrderByActivityIdAsc(candidate.getCandidateId());
    return activities.stream().map(ChartActivityResponse::from).toList();
  }
}
