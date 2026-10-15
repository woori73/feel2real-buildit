package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageLayoutService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVisitStatisticsService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.LessonCollectionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.LessonProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcademyControllerTest {

  private AcademyService academyService;
  private AcademyPageLayoutService academyPageLayoutService;
  private AcademyController controller;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    LessonProductService lessonProductService = mock(LessonProductService.class);
    LessonCollectionService lessonCollectionService = mock(LessonCollectionService.class);
    academyPageLayoutService = mock(AcademyPageLayoutService.class);
    controller = new AcademyController(
        academyService,
        lessonProductService,
        lessonCollectionService,
        academyPageLayoutService,
        mock(AcademyVisitStatisticsService.class));
  }


  @Test
  void createAcademy_acceptsMultipartWithoutLogoPart() throws Exception {
    // given
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(new JacksonJsonHttpMessageConverter())
        .build();
    when(academyService.create(any(), any(), isNull(), isNull())).thenReturn(null);
    AcademyUpsertRequest request = new AcademyUpsertRequest(
        "샘플 Academy",
        "sample-academy",
        "소개",
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울시", null)),
        null);
    MockMultipartFile academyPart = new MockMultipartFile(
        "academy",
        "academy.json",
        MediaType.APPLICATION_JSON_VALUE,
        new ObjectMapper().writeValueAsBytes(request));

    // when & then
    mockMvc.perform(multipart("/api/tutor/academy")
            .file(academyPart))
        .andExpect(status().isOk());
    verify(academyService).create(any(), any(AcademyUpsertRequest.class), isNull(), isNull());
  }

  @Test
  void createAcademyJson_acceptsJsonBodyAndDelegatesToCreateWithoutLogo() throws Exception {
    // given
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(new JacksonJsonHttpMessageConverter())
        .build();
    when(academyService.create(any(), any(), isNull(), isNull())).thenReturn(null);
    AcademyUpsertRequest request = new AcademyUpsertRequest(
        "샘플 Academy",
        "sample-academy",
        "소개",
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울시", null)),
        null);

    // when & then: application/json → createAcademyJson → create(logo/cover null)
    mockMvc.perform(post("/api/tutor/academy")
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsBytes(request)))
        .andExpect(status().isOk());
    verify(academyService).create(any(), any(AcademyUpsertRequest.class), isNull(), isNull());
  }


  @Test
  void updateAcademyJson_acceptsJsonBodyAndDelegatesToUpdateWithoutFiles() throws Exception {
    // given
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(new JacksonJsonHttpMessageConverter())
        .build();
    when(academyService.update(any(), any(), isNull(), isNull())).thenReturn(null);
    AcademyUpsertRequest request = new AcademyUpsertRequest(
        "수정 Academy",
        "sample-academy",
        "수정 소개",
        Set.of(1),
        Set.of(TargetGroup.MIDDLE),
        List.of(new AcademyRegionRequest("서울시", null)),
        null);

    // when & then: application/json → updateAcademyJson → update(logo/cover null)
    mockMvc.perform(put("/api/tutor/academy")
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsBytes(request)))
        .andExpect(status().isOk());
    verify(academyService).update(any(), any(AcademyUpsertRequest.class), isNull(), isNull());
  }

  @Test
  void updatePageSettings_delegatesToService() {
    // given
    AcademyPageSettingsUpdateRequest request = new AcademyPageSettingsUpdateRequest(
        null, null, null, null, java.util.Map.of("sec-payment", false), null);

    // when
    controller.updatePageSettings(null, request);

    // then
    verify(academyService).updatePageSettings(null, request);
  }

  @Test
  void listPublicAcademies_passesFiltersToService() {
    // given
    when(academyService.listPublic(1, TargetGroup.HIGH, "서울시", "서초구"))
        .thenReturn(List.of());

    // when
    controller.listPublicAcademies(1, TargetGroup.HIGH, "서울시", "서초구");

    // then
    verify(academyService).listPublic(1, TargetGroup.HIGH, "서울시", "서초구");
  }

  @Test
  void checkHandleAvailable_returnsServiceResultAsBooleanBody() {
    // given
    when(academyService.isHandleAvailable(any(), org.mockito.ArgumentMatchers.eq("minji-eng")))
        .thenReturn(true);

    // when
    ResponseEntity<java.util.Map<String, Boolean>> response =
        controller.checkHandleAvailable(null, "minji-eng");

    // then
    assertThat(response.getBody()).containsEntry("available", true);
    verify(academyService).isHandleAvailable(any(), org.mockito.ArgumentMatchers.eq("minji-eng"));
  }

  @Test
  void getPublicLayout_resolvesSchemaForPublishedAcademy() {
    // given
    String slug = "sample-english-academy";
    Academy academy = mock(Academy.class);
    AcademyPageLayoutSchema schema = new AcademyPageLayoutSchema(1, List.of());
    when(academyService.getPublishedEntity(slug)).thenReturn(academy);
    when(academyPageLayoutService.resolveLayoutSchema(academy)).thenReturn(schema);

    // when
    ResponseEntity<AcademyPageLayoutSchema> response = controller.getPublicLayout(slug);

    // then
    assertThat(response.getBody()).isSameAs(schema);
    verify(academyService).getPublishedEntity(slug);
    verify(academyPageLayoutService).resolveLayoutSchema(academy);
  }
}
