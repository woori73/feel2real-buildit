package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/** Public availability lifecycle. UNAVAILABLE exists only for migrated V930 failures. */
public enum VideoLifecycleStatus {
  READY,
  DELETING,
  UNAVAILABLE
}
