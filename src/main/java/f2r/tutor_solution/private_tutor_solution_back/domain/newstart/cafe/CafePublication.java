package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 발행 이력은 append-only이며 네이버 글의 수정·삭제 API가 아니다. */
@Entity
@Table(name = "newstart_cafe_publications")
public class CafePublication {
  @Id @Column(length = 36) private String id;
  @Column(name = "owner_id", nullable = false) private Long ownerId;
  @Column(name = "grant_id", nullable = false, length = 36) private String grantId;
  @Column(name = "cafe_id", nullable = false) private Long cafeId;
  @Column(name = "menu_id", nullable = false) private Integer menuId;
  @Column(name = "board_key", nullable = false, length = 40) private String boardKey;
  @Column(name = "key_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)") private String keyHash;
  @Column(name = "payload_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)") private String payloadHash;
  @Column(nullable = false, length = 150) private String title;
  @Column(nullable = false, columnDefinition = "TEXT") private String content;
  @Column(nullable = false, length = 12) private String state;
  @Column(name = "article_id") private Long articleId;
  @Column(name = "article_url", length = 250) private String articleUrl;
  @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)") private LocalDateTime createdAt;
  @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)") private LocalDateTime updatedAt;
  protected CafePublication() {}
}
