package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.PreHighEnglishController.Action;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Reading;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Round;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Word;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class PreHighGrade2ReadingTest {
  private static final String ORIGIN = "https://feel2real.co.kr";
  private static final String WEBP = "UklGRgAAAABXRUJQ";
  private static final Reading STANDALONE = new Reading(WEBP, null, 1, "풀이", List.of());
  private static final Reading PAIRED = new Reading(WEBP, WEBP, 2, "공통 지문 풀이", List.of());
  private static final Word WORD = new Word("example", List.of("예", "설명", "증명", "결과", "시작"), 1);
  private final ObjectMapper json = new ObjectMapper();

  @TempDir Path directory;

  @Test
  void existingEditionCanBootAndTheAdministratorCanReplaceItWithTheRevisedEdition() throws Exception {
    byte[] legacy = edition(48);
    Path previousPackage = directory.resolve("part5.json");
    Files.write(previousPackage, legacy);
    var store = new PreHighEnglishStore(json, directory.toString());
    assertThat(store.listGrade2()).hasSize(48);

    store.installGrade2(edition(36));

    assertThat(store.listGrade2()).hasSize(36);
    assertThat(store.getGrade2(37)).isNull();
    assertThat(Files.readAllBytes(previousPackage)).isEqualTo(legacy);
    assertThat(new PreHighEnglishStore(json, directory.toString()).listGrade2()).hasSize(36);
    assertThatThrownBy(() -> store.installGrade2(edition(48))).isInstanceOf(IllegalArgumentException.class);
    assertThat(store.listGrade2()).hasSize(36);
  }

  @Test
  void startIncludesSharedReadingImagesWithoutExposingAnswersAndLaterPostsStaySmall() throws Exception {
    var store = new PreHighEnglishStore(json, directory.toString());
    store.installGrade2(edition(36));
    var controller = new PreHighEnglishController(store, null, null, null, List.of(ORIGIN));
    var principal = new AuthenticatedPrincipal(42L, "test", "ADMIN", null);
    var request = new MockHttpServletRequest();

    var start = controller.actPart5(36, new Action("start", null, 0, 0), principal, request, ORIGIN);
    var startBody = json.valueToTree(start.getBody());

    assertThat(startBody.path("phase").asText()).isEqualTo("READING");
    assertThat(startBody.path("readingResults").isNull()).isTrue();
    assertThat(startBody.path("images").size()).isEqualTo(6);
    assertThat(startBody.path("images").get(4).path("passage").asText()).isEqualTo(WEBP);
    assertThat(startBody.path("images").get(5).path("question").asText()).isEqualTo(WEBP);
    assertThat(startBody.path("images").get(5).has("answer")).isFalse();

    var answer = new Action("read-answer", start.getBody().state().id(), 5, 2);
    var answered = controller.actPart5(36, answer, principal, request, ORIGIN);
    var answeredBody = json.valueToTree(answered.getBody());
    assertThat(answeredBody.has("images")).isFalse();
    assertThat(answeredBody.path("readingAnswers").get(4).asInt()).isEqualTo(2);

    store.installGrade2(edition(36));
    assertThatThrownBy(() -> controller.actPart5(36, answer, principal, request, ORIGIN))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    var refreshed = controller.actPart5(36, new Action("start", null, 0, 0), principal, request, ORIGIN);
    assertThat(refreshed.getBody().state().readingAnswers()).containsOnly(0);
  }

  private byte[] edition(int count) throws Exception {
    return json.writeValueAsBytes(IntStream.rangeClosed(1, count)
        .mapToObj(number -> new Round(number, List.of(STANDALONE, STANDALONE, STANDALONE, STANDALONE, PAIRED, PAIRED),
            java.util.Collections.nCopies(15, WORD))).toList());
  }
}
