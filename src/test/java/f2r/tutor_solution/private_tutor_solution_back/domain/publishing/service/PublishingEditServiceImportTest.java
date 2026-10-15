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
    when(jobService.attachManuscript(any(), any(), any())).thenReturn("manuscript.md");
    when(versions.save(any())).thenAnswer(i -> {
      PublishingVersion v = i.getArgument(0);
      ReflectionTestUtils.setField(v, "id", 42L);
      return v;
    });
    when(cli.pageCount(any(), any())).thenReturn(20);
  }

  private final MockMultipartFile file =
      new MockMultipartFile("manuscript", "교재.md", "text/markdown", "# 제목".getBytes());

  @Test
  void importManuscript_snapshotsThenPlacesSelectsAndAppliesBodyStyleInOneScript() {
    // given / when
    var r = service.importManuscript(principal, 1L, file);
    // then
    ArgumentCaptor<List<PublishingCommand>> cmds = ArgumentCaptor.forClass(List.class);
    verify(cli).executeOnDocument(cmds.capture(), any());
    assertThat(cmds.getValue()).extracting(PublishingCommand::id)
        .containsExactly("file.place", "edit.selectAll", "style.paragraph.apply");
    assertThat(cmds.getValue().get(0).params()).containsEntry("path", "manuscript.md").containsEntry("autoflow", true);
    assertThat(r.snapshotId()).isEqualTo(42L);
    assertThat(r.pageCount()).isEqualTo(20);
    assertThat(r.fileName()).isEqualTo("교재.md");
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
}
