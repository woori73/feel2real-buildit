package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="choonggi_media") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ChoonggiMedia extends BaseEntity {
  public enum Type { PHOTO, VIDEO }
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="media_id") private Long id;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="post_id") private ChoonggiPost post;
  @Column(name="storage_key",nullable=false,length=700) private String storageKey;
  @Enumerated(EnumType.STRING) @Column(name="media_type",nullable=false,length=10) private Type type;
  @Column(name="content_type",nullable=false,length=100) private String contentType;
  @Column(name="original_filename",nullable=false,length=255) private String originalFilename;
  @Column(name="size_bytes",nullable=false) private long sizeBytes;
  private Integer width; private Integer height; @Column(name="duration_ms") private Long durationMs;
  @Column(name="sort_order",nullable=false) private int sortOrder;
  @Column(name="cover_media",nullable=false) private boolean cover;
  @Column(name="deleted_at") private LocalDateTime deletedAt;
  public static ChoonggiMedia of(ChoonggiPost post,String key,Type type,String contentType,String name,long size,Integer width,Integer height,int order){var m=new ChoonggiMedia();m.post=post;m.storageKey=key;m.type=type;m.contentType=contentType;m.originalFilename=name;m.sizeBytes=size;m.width=width;m.height=height;m.sortOrder=order;return m;}
  public void setCover(boolean value){cover=value;} public void move(int order){sortOrder=order;} public void softDelete(){deletedAt=LocalDateTime.now();cover=false;}
}
