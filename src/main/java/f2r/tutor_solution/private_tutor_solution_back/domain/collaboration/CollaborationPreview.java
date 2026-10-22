package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static f2r.tutor_solution.private_tutor_solution_back.domain.collaboration.CollaborationService.error;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/** Disposable previews only; no original overwrite or remote resource fetch. */
@Service
public class CollaborationPreview {
  private final CollaborationFiles files;
  private final java.util.concurrent.Semaphore render = new java.util.concurrent.Semaphore(1);

  public CollaborationPreview(CollaborationFiles files) {
    this.files = files;
  }

  public String html(AuthenticatedPrincipal p, String id) {
    if (!files.metadata(p, id).get("extension").equals("html")) throw error(400, "HTML 원본이 아닙니다.");
    var doc = org.jsoup.Jsoup.parse(new String(files.bytes(p, id), StandardCharsets.UTF_8));
    doc.select(
            "script,iframe,object,embed,link,base,form,meta,svg,math,input,button,textarea,select")
        .remove();
    for (var el : doc.getAllElements())
      for (var attr : new ArrayList<>(el.attributes().asList())) {
        String key = attr.getKey().toLowerCase(Locale.ROOT), value = attr.getValue();
        if (key.startsWith("on")
            || key.equals("srcdoc")
            || key.equals("srcset")
            || key.equals("action")
            || key.equals("formaction")
            || (key.equals("href") && !value.startsWith("#"))
            || (key.equals("src")
                && !value.matches("(?is)data:image/(?:png|jpeg|gif|webp);base64,[a-z0-9+/=\\s]+")))
          el.removeAttr(attr.getKey());
      }
    return doc.outerHtml();
  }

  public Map<String, Object> pdfInfo(AuthenticatedPrincipal p, String id) {
    if (!files.metadata(p, id).get("extension").equals("pdf")) throw error(400, "PDF 원본이 아닙니다.");
    if (!render.tryAcquire()) throw error(429, "다른 미리보기 처리 중입니다.");
    try (var doc = org.apache.pdfbox.Loader.loadPDF(files.bytes(p, id))) {
      return Map.of("pages", doc.getNumberOfPages(), "originalUnchanged", true);
    } catch (IOException e) {
      throw error(422, "PDF 열람 실패");
    } finally {
      render.release();
    }
  }

  public byte[] pdf(AuthenticatedPrincipal p, String id, int page) {
    if (!files.metadata(p, id).get("extension").equals("pdf")) throw error(400, "PDF 원본이 아닙니다.");
    if (!render.tryAcquire()) throw error(429, "다른 미리보기 처리 중입니다.");
    try (var doc = org.apache.pdfbox.Loader.loadPDF(files.bytes(p, id))) {
      if (page < 1 || page > doc.getNumberOfPages()) throw error(400, "PDF 페이지 오류");
      var box = doc.getPage(page - 1).getCropBox();
      if ((double) box.getWidth() * box.getHeight() > 6000000) throw error(413, "PDF 페이지 크기 제한");
      var image = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(page - 1, 96);
      var out = new ByteArrayOutputStream();
      javax.imageio.ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw error(422, "PDF 미리보기 실패");
    } finally {
      render.release();
    }
  }
}
