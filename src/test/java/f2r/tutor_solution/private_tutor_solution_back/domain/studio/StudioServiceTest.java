package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class StudioServiceTest {
  @TempDir Path dir;

  private StudioService.Revision revision(Integer episode, Integer cut, String origin) {
    return new StudioService.Revision(
        "ep01-script",
        null,
        "r14",
        "SCRIPT",
        episode,
        cut,
        origin,
        "제목",
        "본문",
        "대사",
        "연출",
        "WAITING",
        null);
  }

  @Test
  void givenProposal_whenValidated_thenRemainsProposal() {
    var r = revision(1, null, "AI_PROPOSAL");
    assertDoesNotThrow(() -> StudioService.validate(r));
    assertEquals("AI_PROPOSAL", r.origin());
  }

  @Test
  void givenOutOfRangeEpisodeOrCut_whenValidated_thenRejected() {
    assertThrows(
        ResponseStatusException.class,
        () -> StudioService.validate(revision(15, null, "ORIGINAL")));
    assertThrows(
        ResponseStatusException.class, () -> StudioService.validate(revision(1, 69, "ORIGINAL")));
    assertThrows(
        ResponseStatusException.class, () -> StudioService.validate(revision(null, 1, "ORIGINAL")));
  }

  @Test
  void givenSpoofedPng_whenValidated_thenRejected() throws Exception {
    Path p = dir.resolve("fake.png");
    Files.writeString(p, "<script>bad</script>");
    assertThrows(ResponseStatusException.class, () -> StudioService.validateType(p, "png"));
  }

  @Test
  void givenNonUtf8_whenValidated_thenRejected() throws Exception {
    Path p = dir.resolve("bad.md");
    Files.write(p, new byte[] {(byte) 0xff, 0});
    assertThrows(Exception.class, () -> StudioService.validateType(p, "md"));
  }

  @Test
  void givenHtml_whenValidated_thenOriginalUnchanged() throws Exception {
    Path p = dir.resolve("original.html");
    byte[] b = "<script>alert('원문')</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(p, b);
    StudioService.validateType(p, "html");
    assertArrayEquals(b, Files.readAllBytes(p));
  }
}
