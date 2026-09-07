package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCandidateAdminView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiExposureUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermWikiSourceExposure;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermWikiSourceExposureRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 운영자는 Wiki candidate를 수정하지 않고 공개/숨김/대표/최소 정렬만 관리한다. */
@Service
@RequiredArgsConstructor
public class MidtermWikiSourceAdminService {

  private final AcademyService academyService;
  private final MidtermCampaignRepository campaignRepository;
  private final MidtermWikiSourceExposureRepository exposureRepository;
  private final MidtermWikiSourceGateway gateway;
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public List<WikiCandidateAdminView> listCandidates(String handle) {
    OwnedContext context = requireOwnedCampaign(handle);
    return views(context);
  }

  @Transactional
  public List<WikiCandidateAdminView> updateExposure(
      String handle, String opaqueSourceId, WikiExposureUpdate request) {
    OwnedContext context = requireOwnedCampaign(handle);
    entityManager.lock(context.campaign(), LockModeType.PESSIMISTIC_WRITE);

    MidtermWikiSourceService.ParsedSourceId parsed =
        MidtermWikiSourceService.parseSourceId(opaqueSourceId);
    AuthenticatedPrincipal tutor = MidtermWikiSourceService.tutorPrincipal(context.academy());
    WikiSourceRow candidate = gateway
        .listCandidates(tutor, MidtermWikiSourceService.scope(context.campaign())).stream()
        .filter(row -> row.kind().equals(parsed.kind()) && row.id() == parsed.id())
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "현재 캠페인의 eligible Wiki 자료가 아닙니다."));

    MidtermWikiSourceExposure exposure = exposureRepository
        .findByCampaignIdAndSourceKindAndSourceId(
            context.campaign().getCampaignId(), candidate.kind(), candidate.id())
        .orElseGet(() -> MidtermWikiSourceExposure.builder()
            .campaignId(context.campaign().getCampaignId())
            .sourceKind(candidate.kind())
            .sourceId(candidate.id())
            .visible(false)
            .featured(false)
            .sortOrder(0)
            .build());

    boolean visible = request.visible();
    boolean featured = visible && request.featured();
    if (featured) {
      exposureRepository.clearFeatured(
          context.campaign().getCampaignId(), exposure.getExposureId());
    }
    exposure.update(visible, featured, request.sortOrder());
    exposureRepository.saveAndFlush(exposure);
    return views(context);
  }

  private List<WikiCandidateAdminView> views(OwnedContext context) {
    Map<String, MidtermWikiSourceExposure> exposures = exposureRepository
        .findByCampaignId(context.campaign().getCampaignId()).stream()
        .collect(Collectors.toMap(
            e -> key(e.getSourceKind(), e.getSourceId()), Function.identity()));
    return gateway.listCandidates(
            MidtermWikiSourceService.tutorPrincipal(context.academy()),
            MidtermWikiSourceService.scope(context.campaign())).stream()
        .map(row -> {
          MidtermWikiSourceExposure exposure = exposures.get(key(row.kind(), row.id()));
          return new WikiCandidateAdminView(
              MidtermWikiSourceService.sourceId(row.kind(), row.id()),
              row.kind(), row.school(), row.grade(), row.year(), row.semester(), row.exam(),
              row.createdAt(), true,
              exposure != null && exposure.isVisible(),
              exposure != null && exposure.isFeatured(),
              exposure == null ? 0 : exposure.getSortOrder());
        })
        .toList();
  }

  private OwnedContext requireOwnedCampaign(String handle) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "캠페인을 찾을 수 없습니다."));
    return new OwnedContext(academy, campaign);
  }

  private static String key(String kind, long id) {
    return kind + ":" + id;
  }

  private record OwnedContext(Academy academy, MidtermCampaign campaign) {}
}
