package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCampaignScope;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceListResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermWikiSourceExposure;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermWikiSourceExposureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public LIVE source = current campaign-scoped Wiki candidate ∩ visible exposure.
 * SchoolCollection은 이 경로의 prerequisite가 아니며, exposure 부재/불일치는 항상 fail closed다.
 */
@Service
@RequiredArgsConstructor
public class MidtermWikiSourceService {

  private final AcademyRepository academyRepository;
  private final MidtermCampaignRepository campaignRepository;
  private final MidtermWikiSourceExposureRepository exposureRepository;
  private final MidtermWikiSourceGateway gateway;

  @Transactional(readOnly = true)
  public WikiSourceListResult listSources(String handle) {
    Context context = requirePublishedCampaign(handle);
    WikiCampaignScope scope = scope(context.campaign());
    List<WikiSourceRow> candidates =
        gateway.listCandidates(tutorPrincipal(context.academy()), scope);
    Map<String, WikiSourceRow> byKey = candidates.stream().collect(Collectors.toMap(
        row -> key(row.kind(), row.id()), Function.identity(), (first, ignored) -> first));

    List<WikiSourceView> views = exposureRepository
        .findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(
            context.campaign().getCampaignId())
        .stream()
        .map(exposure -> byKey.get(key(exposure.getSourceKind(), exposure.getSourceId())))
        .filter(java.util.Objects::nonNull)
        .map(row -> toView(handle, sourceId(row.kind(), row.id()), row))
        .toList();
    return new WikiSourceListResult(views, candidates.isEmpty() ? List.of(scope) : List.of());
  }

  /** Preview 요청마다 published campaign, current candidate, visible exposure를 모두 재검증한다. */
  @Transactional(readOnly = true)
  public byte[] previewHtml(String handle, String sourceId) {
    ParsedSourceId parsed = parseSourceId(sourceId);
    Context context = requirePublishedCampaign(handle);
    boolean visible = exposureRepository
        .findByCampaignIdAndSourceKindAndSourceId(
            context.campaign().getCampaignId(), parsed.kind(), parsed.id())
        .map(MidtermWikiSourceExposure::isVisible)
        .orElse(false);
    if (!visible) {
      throw notFound();
    }

    AuthenticatedPrincipal tutor = tutorPrincipal(context.academy());
    boolean currentCandidate = gateway.listCandidates(tutor, scope(context.campaign())).stream()
        .anyMatch(row -> row.kind().equals(parsed.kind()) && row.id() == parsed.id());
    if (!currentCandidate) {
      throw notFound();
    }
    return gateway.fetchHtml(tutor, parsed.kind(), parsed.id())
        .orElseThrow(MidtermWikiSourceService::notFound);
  }

  record Context(Academy academy, MidtermCampaign campaign) {}
  record ParsedSourceId(String kind, long id) {}

  Context requirePublishedCampaign(String handle) {
    Academy academy = academyRepository.findByHandle(handle)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학원을 찾을 수 없습니다."));
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .filter(MidtermCampaign::isPublished)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개된 캠페인이 없습니다."));
    return new Context(academy, campaign);
  }

  static WikiCampaignScope scope(MidtermCampaign campaign) {
    return new WikiCampaignScope(
        campaign.getExamYear(), campaign.getSemester(), campaign.getExamType());
  }

  static AuthenticatedPrincipal tutorPrincipal(Academy academy) {
    User tutor = academy.getTutor();
    if (tutor == null || tutor.getUserId() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "학원 소유자를 확인할 수 없습니다.");
    }
    return new AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), tutor.getRole().name());
  }

  static String sourceId(String kind, long id) {
    return kind + "-" + id;
  }

  static ParsedSourceId parseSourceId(String sourceId) {
    int sep = sourceId == null ? -1 : sourceId.lastIndexOf('-');
    if (sep <= 0) {
      throw notFound();
    }
    String kind = sourceId.substring(0, sep);
    if (!"school-print".equals(kind) && !"final".equals(kind)) {
      throw notFound();
    }
    try {
      long id = Long.parseLong(sourceId.substring(sep + 1));
      if (id <= 0) {
        throw notFound();
      }
      return new ParsedSourceId(kind, id);
    } catch (NumberFormatException exception) {
      throw notFound();
    }
  }

  private static String key(String kind, long id) {
    return kind + ":" + id;
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 대상 자료가 아닙니다.");
  }

  private static WikiSourceView toView(String handle, String sourceId, WikiSourceRow row) {
    String kind = "school-print".equals(row.kind()) ? "SCHOOL_PRINT" : "FINAL";
    String label = "school-print".equals(row.kind()) ? "학교 프린트" : "완성 자료";
    String previewUrl = "/api/academies/" + handle
        + "/midterm-campaign/wiki-sources/" + sourceId + "/preview";
    return new WikiSourceView(sourceId, kind, label, row.title(), row.school(), row.grade(),
        row.year(), row.semester(), row.exam(), row.createdAt(), previewUrl);
  }
}
