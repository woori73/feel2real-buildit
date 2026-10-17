package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

/** 상담신청 처리 상태. 신규 → 처리중 → 완료. */
public enum InquiryStatus {
  NEW,
  IN_PROGRESS,
  DONE
}
