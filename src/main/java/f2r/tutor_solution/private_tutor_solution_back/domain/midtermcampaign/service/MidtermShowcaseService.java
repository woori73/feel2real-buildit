package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermShowcaseDtos.ShowcaseScene;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiCampaignScope;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermWikiSourceExposure;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermWikiSourceExposureRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 페이지 상단 "중간고사 준비 중" Showcase(Phase 4e).
 *
 * <p>하단 LIVE collection 과 <b>같은 (school, grade)</b> — visible wiki-source exposure ∩ 현재
 * campaign candidate — 를 학교당 대표 1건으로 묶고, 각 자료의 이미 인가된 HTML 을 서버에서
 * {@link MidtermShowcaseExtractor} 로 bounded parsing 해 텍스트만 담은 {@link ShowcaseScene} 을
 * 반환한다. HTML 원문 / 파일 URL / storageKey 는 노출하지 않는다.
 *
 * <p>미발행 캠페인 / 자료 없음 → 빈 리스트(프런트가 짧은 시즌 화면으로 폴백). Wiki 왕복은
 * {@code sourceId@createdAt} 키의 in-memory 캐시로 완화한다(새 업로드 → createdAt 변경 → 재계산).
 */
@Service
@RequiredArgsConstructor
public class MidtermShowcaseService {

  private static final int MAX_SCENES = 6;
  private static final int CACHE_CAP = 200;

  private final AcademyRepository academyRepository;
  private final MidtermCampaignRepository campaignRepository;
  private final MidtermWikiSourceExposureRepository exposureRepository;
  private final MidtermWikiSourceGateway gateway;

  private final Map<String, MidtermShowcaseExtractor.Extract> cache = new ConcurrentHashMap<>();

  @Transactional(readOnly = true)
  public List<ShowcaseScene> getShowcase(String handle) {
    Academy academy = academyRepository.findByHandle(handle).orElse(null);
    if (academy == null) {
      return List.of();
    }
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .filter(MidtermCampaign::isPublished)
        .orElse(null);
    if (campaign == null) {
      return List.of();
    }

    WikiCampaignScope scope = MidtermWikiSourceService.scope(campaign);
    AuthenticatedPrincipal tutor = MidtermWikiSourceService.tutorPrincipal(academy);
    Map<String, WikiSourceRow> byKey;
    try {
      byKey = gateway.listCandidates(tutor, scope).stream()
          .collect(Collectors.toMap(r -> r.kind() + ":" + r.id(), Function.identity(),
              (first, ignored) -> first));
    } catch (RuntimeException e) {
      return List.of();
    }
    if (byKey.isEmpty()) {
      return List.of();
    }

    List<MidtermWikiSourceExposure> visible = exposureRepository
        .findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(campaign.getCampaignId());

    String examLabel = campaign.getExamType();
    Set<String> seenSchoolGrade = new LinkedHashSet<>();
    List<ShowcaseScene> scenes = new ArrayList<>();
    for (MidtermWikiSourceExposure exposure : visible) {
      if (scenes.size() >= MAX_SCENES) {
        break;
      }
      WikiSourceRow row = byKey.get(exposure.getSourceKind() + ":" + exposure.getSourceId());
      if (row == null) {
        continue;
      }
      Integer grade = gradeNumber(row.grade());
      String school = row.school() == null ? "" : row.school().trim();
      if (grade == null || school.isEmpty()) {
        continue;
      }
      if (!seenSchoolGrade.add(school + "|" + grade)) {
        continue;   // 학교당 대표(정렬상 첫) 1건만
      }

      MidtermShowcaseExtractor.Extract extract = extractCached(tutor, row);
      scenes.add(new ShowcaseScene(
          school, grade, examLabel,
          extract.lesson(),
          extract.grammarPoints(),
          extract.sampleSentences(),
          MidtermWikiSourceService.sourceId(row.kind(), row.id())));
    }
    return scenes;
  }

  private MidtermShowcaseExtractor.Extract extractCached(AuthenticatedPrincipal tutor, WikiSourceRow row) {
    String key = row.kind() + "-" + row.id() + "@" + (row.createdAt() == null ? "" : row.createdAt());
    MidtermShowcaseExtractor.Extract hit = cache.get(key);
    if (hit != null) {
      return hit;
    }
    MidtermShowcaseExtractor.Extract computed;
    try {
      Optional<byte[]> html = gateway.fetchHtml(tutor, row.kind(), row.id());
      computed = html
          .map(bytes -> MidtermShowcaseExtractor.from(new String(bytes, StandardCharsets.UTF_8)))
          .orElse(MidtermShowcaseExtractor.Extract.EMPTY);
    } catch (RuntimeException e) {
      computed = MidtermShowcaseExtractor.Extract.EMPTY;
    }
    if (cache.size() >= CACHE_CAP) {
      cache.clear();
    }
    cache.put(key, computed);
    return computed;
  }

  private static Integer gradeNumber(String value) {
    if (value == null) {
      return null;
    }
    var m = java.util.regex.Pattern.compile("\\d+").matcher(value);
    return m.find() ? Integer.parseInt(m.group()) : null;
  }
}
