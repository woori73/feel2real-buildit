package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import java.nio.file.Path;

/** 러너 전용: 병합된 PdfMarkTitle.restoreTitle을 같은 PDF(pre)에 직접 적용한다. */
public class RestoreMain {
  public static void main(String[] args) throws Exception {
    PdfMarkTitle.restoreTitle(Path.of(args[0]), args[1]);
    System.out.println("restored " + args[0] + " -> title=" + args[1]);
  }
}
