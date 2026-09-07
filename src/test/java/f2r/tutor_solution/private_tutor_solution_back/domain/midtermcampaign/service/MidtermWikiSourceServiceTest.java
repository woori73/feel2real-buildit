package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiExamContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceListResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermSchoolCollection;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermSchoolCollectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

/**
 * TUTOR Wiki 자료 연결의 노출 정책·보안 계약.
 *
 * <ul>
 *   <li>allowlist = 발행 캠페인 + 발행 school collection 컨텍스트와 <b>정확히 일치</b>하는 자료만
 *   <li>일치하지 않으면 목록·preview 어디에도 노출되지 않는다(정규화·fallback 없음)
 *   <li>preview 는 body(HTML) bytes 만 돌려준다 — storageKey/URL/download 미노출
 *   <li>Python 호출은 academy 강사 신원(userId/loginId/role)으로 서명한다
 *   <li>미발행 캠페인/미발행 collection → 노출 없음
 * </ul>
 */
class MidtermWikiSourceServiceTest {

  private AcademyRepository academyRepository;
  private MidtermCampaignRepository campaignRepository;
  private MidtermSchoolCollectionRepository schoolCollectionRepository;
  private MidtermWikiSourceGateway gateway;
  private MidtermWikiSourceService service;

  private Academy academy;

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    campaignRepository = mock(MidtermCampaignRepository.class);
    schoolCollectionRepository = mock(MidtermSchoolCollectionRepository.class);
    gateway = mock(MidtermWikiSourceGateway.class);
    service = new MidtermWikiSourceService(
        academyRepository, campaignRepository, schoolCollectionRepository, gateway);

