package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 학생 업로드 파일의 영속화/조회 추상화.
 *
 * <p>구현체는 두 가지:
 * <ul>
 *   <li>{@link LocalFileStorage} — 로컬 디스크 (개발/로컬 profile)</li>
 *   <li>{@link S3FileStorage} — AWS S3 (운영 profile)</li>
 * </ul>
 *
 * <p>storageKey 의미는 구현체마다 다르다:
 * <ul>
 *   <li>Local: 절대 경로 (단일 파일이면 파일 경로, PDF면 페이지 디렉터리 경로)</li>
 *   <li>S3: 객체 키 prefix (예: "prd/users/12/abc-def" — 그 아래에 page_01.jpg 등이 위치)</li>
 * </ul>
 * 호출부는 storageKey를 불투명 식별자로 취급한다.
 */
public interface FileStorage {

  /**
   * 이미지 1장(JPEG bytes) 저장. 반환값은 source.storage_key에 저장된다.
   *
   * <p>내부에서 만든 JPEG(영상 썸네일, 기본 로고, PDF 페이지)를 저장하는 경로용이다. 사용자
   * 업로드는 포맷이 JPEG가 아닐 수 있으므로 {@link #saveImage(Long, String, byte[], String)}을 쓴다.
   */
  default String saveImage(Long userId, String filenameHint, byte[] jpegBytes) throws IOException {
    return saveImage(userId, filenameHint, jpegBytes, "jpg");
  }

  /**
   * 이미지 1장을 확장자를 지정해 저장한다.
   *
   * <p>저장 키의 확장자가 실제 포맷을 따라가야 서빙 쪽이 Content-Type을 되찾을 수 있다
   * (Content-Type 컬럼이 없는 Source/Academy 로고·커버가 그렇다).
   *
   * @param extension 점 없는 확장자 (예: {@code "jpg"}, {@code "gif"})
   */
  String saveImage(Long userId, String filenameHint, byte[] bytes, String extension) throws IOException;

  /** PDF 페이지들(page_01부터 순서대로) 저장. 반환값은 페이지 묶음의 식별자(local: 디렉터리 경로, s3: 객체 prefix). */
  String savePdfPages(Long userId, List<byte[]> pageJpegBytes) throws IOException;

  /** UNKNOWN 타입 원본 그대로 저장 (재인코딩 안 함). */
  String saveRaw(Long userId, String filenameHint, InputStream in, long size) throws IOException;

  /**
   * 한 source의 모든 페이지를 순서대로 읽어 List&lt;byte[]&gt;로 반환.
   * IMAGE/UNKNOWN: 길이 1. PDF: 페이지 수만큼.
   * LLM(Claude Vision)에 전체 페이지를 한 번에 보낼 때 사용.
   */
  List<byte[]> readAllPages(String storageKey, String fileType) throws IOException;

  /**
   * 특정 페이지 bytes를 읽는다 (라이트박스 미리보기용).
   * IMAGE/UNKNOWN: page 인자 무시하고 단일 파일 반환.
   * PDF: 1-based page 번호 (1이면 page_01.jpg).
   */
  byte[] readPage(String storageKey, String fileType, int page) throws IOException;

  /**
   * 단일 객체를 최대 {@code maxBytes}까지만 읽는다.
   *
   * <p>구현체는 metadata 크기뿐 아니라 실제 stream도 최대 {@code maxBytes + 1} bytes만 읽어야 한다.
   * 상한을 넘으면 storage key를 노출하지 않는 {@link StorageReadLimitExceededException}을 던진다.
   * 원격 저장소의 운영상 조회 실패도 key/path/cause를 노출하지 않는 checked {@link
   * StorageReadException}으로 정규화한다.
   */
  byte[] readSingleLimited(String storageKey, String fileType, long maxBytes) throws IOException;

  /** PDF 페이지 수. IMAGE/UNKNOWN은 항상 1. 조회 실패 시 1로 폴백(라이트박스가 최소 한 장은 시도하도록). */
  int pageCount(String storageKey, String fileType);

  /**
   * 한 source의 파일을 영구 삭제한다 (자료 삭제 기능용).
   * IMAGE/UNKNOWN: 단일 파일 제거. PDF: 페이지 디렉터리/prefix 통째로 제거.
   * 이미 사라진 키여도 예외를 던지지 않는다 (best-effort).
   */
  void delete(String storageKey, String fileType);

  /**
   * 삭제 결과를 호출자에게 보고한다.
   *
   * @return null/blank key, 이미 없는 객체를 포함해 원하는 부재 상태를 달성했으면 {@code true};
   *     예외 또는 부분 재귀 삭제면 {@code false}
   */
  boolean deleteReported(String storageKey, String fileType);

  /**
   * 단일 객체의 [start, end] 바이트 구간(양끝 포함)을 스트림으로 연다. 영상·음악 재생의 Range 요청용이며 전체를 메모리에 올리지
   * 않는다. 호출자가 스트림을 닫는다.
   */
  default InputStream openRange(String storageKey, long start, long end) throws IOException {
    throw new IOException("ranged read not supported");
  }
}
