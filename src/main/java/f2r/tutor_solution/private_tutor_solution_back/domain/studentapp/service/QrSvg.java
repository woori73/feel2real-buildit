package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;

/** 글자를 QR 코드 SVG로 그린다(서버 안에서만, 외부 QR 서비스에 코드를 보내지 않는다). */
public final class QrSvg {

  private QrSvg() {}

  public static String of(String text) {
    try {
      BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
          Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
      int w = m.getWidth(), h = m.getHeight();
      StringBuilder path = new StringBuilder();
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          if (m.get(x, y)) path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
        }
      }
      return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + w + " " + h
          + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"재원생 앱 접속 QR\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
          + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
    } catch (WriterException e) {
      throw new IllegalArgumentException("QR을 만들 수 없습니다.", e);
    }
  }
}
