package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiExamContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceListResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermSchoolCollection;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermSchoolCollectionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * TUTOR Wiki(Python) HTML 자료를 Midterm LIVE 의 read-only visual source 로 연결한다.
 * <b>데이터 연결만</b> — 자료를 복제하지 않고, {@code MidtermMaterial} authoring 을 확장하지 않으며,
 * 공개 페이지에 아직 표시하지 않는다.
 *
 * <p>allowlist = 발행 캠페인의 <b>발행된 school collection</b> 이 가진 (school, grade) +
 * 캠페인 (examYear, semester, examType) 컨텍스트와 <b>정확히 일치</b>하는 Wiki 자료.
 * 값을 저장된 그대로 비교하고, 일치하지 않으면 노출하지 않는다(정규화·fallback·전체 공개 없음).
 */
@Service
@RequiredArgsConstructor
public class MidtermWikiSourceService {

  private final AcademyRepository academyRepository;
  private final MidtermCampaignRepository campaignRepository;
  private final MidtermSchoolCollectionRepository schoolCollectionRepository;
  private final MidtermWikiSourceGateway gateway;

  @Transactional(readOnly = true)
  public WikiSourceListResult listSources(String handle) {
    Context context = requirePublishedCampaign(handle);
    AuthenticatedPrincipal tutor = tutorPrincipal(context.academy());

    List<MidtermSchoolCollection> collections = schoolCollectionRepository
        .findByCampaignIdAndPublishedTrueOrderBySortOrderAscSchoolCollectionIdAsc(
            context.campaign().getCampaignId());

    List<WikiSourceView> views = new ArrayList<>();
    List<WikiExamContext> unmatched = new ArrayList<>();
    Set<String> seenSourceIds = new HashSet<>();
    Set<String> seenContexts = new HashSet<>();

    for (MidtermSchoolCollection collection : collections) {
      WikiExamContext ctx = new WikiExamContext(
          collection.getSchoolName(),
          String.valueOf(collection.getGrade()),
          context.campaign().getExamYear(),
          context.campaign().getSemester(),
          context.campaign().getExamType());
      if (!seenContexts.add(ctx.toString())) {
        continue;
      }
      List<WikiSourceRow> rows = gateway.listByContext(tutor, ctx);
      if (rows.isEmpty()) {
        unmatched.add(ctx);
        continue;
      }
      for (WikiSourceRow row : rows) {
        String sourceId = sourceId(row.kind(), row.id());
        if (seenSourceIds.add(sourceId)) {
          views.add(toView(handle, sourceId, row));
        }
      }
    }
    return new WikiSourceListResult(views, unmatched);
  }

  /** allowlist 안의 자료만. body(HTML) bytes 만 돌려준다 — 헤더는 컨트롤러가 새로 구성한다. */
  @Transactional(readOnly = true)
  public byte[] previewHtml(String handle, String sourceId) {
    ParsedSourceId parsed = parseSourceId(sourceId);
    WikiSourceListResult allowed = listSources(handle);
    boolean inAllowlist = allowed.views().stream()
        .anyMatch(view -> view.sourceId().equals(sourceId));
    if (!inAllowlist) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 대상 자료가 아닙니다.");
    }
    Context context = requirePublishedCampaign(handle);
    return gateway.fetchHtml(tutorPrincipal(context.academy()), parsed.kind(), parsed.id())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
  }

  // ── 내부 ──────────────────────────────────────────────────────────────────
  private record Context(Academy academy, MidtermCampaign campaign) {}

  private record ParsedSourceId(String kind, long id) {}

  private Context requirePublishedCampaign(String handle) {
    Academy academy = academyRepository.findByHandle(handle)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학원을 찾을 수 없습니다."));
    MidtermCampaign campaign = campaignRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .filter(MidtermCampaign::isPublished)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개된 캠페인이 없습니다."));
    return new Context(academy, campaign);
  }

  private AuthenticatedPrincipal tutorPrincipal(Academy academy) {
    User tutor = academy.getTutor();
    if (tutor == null || tutor.getUserId() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "학원 소유자를 확인할 수 없습니다.");
    }
    return new AuthenticatedPrincipal(tutor.getUserId(), tutor.getLoginId(), tutor.getRole().name());
  }

  private static String sourceId(String kind, long id) {
    return kind + "-" + id;
  }

  private static ParsedSourceId parseSourceId(String sourceId) {
    int sep = sourceId == null ? -1 : sourceId.lastIndexOf('-');
    if (sep <= 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 식별자가 올바르지 않습니다.");
    }
    String kind = sourceId.substring(0, sep);
    if (!"school-print".equals(kind) && !"final".equals(kind)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 식별자가 올바르지 않습니다.");
    }
    long id;
    try {
      id = Long.parseLong(sourceId.substring(sep + 1));
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 식별자가 올바르지 않습니다.");
    }
    if (id <= 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 식별자가 올바르지 않습니다.");
    }
    return new ParsedSourceId(kind, id);
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
