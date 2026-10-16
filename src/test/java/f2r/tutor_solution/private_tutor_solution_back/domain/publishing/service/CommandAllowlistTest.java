package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CommandAllowlistTest {

  private CommandAllowlist allowlist;

  @BeforeEach
  void setUp() {
    allowlist = new CommandAllowlist();
  }

  @Test
  @DisplayName("AI 허용 명령은 통과한다")
  void aiAllowedCommandsPass() {
    List<PublishingCommand> commands = List.of(
        new PublishingCommand("style.paragraph.apply", Map.of("name", "ko-heading1", "page", 1)),
        new PublishingCommand("toc.generate", Map.of("title", "목차")),
        new PublishingCommand("edit.undo", Map.of())
    );
    assertDoesNotThrow(() -> allowlist.validateAiPlan(commands));
  }

  @Test
  @DisplayName("내부 전용 명령(file.exportPdf)은 AI plan에서 거부된다")
  void internalCommandsRejected() {
    List<PublishingCommand> commands = List.of(
        new PublishingCommand("file.exportPdf", Map.of("path", "/tmp/out.pdf"))
    );
    assertThrows(ResponseStatusException.class,
        () -> allowlist.validateAiPlan(commands));
  }

  @Test
  @DisplayName("내부 전용 명령(preflight.run)은 AI plan에서 거부된다")
  void preflightRejectedForAi() {
    List<PublishingCommand> commands = List.of(
        new PublishingCommand("preflight.run", Map.of("minPpi", 150))
    );
    assertThrows(ResponseStatusException.class,
        () -> allowlist.validateAiPlan(commands));
  }

  @Test
  @DisplayName("알 수 없는 명령은 거부된다")
  void unknownCommandRejected() {
    List<PublishingCommand> commands = List.of(
        new PublishingCommand("file.delete", Map.of())
    );
    assertThrows(ResponseStatusException.class,
        () -> allowlist.validateAiPlan(commands));
  }

  @Test
  @DisplayName("폰트 변경 시도는 거부된다")
  void fontChangeRejected() {
    List<PublishingCommand> commands = List.of(
        new PublishingCommand("style.paragraph.create", Map.of(
            "name", "ko-body",
            "chars", Map.of("fontFamily", "Arial")))
    );
    assertThrows(ResponseStatusException.class,
        () -> allowlist.validateAiPlan(commands));
  }

  @Test
  @DisplayName("Noto Sans KR 지정은 통과한다")
  void notoSansKrAllowed() {
    List<PublishingCommand> commands = List.of(
        new PublishingCommand("style.paragraph.create", Map.of(
            "name", "ko-body",
            "chars", Map.of("fontFamily", "Noto Sans KR")))
    );
    assertDoesNotThrow(() -> allowlist.validateAiPlan(commands));
  }

  @Test
  @DisplayName("원고 경로 조작(..)은 거부된다")
  void pathTraversalRejected() {
    List<PublishingCommand> commands = List.of(
        new PublishingCommand("file.place", Map.of("path", "../../../etc/passwd"))
    );
    assertThrows(ResponseStatusException.class,
        () -> allowlist.validateAiPlan(commands));
  }

  @Test
  @DisplayName("빈 명령 목록은 거부된다")
  void emptyCommandsRejected() {
    assertThrows(ResponseStatusException.class,
        () -> allowlist.validateAiPlan(List.of()));
  }
}
