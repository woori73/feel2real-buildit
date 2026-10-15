package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 입시상담 이력 — 비회원 세션 토큰 기반.
 * NewStart 상담 이력과 완전 분리. 개인정보 저장 금지.
 */
@Entity
@Table(name = "admissions_counseling_sessions",
        indexes = {
                @Index(name = "idx_admissions_session_token", columnList = "session_token"),
                @Index(name = "idx_admissions_session_fingerprint", columnList = "question_fingerprint"),
                @Index(name = "idx_admissions_session_created", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsCounselingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "session_token", length = 64, nullable = false)
    private String sessionToken;

    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(name = "question_fingerprint", length = 64, nullable = false)
    private String questionFingerprint;

    @Column(name = "answer", columnDefinition = "LONGTEXT")
    private String answer;

    @Column(name = "evidence_summary", columnDefinition = "TEXT")
    private String evidenceSummary;

    @Column(name = "student_profile", columnDefinition = "TEXT")
    private String studentProfile;

    @Column(name = "academic_year")
    private Integer academicYear;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
