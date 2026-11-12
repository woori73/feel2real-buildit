package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PublishingEditServiceExpandTest {

  private PublishingEditService serviceWithLockedStory(DesignCraftCliExecutor cli) {
    return new PublishingEditService(
        mock(PublishingJobService.class),
        cli,
        new CommandAllowlist(),
        mock(StyleParamNormalizer.class),
        mock(MarkdownStyleService.class),
        mock(f2r.tutor_solution.private_tutor_solution_back.domain.publishing.repository.PublishingVersionRepository.class),
        new ObjectMapper());
  }

  @Test
  @DisplayName("잠금 스토리 수정은 userConfirmed 없이 차단된다")
  void blockedWithoutUserConfirmed() {
    // given
    var cli = mock(DesignCraftCliExecutor.class);
    var service = serviceWithLockedStory(cli);
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    job.setLockedStories("[20]");
    var cmd = new PublishingCommand("document.replaceText",
        Map.of("story", 20, "before", "Passage.", "after", "Changed."));
    // when & then
    assertThatThrownBy(() -> service.expand(cmd, job, Path.of("/tmp")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("원문 보호");
  }

  @Test
  @DisplayName("잠금 스토리는 userConfirmed=true인 사용자 명시 수정만 허용된다")
  void allowedWithUserConfirmed() {
    // given
    var cli = mock(DesignCraftCliExecutor.class);
    var result = mock(DesignCraftCliExecutor.CliResult.class);
    when(result.output()).thenReturn("{\"text\":\"Passage.\"}");
    when(cli.execute(any(), any())).thenReturn(result);
    var service = serviceWithLockedStory(cli);
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    job.setLockedStories("[20]");
    var cmd = new PublishingCommand("document.replaceText",
        Map.of("story", 20, "before", "Passage.", "after", "Changed.", "userConfirmed", true));
    // when
    List<PublishingCommand> expanded = service.expand(cmd, job, Path.of("/tmp"));
    // then: story.replaceRange로 변환됨
    assertThat(expanded).hasSize(1);
    assertThat(expanded.get(0).id()).isEqualTo("story.replaceRange");
  }

  @Test
  @DisplayName("잠기지 않은 스토리는 기존대로 동작한다")
  void unlockedStoryPasses() {
    // given
    var cli = mock(DesignCraftCliExecutor.class);
    var result = mock(DesignCraftCliExecutor.CliResult.class);
    when(result.output()).thenReturn("{\"text\":\"일반 문단.\"}");
    when(cli.execute(any(), any())).thenReturn(result);
    var service = serviceWithLockedStory(cli);
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    var cmd = new PublishingCommand("document.replaceText",
        Map.of("story", 21, "before", "일반 문단.", "after", "수정됨."));
    // when
    List<PublishingCommand> expanded = service.expand(cmd, job, Path.of("/tmp"));
    // then
    assertThat(expanded).hasSize(1);
    assertThat(expanded.get(0).id()).isEqualTo("story.replaceRange");
  }
}
