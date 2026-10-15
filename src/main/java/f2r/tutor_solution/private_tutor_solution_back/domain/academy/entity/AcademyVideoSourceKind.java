package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * 한 {@code academy_videos} 행이 어떤 종류의 영상 자산인지.
 *
 * <p>{@link #DIRECT_UPLOAD} 는 서버가 받아 MP4 정규화·썸네일·private storage 저장까지 마친
 * 기존 영상이다({@code raw/normalized} storage key + optimization/cleanup 파이프라인). {@link
 * #EXTERNAL_LINK} 는 네이버·YouTube 등 외부에 이미 올라간 영상을 <b>URL 자체만</b> 자산으로
 * 저장한 것이다 — BuildIt 서버로 다운로드/복제하지 않고, storage key·트랜스코딩·썸네일이 없다.
 *
 * <p>두 종류 모두 같은 학원 영상 풀(최대 개수 합산)에 들어가고, 용도({@link AcademyVideoPurpose})
 * 지정과 내신 LIVE 연결({@code midterm_live_video_links})을 똑같이 받는다.
 */
public enum AcademyVideoSourceKind {
  DIRECT_UPLOAD,
  EXTERNAL_LINK
}
