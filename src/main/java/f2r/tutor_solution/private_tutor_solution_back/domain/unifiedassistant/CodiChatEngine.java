package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import java.util.List;

/** A provider sees only the public messages of one authenticated conversation. */
public interface CodiChatEngine {
  record PublicExchange(String question, String answer) {}
  record SourceLink(String title, String url) {}
  record PublicAnswer(String text, List<SourceLink> sources) {}

  void requireAvailable();
  PublicAnswer reply(List<PublicExchange> history, String question, boolean webSearch);
}
