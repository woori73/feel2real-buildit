package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsArticle;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsArticleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「바뀌는 입시」 해설 글 — 공개 읽기 + 관리자 추가·수정·숨김. 삭제는 없다(숨김으로 내린다).
 *
 * <p>상담 AI 근거(공식자료·지식 항목·검색 인덱스)와 연결하지 않는다. 이 서비스는 다른 입시 서비스에서 참조하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdmissionsArticleService {

    /**
     * 허용 태그: p, h2, h3, ul, ol, li, table, thead, tbody, tr, th, td, strong, em, a(href: http·https),
     * figure, img(src·alt), figcaption. img의 src는 {@link #IMAGE_SRC}(사이트 안 /images/admissions/ 아래 그림)만 남긴다.
     */
    static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "h2", "h3", "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td", "strong", "em", "a",
                    "figure", "img", "figcaption")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https")
            .addAttributes("img", "src", "alt");

    /** 본문 그림 주소: 사이트 안 /images/admissions/ 아래 그림 파일만. 외부 주소·//·data:·javascript:·..은 막는다. */
    static final Pattern IMAGE_SRC = Pattern.compile("^/images/admissions/(?!.*\\.\\.)[A-Za-z0-9_\\-./]+\\.(?:png|jpe?g|webp|gif)$");

    private final AdmissionsArticleRepository repository;

    public record Summary(Long id, String series, String episode, String title, String summary, LocalDate publishedOn) {}

    public record Detail(Long id, String series, String episode, String title, String summary, LocalDate publishedOn,
            String bodyHtml) {}

    public record AdminView(Long id, String series, String episode, String title, String summary, LocalDate publishedOn,
            boolean visible, int sortOrder, String bodyHtml) {}

    public record Form(String series, String episode, String title, String summary, String bodyHtml,
            String publishedOn, Boolean visible, Integer sortOrder) {}

    // ------------------------------------------------------------ 공개

    @Transactional(readOnly = true)
    public List<Summary> listPublic() {
        return repository.findAllByVisibleTrueOrderByPublishedOnDescSortOrderAscArticleIdAsc().stream()
                .map(a -> new Summary(a.getArticleId(), a.getSeries(), a.getEpisode(), a.getTitle(), a.getSummary(), a.getPublishedOn()))
                .toList();
    }

    /** 공개 글 하나. 숨김·없음은 IllegalArgumentException(화면에서는 404). 출력할 때도 한 번 더 정리한다. */
    @Transactional(readOnly = true)
    public Detail getPublic(Long id) {
        AdmissionsArticle a = repository.findByArticleIdAndVisibleTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("글을 찾을 수 없습니다."));
        return new Detail(a.getArticleId(), a.getSeries(), a.getEpisode(), a.getTitle(), a.getSummary(), a.getPublishedOn(),
                forDisplay(a.getBodyHtml()));
    }

    // ------------------------------------------------------------ 관리자

    @Transactional(readOnly = true)
    public List<AdminView> listAll() {
        return repository.findAllByOrderByPublishedOnDescSortOrderAscArticleIdAsc().stream().map(AdmissionsArticleService::adminView).toList();
    }

    public AdminView create(Form form) {
        Normalized n = normalize(form);
        if (repository.existsBySeriesAndEpisode(n.series, n.episode)) {
            throw new IllegalArgumentException("같은 시리즈에 같은 회차가 이미 있습니다.");
        }
        AdmissionsArticle saved = repository.save(AdmissionsArticle.builder().series(n.series).episode(n.episode).title(n.title)
                .summary(n.summary).bodyHtml(n.body).publishedOn(n.publishedOn).visible(n.visible).sortOrder(n.sortOrder).build());
        return adminView(saved);
    }

    public AdminView update(Long id, Form form) {
        AdmissionsArticle a = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("글을 찾을 수 없습니다."));
        Normalized n = normalize(form);
        if (repository.existsBySeriesAndEpisodeAndArticleIdNot(n.series, n.episode, id)) {
            throw new IllegalArgumentException("같은 시리즈에 같은 회차가 이미 있습니다.");
        }
        a.setSeries(n.series);
        a.setEpisode(n.episode);
        a.setTitle(n.title);
        a.setSummary(n.summary);
        a.setBodyHtml(n.body);
        a.setPublishedOn(n.publishedOn);
        a.setVisible(n.visible);
        a.setSortOrder(n.sortOrder);
        return adminView(a);
    }

    /** 숨김/다시 공개. */
    public AdminView setVisible(Long id, boolean visible) {
        AdmissionsArticle a = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("글을 찾을 수 없습니다."));
        a.setVisible(visible);
        return adminView(a);
    }

    // ------------------------------------------------------------ 규칙

    /**
     * 저장용: 허용 태그·a[href](http·https)·img[src·alt]만 남긴다. 스크립트·이벤트 속성·style·class는 모두 사라진다.
     * src가 {@link #IMAGE_SRC}에 맞지 않는 img는 통째로 지운다. 그림이 없는 본문은 전과 같은 결과다.
     */
    static String sanitize(String html) {
        String cleaned = Jsoup.clean(html == null ? "" : html, SAFELIST).trim();
        if (!cleaned.contains("<img")) {
            return cleaned;
        }
        Document doc = Jsoup.parseBodyFragment(cleaned);
        for (Element img : doc.select("img")) {
            if (!IMAGE_SRC.matcher(img.attr("src")).matches()) {
                img.remove();
            }
        }
        doc.outputSettings().prettyPrint(false);
        return doc.body().html().trim();
    }

    /** 출력용: 한 번 더 정리하고 외부 링크는 새 창(rel=noopener noreferrer)으로 연다. */
    static String forDisplay(String html) {
        Document doc = Jsoup.parseBodyFragment(sanitize(html));
        for (Element a : doc.select("a[href]")) {
            a.attr("target", "_blank");
            a.attr("rel", "noopener noreferrer");
        }
        doc.outputSettings().prettyPrint(false);
        return doc.body().html();
    }

    private record Normalized(String series, String episode, String title, String summary, String body,
            LocalDate publishedOn, boolean visible, int sortOrder) {}

    static Normalized normalize(Form f) {
        if (f == null) throw new IllegalArgumentException("글 내용을 보내 주세요.");
        String series = required(f.series(), "시리즈명", 60);
        String episode = required(f.episode(), "회차", 20);
        String title = required(f.title(), "제목", 200);
        String summary = required(f.summary(), "한 줄 요약", 500);
        String body = sanitize(f.bodyHtml());
        if (body.isEmpty()) throw new IllegalArgumentException("본문을 입력해 주세요.");
        LocalDate date;
        try {
            date = LocalDate.parse(required(f.publishedOn(), "게시일", 10));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("게시일은 YYYY-MM-DD 형식으로 입력해 주세요.");
        }
        int order = f.sortOrder() == null ? 0 : f.sortOrder();
        if (order < 0 || order > 9999) throw new IllegalArgumentException("정렬 순서는 0~9999입니다.");
        return new Normalized(series, episode, title, summary, body, date, f.visible() == null || f.visible(), order);
    }

    private static String required(String v, String label, int max) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        if (t.length() > max) throw new IllegalArgumentException(label + "은(는) " + max + "자 이내로 입력해 주세요.");
        return t;
    }

    private static AdminView adminView(AdmissionsArticle a) {
        return new AdminView(a.getArticleId(), a.getSeries(), a.getEpisode(), a.getTitle(), a.getSummary(), a.getPublishedOn(),
                a.isVisible(), a.getSortOrder(), a.getBodyHtml());
    }
}
