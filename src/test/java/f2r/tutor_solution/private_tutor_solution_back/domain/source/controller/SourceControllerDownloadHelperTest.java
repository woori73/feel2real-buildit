package f2r.tutor_solution.private_tutor_solution_back.domain.source.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceController 다운로드 헬퍼 단위 테스트.
 * 파일 저장/JPEG 변환은 storage layer가 책임지므로, 컨트롤러 헬퍼는 파일명 조합과
 * Content-Disposition 헤더 직렬화만 검증한다.
 */
class SourceControllerDownloadHelperTest {

  @Nested
  @DisplayName("buildDownloadFilename")
  class BuildDownloadFilename {

    @Test
    @DisplayName("IMAGE는 원본 확장자를 떼고 저장 포맷의 확장자를 붙인다")
    void imageDropsExtension() {
      // given: PNG로 올렸지만 JPEG로 저장된 자료
      String originalName = "answer_sheet.png";

      // when
      String result = SourceController.buildDownloadFilename(originalName, "IMAGE", 1, "jpg");

      // then
      assertThat(result).isEqualTo("answer_sheet.jpg");
    }

    @Test
    @DisplayName("애니메이션 GIF로 저장된 IMAGE는 .gif로 내려간다")
    void animatedGifKeepsGifExtension() {
      // given: 애니메이션이 보존돼 GIF로 저장된 자료
      String originalName = "reaction.gif";

      // when
      String result = SourceController.buildDownloadFilename(originalName, "IMAGE", 1, "gif");

      // then: .jpg로 고정하면 OS가 JPEG로 열려다 실패한다
      assertThat(result).isEqualTo("reaction.gif");
    }

    @Test
    @DisplayName("PDF는 저장 확장자와 무관하게 페이지 suffix(_pN) + .jpg")
    void pdfAppendsPageSuffix() {
      // given
      String originalName = "midterm_2024.pdf";

      // when: PDF 페이지는 항상 JPEG로 렌더링된다
      String page1 = SourceController.buildDownloadFilename(originalName, "PDF", 1, "jpg");
      String page12 = SourceController.buildDownloadFilename(originalName, "PDF", 12, "jpg");

      // then
      assertThat(page1).isEqualTo("midterm_2024_p1.jpg");
      assertThat(page12).isEqualTo("midterm_2024_p12.jpg");
    }

    @Test
    @DisplayName("한글 파일명도 그대로 보존된다 (확장자만 교체)")
    void koreanNameIsPreserved() {
      // given
      String originalName = "2024_1학기_중간고사.pdf";

      // when
      String result = SourceController.buildDownloadFilename(originalName, "PDF", 3, "jpg");

      // then
      assertThat(result).isEqualTo("2024_1학기_중간고사_p3.jpg");
    }

    @Test
    @DisplayName("originalName이 null/빈문자열이면 'download'를 base로 사용한다")
    void blankNameFallsBackToDownload() {
      // given
      String empty = "";

      // when
      String fromNull = SourceController.buildDownloadFilename(null, "IMAGE", 1, "jpg");
      String fromEmpty = SourceController.buildDownloadFilename(empty, "PDF", 2, "jpg");

      // then
      assertThat(fromNull).isEqualTo("download.jpg");
      assertThat(fromEmpty).isEqualTo("download_p2.jpg");
    }

    @Test
    @DisplayName("확장자가 여러 개여도 마지막 점 이후만 잘라낸다")
    void onlyLastExtensionStripped() {
      // given
      String originalName = "answer.v2.final.png";

      // when
      String result = SourceController.buildDownloadFilename(originalName, "IMAGE", 1, "jpg");

      // then
      assertThat(result).isEqualTo("answer.v2.final.jpg");
    }
  }

  @Nested
  @DisplayName("contentDispositionAttachment")
  class ContentDispositionAttachment {

    @Test
    @DisplayName("ASCII 파일명은 filename과 filename* 양쪽 다 동일하게 들어간다")
    void asciiName() {
      // given
      String filename = "answer.jpg";

      // when
      String header = SourceController.contentDispositionAttachment(filename);

      // then
      assertThat(header)
          .startsWith("attachment;")
          .contains("filename=\"answer.jpg\"")
          .contains("filename*=UTF-8''answer.jpg");
    }

    @Test
    @DisplayName("한글 파일명은 ASCII filename은 폴백(_)로, filename*은 UTF-8 percent-encoded로 직렬화")
    void koreanNameUsesRfc5987() {
      // given — 한글 + 공백 포함
      String filename = "중간고사 답안.jpg";

      // when
      String header = SourceController.contentDispositionAttachment(filename);

      // then
      // ASCII 폴백: 비-ASCII는 _, 공백은 보존
      assertThat(header).contains("filename=\"____ __.jpg\"");
      // RFC 5987: UTF-8 percent encoded (공백은 %20으로, +가 아님)
      assertThat(header).contains("filename*=UTF-8''%EC%A4%91%EA%B0%84%EA%B3%A0%EC%82%AC%20%EB%8B%B5%EC%95%88.jpg");
    }

    @Test
    @DisplayName("쌍따옴표는 작은따옴표로 치환해 헤더 파싱이 깨지지 않게 한다")
    void doubleQuoteIsReplaced() {
      // given
      String filename = "weird\"name.jpg";

      // when
      String header = SourceController.contentDispositionAttachment(filename);

      // then
      assertThat(header).contains("filename=\"weird'name.jpg\"");
      // filename*에는 percent-encoding으로 들어감 (%22)
      assertThat(header).contains("filename*=UTF-8''weird%22name.jpg");
    }
  }
}
