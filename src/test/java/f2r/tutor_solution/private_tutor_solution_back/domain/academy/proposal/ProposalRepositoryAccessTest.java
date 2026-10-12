package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 학원 조건 없는 제안 조회(findById 등)가 main 소스에 생기지 않도록 강제하는 구조 테스트. */
@DisplayName("제안 Repository 접근 규칙")
class ProposalRepositoryAccessTest {

  @Test
  @DisplayName("main 소스 어디에서도 proposalRepository.findById/getById/deleteById를 쓰지 않는다")
  void noIdOnlyAccess() throws IOException {
    Path root = Path.of("src/main/java");
    try (Stream<Path> files = Files.walk(root)) {
      List<String> offenders = files.filter(p -> p.toString().endsWith(".java")).flatMap(p -> {
        try {
          String src = Files.readString(p);
          return Stream.of("findById(", "getById(", "getReferenceById(", "deleteById(", "existsById(")
              .filter(m -> src.matches("(?s).*[pP]roposalRepository\\s*\\.\\s*" + m.replace("(", "\\(") + ".*"))
              .map(m -> p + " uses " + m);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }).toList();
      assertThat(offenders).isEmpty();
    }
  }
}
