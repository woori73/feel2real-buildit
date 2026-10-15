package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.config.PublishingProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

/** 가짜 CLI(shell)로 호출 형태·저장·결과 모양을 검증한다. */
class DesignCraftCliExecutorTest {

  @TempDir Path dir;
  Path work;
  PublishingProperties props = new PublishingProperties();

  @BeforeEach
  void setUp() throws Exception {
    work = Files.createDirectories(dir.resolve("work"));
    Files.writeString(work.resolve("job.designcraft"), "ORIGINAL");
  }

  private DesignCraftCliExecutor executorWith(String script) throws Exception {
    Path cli = dir.resolve("fake-cli");
    Files.writeString(cli, "#!/bin/sh\n" + script);
    cli.toFile().setExecutable(true);
    props.setCliPath(cli.toString());
    props.setCliTimeoutSeconds(20);
    return new DesignCraftCliExecutor(props, new ObjectMapper());
  }

  private static final String SCRIPT_OK = """
      if [ "$1" = "script" ]; then
        cat > stdin.txt
        echo "$@" > args.txt
        while [ $# -gt 0 ]; do [ "$1" = "--save" ] && OUT="$2"; [ "$1" = "--in" ] && IN="$2"; shift; done
        { cat "$IN"; echo "+EDIT"; } > "$OUT"
        echo '{"completed":1,"results":[{"pagesAdded":2}]}'
        exit 0
      fi
      echo '{"pageCount":7}'
      """;

  @Test
  void executeOnDocument_runsScriptOnJobDocument_savesChange_andUnwrapsSingleResult() throws Exception {
    // given
    var executor = executorWith(SCRIPT_OK);
    var cmd = new PublishingCommand("file.place", Map.of("path", "manuscript.md", "autoflow", true));
    // when
    var result = executor.executeOnDocument(cmd, work);
    // then
    assertThat(Files.readString(work.resolve("job.designcraft"))).contains("ORIGINAL").contains("+EDIT");
    assertThat(Files.readString(work.resolve("stdin.txt"))).startsWith("file.place={");
    assertThat(Files.readString(work.resolve("args.txt"))).contains("script -").contains("--in").contains("--save");
    assertThat(new ObjectMapper().readTree(result.output()).path("pagesAdded").asInt()).isEqualTo(2);
    assertThat(Files.exists(work.resolve("job.designcraft.saving"))).isFalse();
  }

  @Test
  void executeOnDocument_whenCliFails_keepsOriginalDocument() throws Exception {
    // given
    var executor = executorWith("cat > /dev/null\necho '{\"error\":\"boom\"}'\nexit 1\n");
    // when/then
    assertThatThrownBy(() -> executor.executeOnDocument(new PublishingCommand("file.place", Map.of()), work))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(Files.readString(work.resolve("job.designcraft"))).isEqualTo("ORIGINAL");
  }

  @Test
  void executeOnDocument_withoutDocument_isConflict() throws Exception {
    // given
    var executor = executorWith(SCRIPT_OK);
    Files.delete(work.resolve("job.designcraft"));
    // when/then
    assertThatThrownBy(() -> executor.executeOnDocument(new PublishingCommand("file.place", Map.of()), work))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("준비되지");
  }

  @Test
  void pageCount_readsPageCountFromDocumentInspect() throws Exception {
    // given
    var executor = executorWith(SCRIPT_OK);
    // when
    int pages = executor.pageCount(work.resolve("job.designcraft"), work);
    // then
    assertThat(pages).isEqualTo(7);
  }

  @Test
  void execute_stillUsesPlainRunCommand() throws Exception {
    // given
    var executor = executorWith("echo \"$@\" > args.txt\necho '{}'\n");
    // when
    executor.execute(new PublishingCommand("preflight.run", Map.of("minPpi", 150)), work);
    // then
    assertThat(Files.readString(work.resolve("args.txt"))).startsWith("run --cmd preflight.run=");
  }

  @Test
  void renderPage_passesZeroBasedPageToCli() throws Exception {
    // given
    var executor = executorWith("echo \"$@\" > args.txt\n");
    // when
    executor.renderPage(work.resolve("job.designcraft"), 1, 1.0, work.resolve("out.png"), work);
    executor.renderPage(work.resolve("job.designcraft"), 20, 0.5, work.resolve("out.png"), work);
    // then: 마지막 호출(20쪽) → --page 19
    assertThat(Files.readString(work.resolve("args.txt"))).contains("--page 19 ");
  }

  @Test
  void executeOnDocument_withSeveralCommands_sendsThemInOneScriptInOrder() throws Exception {
    // given
    var executor = executorWith(SCRIPT_OK);
    var cmds = java.util.List.of(
        new PublishingCommand("file.place", Map.of("path", "m.md")),
        new PublishingCommand("edit.selectAll", Map.of()),
        new PublishingCommand("style.paragraph.apply", Map.of("name", "ko-body")));
    // when
    executor.executeOnDocument(cmds, work);
    // then
    var lines = Files.readAllLines(work.resolve("stdin.txt"));
    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).startsWith("file.place=");
    assertThat(lines.get(1)).startsWith("edit.selectAll");
    assertThat(lines.get(2)).startsWith("style.paragraph.apply=");
  }
}
