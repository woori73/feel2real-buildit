package f2r.tutor_solution.private_tutor_solution_back.domain.user.entity;

public enum Role {
  STUDENT,
  ADMIN,
  TUTOR,
  // CHOONGGI 전용 콘텐츠 운영자. GLOBAL ADMIN이 아니다 — SecurityConfig에서 /choonggi/admin/**,
  // /api/admin/choonggi/** 에만 ADMIN과 함께 허용되고, 그 외 /api/admin/** 은 여전히 ADMIN 전용이다.
  CREATOR
}