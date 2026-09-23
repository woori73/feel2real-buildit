package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="choonggi_posts") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ChoonggiPost extends BaseEntity {
  public enum Status { DRAFT, PUBLISHED }
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="post_id") private Long id;
  @Column(name="episode_code",nullable=false,length=30) private String episodeCode;
  @Column(nullable=false,length=120) private String title;
  @Column(nullable=false,unique=true,length=140) private String slug;
  @Column(name="youtube_video_id",length=20) private String youtubeVideoId;
  @Lob @Column(nullable=false,columnDefinition="TEXT") private String body;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.DRAFT;
  @Column(name="published_at") private LocalDateTime publishedAt;
  @Column(name="deleted_at") private LocalDateTime deletedAt;
  @OneToMany(mappedBy="post",cascade=CascadeType.ALL,orphanRemoval=false)
  @OrderBy("sortOrder ASC, id ASC") private List<ChoonggiMedia> media=new ArrayList<>();
  public static ChoonggiPost draft(String episode,String title,String slug,String body){var p=new ChoonggiPost();p.update(episode,title,slug,body);return p;}
  public void update(String episode,String title,String slug,String body){this.episodeCode=episode;this.title=title;this.slug=slug;this.body=body;}
  /** Null clears the video (e.g. creator removes the YouTube link on edit). */
  public void setYoutubeVideoId(String youtubeVideoId){this.youtubeVideoId=youtubeVideoId;}
  public void publish(){status=Status.PUBLISHED;if(publishedAt==null)publishedAt=LocalDateTime.now();}
  public void unpublish(){status=Status.DRAFT;publishedAt=null;}
  public void softDelete(){deletedAt=LocalDateTime.now();status=Status.DRAFT;}
}
