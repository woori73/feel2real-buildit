package f2r.tutor_solution.private_tutor_solution_back.infrastructure.image;

import com.anthropic.models.messages.Base64ImageSource;

/**
 * 저장된 이미지를 Claude Vision에 보낼 때 붙일 media type.
 *
 * <p><b>원본 파일명이 아니라 저장 키로 판정한다.</b> 업로드는 저장 시점에 재인코딩되므로
 * {@code cat.png}를 올려도 실제 저장물은 JPEG다. 원본 이름을 믿으면 JPEG 바이트를
 * {@code image/png}라고 알리게 되고, Anthropic API가 media type 불일치로 요청을 거부한다.
 *
 * <p>PDF의 저장 키는 확장자가 없는 페이지 묶음 prefix고 페이지는 항상 {@code page_NN.jpg}이므로,
 * 기본값 JPEG가 그대로 맞는다.
 */
public final class VisionMediaType {

  private VisionMediaType() {
  }

  public static Base64ImageSource.MediaType forStorageKey(String storageKey) {
    String key = storageKey != null ? storageKey.toLowerCase() : "";
    if (key.endsWith(".png")) return Base64ImageSource.MediaType.IMAGE_PNG;
    if (key.endsWith(".gif")) return Base64ImageSource.MediaType.IMAGE_GIF;
    if (key.endsWith(".webp")) return Base64ImageSource.MediaType.IMAGE_WEBP;
    return Base64ImageSource.MediaType.IMAGE_JPEG;
  }
}
