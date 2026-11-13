package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 입시상담 AI 화면의 「바뀌는 입시」 같은 해설 글(V1111).
 *
 * <p>상담 AI의 답변 근거(공식자료·지식 항목·검색 인덱스)와 연결하지 않는다 — 해설이며 기사·영상 출처가 섞여 있기 때문이다.
 * 본문은 허용 태그만 남긴 HTML로 저장한다({@code AdmissionsArticleService#sanitize}).
 */
@Entity
@Table(name = "admissions_articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "series", length = 60, nullable = false)
    private String series;

    @Column(name = "episode", length = 20, nullable = false)
    private String episode;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "summary", length = 500, nullable = false)
    private String summary;

    @Column(name = "body_html", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String bodyHtml;

    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
