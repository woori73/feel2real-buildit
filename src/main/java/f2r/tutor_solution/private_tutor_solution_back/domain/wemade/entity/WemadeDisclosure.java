package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 공시 목록 RAW. 원문 자동수집은 DART API 키 필요(BLOCKED). */
@Entity
@Table(name = "wemade_disclosures")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeDisclosure extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate disclosedAt;
    @Column(length = 500, nullable = false)
    private String title;
    @Column(length = 100)
    private String disclosureNo;
    /** DART 접수번호 (공식 식별자, 예: 20260630900888) */
    @Column(length = 30)
    private String rcpNo;
    /** DART 문서번호 (원문 viewer용) */
    @Column(length = 30)
    private String dcmNo;
    /** KOSCOM 문서번호 (예: 49601334) — DART rcpNo와 다름 */
    @Column(length = 30)
    private String koscomDocNo;
    @Builder.Default
    private String source = "DART_LIST";
    @Column(length = 1000)
    private String sourceUrl;
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private LocalDateTime collectedAt;
}
