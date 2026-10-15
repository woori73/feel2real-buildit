package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingVersionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 원고 반입: 스냅샷 → (반입·전체선택·ko-body) 한 script → 쪽수 반환. */
class PublishingEditServiceImportTest {

  @TempDir Path dir;
  PublishingJobService jobService = mock(PublishingJobService.class);
  DesignCraftCliExecutor cli = mock(DesignCraftCliExecutor.class);
  PublishingVersionRepository versions = mock(PublishingVersionRepository.class);
  PublishingEditService service;
  AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
  PublishingJob job;

  @BeforeEach
  void setUp() throws Exception {
    service = new PublishingEditService(jobService, cli, mock(CommandAllowlist.class),
        mock(StyleParamNormalizer.class), mock(MarkdownStyleService.class), versions, new ObjectMapper());
    job = new PublishingJob(1L, "t", PublishingJob.Template.KO_TEXTBOOK_A4);
    Path doc = dir.resolve("job.designcraft");
    Files.writeString(doc, "DOC");
    job.markRunning(dir.toString(), doc.toString());
    when(jobService.requireOwned(any(), any())).thenReturn(job);
    when(jobService.attachManuscript(any(), any(), any())).thenReturn("manuscript.txt");
    when(versions.save(any())).thenAnswer(i -> {
      PublishingVersion v = i.getArgument(0);
      ReflectionTestUtils.setField(v, "id", 42L);
      return v;
    });
    when(cli.pageCount(any(), any())).thenReturn(20);
    when(cli.inspect(any(), any())).thenReturn(new ObjectMapper().createObjectNode());
  }

  private final MockMultipartFile file =
      new MockMultipartFile("manuscript", "교재.txt", "text/plain", "# 제목".getBytes());

  @Test
  void importManuscript_snapshotsThenPlacesSelectsAndAppliesBodyStyleInOneScript() {
    // given / when
    var r = service.importManuscript(principal, 1L, file);
    // then
    ArgumentCaptor<List<PublishingCommand>> cmds = ArgumentCaptor.forClass(List.class);
    verify(cli, org.mockito.Mockito.atLeastOnce()).executeOnDocument(cmds.capture(), any());
    assertThat(cmds.getAllValues().get(0)).extracting(PublishingCommand::id)
        .containsExactly("file.place", "edit.selectAll", "style.paragraph.apply");
    assertThat(cmds.getAllValues().get(0).get(0).params()).containsEntry("path", "manuscript.txt").containsEntry("autoflow", true);
    assertThat(r.snapshotId()).isEqualTo(42L);
    assertThat(r.pageCount()).isEqualTo(20);
    assertThat(r.fileName()).isEqualTo("교재.txt");
  }

  @Test
  void importManuscript_whenCliFails_reportsReason_afterSnapshot() {
    // given
    when(cli.executeOnDocument(anyList(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "boom"));
    // when/then
    assertThatThrownBy(() -> service.importManuscript(principal, 1L, file))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("원고 반입 실패");
    verify(cli, never()).pageCount(any(), any());
  }

  private static final String DOC_JSON = """
      {"pageCount":3,
       "spreads":[
         {"index":0,"pages":[{"index":0,"bounds":[0,0,595,842]}],
          "items":[{"id":19,"kind":"<text frame>","bounds":[62,56,544,785]}]},
         {"index":1,"pages":[{"index":1,"bounds":[0,0,595,842]},{"index":2,"bounds":[595,0,1190,842]}],
          "items":[{"id":48,"kind":"<text frame>","bounds":[51,56,533,785]},
                   {"id":50,"kind":"<text frame>","bounds":[657,56,1139,785]},
                   {"id":99,"kind":"<graphic frame>","bounds":[700,56,900,200]}]}
       ]}""";

  @Test
  void expand_pageStyleCommand_selectsThatPagesTextFramesThenAppliesByName() throws Exception {
    // given: 3쪽(index 2)은 오른쪽 쪽 — 프레임 50만 해당, 그래픽 프레임 제외
    when(cli.inspect(any(), any())).thenReturn(new ObjectMapper().readTree(DOC_JSON));
    var cmd = new PublishingCommand("style.paragraph.apply", Map.of("page", 3, "style", "ko-heading1"));
    // when
    var steps = service.expand(cmd, job, dir);
    // then
    assertThat(steps).extracting(PublishingCommand::id).containsExactly("selection.set", "style.paragraph.apply");
    assertThat(steps.get(0).params().get("ids")).isEqualTo(List.of(50L));
    assertThat(steps.get(1).params()).containsEntry("name", "ko-heading1").doesNotContainKey("page");
  }

  @Test
  void expand_nameOnlyStyleCommand_passesThroughUnchanged() {
    // given
    var cmd = new PublishingCommand("style.paragraph.apply", Map.of("name", "ko-body"));
    // when
    var steps = service.expand(cmd, job, dir);
    // then
    assertThat(steps).containsExactly(cmd);
  }

  @Test
  void expand_pageWithoutTextFrame_isBadRequest() throws Exception {
    // given
    when(cli.inspect(any(), any())).thenReturn(new ObjectMapper().readTree(DOC_JSON));
    var cmd = new PublishingCommand("style.paragraph.apply", Map.of("page", 9, "style", "ko-body"));
    // when/then
    assertThatThrownBy(() -> service.expand(cmd, job, dir))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("텍스트 프레임이 없습니다");
  }
}
