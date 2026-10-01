package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

/** #6063 fixture 로 STEP 1·2 에 실제로 들어가는 근거·지시를 출력하는 수동 리포트(개선 전/후 비교용). 테스트 아님. */
public final class Cafe6063Report {
  public static void main(String[] args) {
    Cafe6063Fixture.CapturingTransport t1 = new Cafe6063Fixture.CapturingTransport();
    Cafe6063Fixture.CapturingTransport t2 = new Cafe6063Fixture.CapturingTransport();
    var out = Cafe6063Fixture.run(t1, t2);
    System.out.println("===== QUESTION =====\n" + Cafe6063Fixture.QUESTION);
    System.out.println("\n===== STEP 1 — system prompt(지시+근거) =====\n" + t1.system);
    System.out.println("\n===== STEP 1 — sources =====");
    out[0].sources().forEach(s -> System.out.println("- [" + s.kind() + "] " + s.title()));
    System.out.println("\n===== STEP 2 — system prompt(지시+근거) =====\n" + t2.system);
    System.out.println("\n===== STEP 2 — user prompt =====\n" + t2.prompt);
    System.out.println("\n===== STEP 2 — sources =====");
    out[1].sources().forEach(s -> System.out.println("- [" + s.kind() + "] " + s.title()));
  }
}