    User tutor = mock(User.class);
    when(tutor.getUserId()).thenReturn(7L);
    when(tutor.getLoginId()).thenReturn("eduholic-tutor");
    when(tutor.getRole()).thenReturn(Role.TUTOR);
    academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(1L);
    when(academy.getTutor()).thenReturn(tutor);
    when(academyRepository.findByHandle("eduholic")).thenReturn(Optional.of(academy));
  }

  private MidtermCampaign campaign(boolean published) {
    MidtermCampaign campaign = mock(MidtermCampaign.class);
    when(campaign.getCampaignId()).thenReturn(9L);
    when(campaign.isPublished()).thenReturn(published);
    when(campaign.getExamYear()).thenReturn(2026);
    when(campaign.getSemester()).thenReturn("2학기");
    when(campaign.getExamType()).thenReturn("중간고사");
    when(campaignRepository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));
    return campaign;
  }

  /** 반드시 outer when(...) 밖에서 호출한다 — 내부 stubbing 이 완결돼야 한다. */
  private MidtermSchoolCollection collection(String school, int grade) {
    MidtermSchoolCollection collection = mock(MidtermSchoolCollection.class);
    when(collection.getSchoolName()).thenReturn(school);
    when(collection.getGrade()).thenReturn(grade);
    return collection;
  }

  private void stubPublishedCollections(MidtermSchoolCollection... cols) {
    when(schoolCollectionRepository
        .findByCampaignIdAndPublishedTrueOrderBySortOrderAscSchoolCollectionIdAsc(9L))
        .thenReturn(List.of(cols));
  }

  private WikiSourceRow row(String kind, long id) {
    return new WikiSourceRow(kind, id, "제목 " + id, "신구중학교", "2", 2026, "2학기", "중간고사", "2026-09-07");
  }

  @Test
  void listSources_returnsOnlyMaterialsMatchingPublishedCollectionContext() {
    campaign(true);
    MidtermSchoolCollection shingu = collection("신구중학교", 2);
    MidtermSchoolCollection apgujeong = collection("압구정중학교", 3);
    stubPublishedCollections(shingu, apgujeong);
    when(gateway.listByContext(any(), eq(new WikiExamContext("신구중학교", "2", 2026, "2학기", "중간고사"))))
        .thenReturn(List.of(row("school-print", 11), row("final", 22)));
    when(gateway.listByContext(any(), eq(new WikiExamContext("압구정중학교", "3", 2026, "2학기", "중간고사"))))
        .thenReturn(List.of());

    WikiSourceListResult result = service.listSources("eduholic");

    assertThat(result.views()).extracting(WikiSourceView::sourceId)
        .containsExactlyInAnyOrder("school-print-11", "final-22");
    assertThat(result.views()).allSatisfy(view -> {
      assertThat(view.previewUrl())
          .isEqualTo("/api/academies/eduholic/midterm-campaign/wiki-sources/" + view.sourceId() + "/preview");
      // storageKey / 원본 URL / download 링크가 payload 에 없다.
      assertThat(view.toString()).doesNotContain("storage", "8000", "/download", "/html", "/original");
    });
    // 매칭 0건 컨텍스트는 진단으로만 남고 공개되지 않는다.
    assertThat(result.unmatchedContexts())
        .containsExactly(new WikiExamContext("압구정중학교", "3", 2026, "2학기", "중간고사"));
  }

  @Test
  void listSources_queriesPythonWithSynthesizedTutorIdentity() {
    campaign(true);
    MidtermSchoolCollection shingu = collection("신구중학교", 2);
    stubPublishedCollections(shingu);
    when(gateway.listByContext(any(), any())).thenReturn(List.of());

    service.listSources("eduholic");

    ArgumentCaptor<AuthenticatedPrincipal> principal = ArgumentCaptor.forClass(AuthenticatedPrincipal.class);
    verify(gateway).listByContext(principal.capture(), any());
    assertThat(principal.getValue().userId()).isEqualTo(7L);
    assertThat(principal.getValue().loginId()).isEqualTo("eduholic-tutor");
    assertThat(principal.getValue().role()).isEqualTo("TUTOR");
  }

  @Test
  void listSources_unpublishedCampaign_404_andNeverCallsPython() {
    campaign(false);

    assertThatThrownBy(() -> service.listSources("eduholic"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("공개된 캠페인이 없습니다");
    verify(gateway, never()).listByContext(any(), any());
  }

  @Test
  void listSources_noPublishedCollections_returnsEmpty_andNeverCallsPython() {
    campaign(true);
    when(schoolCollectionRepository
        .findByCampaignIdAndPublishedTrueOrderBySortOrderAscSchoolCollectionIdAsc(9L))
        .thenReturn(List.of());

    WikiSourceListResult result = service.listSources("eduholic");

    assertThat(result.views()).isEmpty();
    assertThat(result.unmatchedContexts()).isEmpty();
    verify(gateway, never()).listByContext(any(), any());
  }

  @Test
  void previewHtml_returnsBodyBytes_onlyForAllowlistedSource() {
    campaign(true);
    MidtermSchoolCollection shingu = collection("신구중학교", 2);
    stubPublishedCollections(shingu);
    when(gateway.listByContext(any(), any())).thenReturn(List.of(row("school-print", 11)));
    byte[] bytes = "<html><body>ok</body></html>".getBytes();
    when(gateway.fetchHtml(any(), eq("school-print"), eq(11L))).thenReturn(Optional.of(bytes));

    assertThat(service.previewHtml("eduholic", "school-print-11")).isSameAs(bytes);
  }

  @Test
  void previewHtml_rejectsSourceNotInAllowlist_withoutFetchingHtml() {
    campaign(true);
    MidtermSchoolCollection shingu = collection("신구중학교", 2);
    stubPublishedCollections(shingu);
    when(gateway.listByContext(any(), any())).thenReturn(List.of(row("school-print", 11)));

    assertThatThrownBy(() -> service.previewHtml("eduholic", "school-print-999"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("공개 대상 자료가 아닙니다");
    verify(gateway, never()).fetchHtml(any(), any(), anyLong());
  }

  @Test
  void previewHtml_rejectsMalformedSourceId() {
    campaign(true);
    for (String bad : List.of("", "abc", "school-print:", "school-print-0", "school-print--1",
        "school-print-1x", "twin-1", "../api/exam-materials/1/html", "school-print-1/../2")) {
      assertThatThrownBy(() -> service.previewHtml("eduholic", bad))
          .as("sourceId=%s", bad)
          .isInstanceOf(ResponseStatusException.class);
    }
    verify(gateway, never()).fetchHtml(any(), any(), anyLong());
  }

  @Test
  void previewHtml_emptyBodyFromPython_404() {
    campaign(true);
    MidtermSchoolCollection shingu = collection("신구중학교", 2);
    stubPublishedCollections(shingu);
    when(gateway.listByContext(any(), any())).thenReturn(List.of(row("school-print", 11)));
    when(gateway.fetchHtml(any(), eq("school-print"), eq(11L))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.previewHtml("eduholic", "school-print-11"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("자료를 찾을 수 없습니다");
  }
}
