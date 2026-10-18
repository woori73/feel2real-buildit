package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile.GrammarDifficulty;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrammarBankService {

  private static final BigDecimal REVIEW_CONFIDENCE_THRESHOLD = new BigDecimal("0.60");

  private final GrammarQuestionProfileRepository profileRepository;
  private final GrammarTagRepository tagRepository;
  private final QuestionGrammarTagRepository questionTagRepository;
  private final GrammarQuestionVariantRepository variantRepository;
  private final QuestionRepository questionRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public record SearchFilters(
      SchoolLevel schoolLevel,
      GrammarQuestionFormat format,
      GrammarDifficulty difficulty,
      GrammarReviewStatus reviewStatus,
      GrammarRightsStatus rightsStatus,
      BigDecimal minConfidence,
      BigDecimal maxConfidence,
      String search,
      List<Long> tagIds
  ) {
  }

  public Page<GrammarQuestionItemResponse> search(SearchFilters filters, Pageable pageable) {
    Specification<GrammarQuestionProfile> spec = buildSpec(filters);
    Page<GrammarQuestionProfile> page = profileRepository.findAll(spec, pageable);
    Map<Long, List<GrammarTagRef>> tagsByQuestion = loadTags(
        page.getContent().stream().map(p -> p.getQuestion().getQuestionId()).toList());
    return page.map(p -> toItem(p, tagsByQuestion.getOrDefault(
        p.getQuestion().getQuestionId(), List.of())));
  }

  public GrammarQuestionDetailResponse detail(Long questionId) {
    GrammarQuestionProfile profile = profileRepository.findByQuestion_QuestionId(questionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문법 문제 프로필이 없습니다."));
    Question q = profile.getQuestion();
    Passage passage = q.getPassage();
    return new GrammarQuestionDetailResponse(
        q.getQuestionId(),
        q.getQuestionType().name(),
        profile.getSchoolLevel(),
        profile.getQuestionFormat(),
        profile.getDifficulty(),
        profile.getReviewStatus(),
        profile.getRightsStatus(),
        profile.getClassificationConfidence(),
        profile.getAutoClassification(),
        profile.getKeywords(),
        loadTags(List.of(questionId)).getOrDefault(questionId, List.of()),
        q.getQuestionText(),
        parseChoices(q.getChoices()),
        q.getCorrectAnswer(),
        q.getExplanation(),
        q.getQuestionLabel(),
        q.getPoint(),
        passage != null ? passage.getPassageId() : null,
        passage != null ? passage.getTitle() : null,
        passage != null ? passage.getContent() : null,
        profile.getMaterial() != null ? profile.getMaterial().getMaterialId() : null,
        profile.getMaterial() != null ? profile.getMaterial().getOriginalFileName() : null,
        profile.getSourcePosition(),
        profile.getExternalRef(),
        variantRepository.findByBaseQuestion_QuestionId(questionId).stream()
            .map(GrammarBankService::toVariantResponse).toList());
  }

  public List<GrammarTag> tags() {
    return tagRepository.findAllByOrderBySortOrderAsc();
  }

  public GrammarStatsResponse stats() {
    long total = profileRepository.count();
    List<GrammarStatsResponse.LevelCount> levels = profileRepository.countGroupBySchoolLevel()
        .stream().map(row -> new GrammarStatsResponse.LevelCount(
            row[0].toString(), ((SchoolLevel) row[0]).labelKo(), ((Number) row[1]).longValue()))
        .toList();
    List<GrammarStatsResponse.TagCount> tagCounts = profileRepository.countGroupByTag()
        .stream().map(row -> new GrammarStatsResponse.TagCount(
            ((Number) row[0]).longValue(), (String) row[1], ((Number) row[2]).longValue()))
        .toList();
    return new GrammarStatsResponse(
        total,
        profileRepository.countByReviewStatus(GrammarReviewStatus.NEEDS_REVIEW),
        profileRepository.countByReviewStatus(GrammarReviewStatus.APPROVED),
        profileRepository.countByReviewStatus(GrammarReviewStatus.REJECTED),
        levels, tagCounts);
  }

  @Transactional
  public GrammarQuestionDetailResponse updateQuestion(AuthenticatedPrincipal principal,
      Long questionId, GrammarQuestionUpdateRequest request) {
    Question q = requireEditableQuestion(principal, questionId);
    String questionText = request.questionText() != null ? request.questionText() : q.getQuestionText();
    String choicesJson = request.choices() != null ? writeJson(request.choices()) : q.getChoices();
    String correctAnswer = request.correctAnswer() != null ? request.correctAnswer() : q.getCorrectAnswer();
    String explanation = request.explanation() != null ? request.explanation() : q.getExplanation();
    Integer point = request.point() != null ? request.point() : q.getPoint();
    q.updateProblemGeneratorFields(q.getQuestionType(), questionText, choicesJson,
        correctAnswer, explanation, q.getQuestionLabel(), point);
    return detail(questionId);
  }

  @Transactional
  public GrammarQuestionDetailResponse updateProfile(AuthenticatedPrincipal principal,
      Long questionId, GrammarProfileUpdateRequest request) {
    Question q = requireEditableQuestion(principal, questionId);
    GrammarQuestionProfile profile = profileRepository.findByQuestion_QuestionId(questionId).orElseThrow();
    profile.updateClassification(request.schoolLevel(), request.questionFormat(),
        request.difficulty(), request.keywords());
    if (request.rightsStatus() != null) {
      profile.updateRightsStatus(request.rightsStatus());
    }
    if (request.tagIds() != null && !request.tagIds().isEmpty()) {
      replaceTags(q, request.tagIds(), request.primaryTagId(), false);
      profile.updateReviewStatus(GrammarReviewStatus.APPROVED);
    }
    profileRepository.save(profile);
    return detail(questionId);
  }

  @Transactional
  public GrammarQuestionDetailResponse review(AuthenticatedPrincipal principal,
      Long questionId, GrammarReviewRequest request) {
    requireReviewer(principal);
    if (request.status() != GrammarReviewStatus.APPROVED && request.status() != GrammarReviewStatus.REJECTED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검수 판정은 APPROVED/REJECTED만 가능합니다.");
    }
    Question q = requireProfile(questionId).getQuestion();
    if (request.tagIds() != null && !request.tagIds().isEmpty()) {
      replaceTags(q, request.tagIds(), request.primaryTagId(), false);
    }
    GrammarQuestionProfile profile = requireProfile(questionId);
    profile.updateReviewStatus(request.status());
    profileRepository.save(profile);
    return detail(questionId);
  }

  @Transactional
  public GrammarVariantResponse createVariant(AuthenticatedPrincipal principal, Long baseQuestionId,
      GrammarVariantCreateRequest request) {
    requireReviewer(principal);
    GrammarQuestionProfile profile = requireProfile(baseQuestionId);
    Question base = profile.getQuestion();
    if (request.questionText() == null || request.questionText().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정된 questionText가 필요합니다.");
    }
    Question variant = Question.builder()
        .subject(base.getSubject())
        .category(base.getCategory())
        .passage(base.getPassage())
        .questionType(base.getQuestionType())
        .questionText(request.questionText())
        .choices(request.choices() != null ? writeJson(request.choices()) : base.getChoices())
        .correctAnswer(request.correctAnswer() != null ? request.correctAnswer() : base.getCorrectAnswer())
        .explanation(request.explanation() != null ? request.explanation() : base.getExplanation())
        .source(f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionSource.GENERATED)
        .orderNum(base.getOrderNum())
        .point(base.getPoint())
        .build();
    questionRepository.save(variant);
    GrammarQuestionVariant row = GrammarQuestionVariant.builder()
        .baseQuestion(base)
        .variantQuestion(variant)
        .changeReason(request.changeReason())
        .build();
    variantRepository.save(row);
    return toVariantResponse(row);
  }

  @Transactional
  public GrammarVariantResponse reviewVariant(AuthenticatedPrincipal principal, Long variantId,
      String status, String reviewNote) {
    requireReviewer(principal);
    GrammarQuestionVariant variant = variantRepository.findById(variantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "변형본이 없습니다."));
    GrammarQuestionVariant.VariantStatus decision;
    try {
      decision = GrammarQuestionVariant.VariantStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PENDING/APPROVED/REJECTED 중 하나여야 합니다.");
    }
    variant.review(decision, reviewNote);
    return toVariantResponse(variantRepository.save(variant));
  }

  // ---------- 내부 ----------

  public GrammarQuestionProfile requireProfile(Long questionId) {
    return profileRepository.findByQuestion_QuestionId(questionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문법 문제 프로필이 없습니다."));
  }

  private Question requireEditableQuestion(AuthenticatedPrincipal principal, Long questionId) {
    GrammarQuestionProfile profile = requireProfile(questionId);
    boolean privileged = Role.ADMIN.name().equals(principal.role()) || Role.TUTOR.name().equals(principal.role());
    if (!privileged && !principal.userId().equals(profile.getOwnerUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 문제를 편집할 권한이 없습니다.");
    }
    return profile.getQuestion();
  }

  private void requireReviewer(AuthenticatedPrincipal principal) {
    if (!Role.ADMIN.name().equals(principal.role()) && !Role.TUTOR.name().equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "검수 권한이 없습니다.");
    }
  }

  void replaceTags(Question question, List<Long> tagIds, Long primaryTagId, boolean auto) {
    List<GrammarTag> tags = tagRepository.findAllById(tagIds);
    if (tags.size() != tagIds.stream().distinct().count()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 문법 태그가 있습니다.");
    }
    questionTagRepository.deleteByQuestion_QuestionId(question.getQuestionId());
    questionTagRepository.flush();
    for (GrammarTag tag : tags) {
      questionTagRepository.save(QuestionGrammarTag.builder()
          .question(question)
          .tag(tag)
          .auto(auto)
          .primary(tag.getTagId().equals(primaryTagId))
          .build());
    }
  }

  Map<Long, List<GrammarTagRef>> loadTags(Collection<Long> questionIds) {
    if (questionIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<GrammarTagRef>> result = new HashMap<>();
    for (QuestionGrammarTag gt : questionTagRepository.findByQuestion_QuestionIdIn(questionIds)) {
      result.computeIfAbsent(gt.getQuestion().getQuestionId(), k -> new ArrayList<>())
          .add(new GrammarTagRef(gt.getTag().getTagId(), gt.getTag().getNameKo(),
              gt.isPrimary(), gt.isAuto(), gt.getConfidence()));
    }
    return result;
  }

  GrammarQuestionItemResponse toItem(GrammarQuestionProfile p, List<GrammarTagRef> tags) {
    Question q = p.getQuestion();
    return new GrammarQuestionItemResponse(
        q.getQuestionId(), p.getSchoolLevel(), p.getQuestionFormat(), p.getDifficulty(),
        p.getReviewStatus(), p.getRightsStatus(), p.getClassificationConfidence(), tags,
        excerpt(q.getQuestionText()), q.getCorrectAnswer(),
        p.getMaterial() != null ? p.getMaterial().getMaterialId() : null,
        p.getSourcePosition(), p.getExternalRef());
  }

  static String excerpt(String text) {
    if (text == null) {
      return "";
    }
    String stripped = text.strip();
    return stripped.length() <= 120 ? stripped : stripped.substring(0, 120) + "…";
  }

  List<String> parseChoices(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {
      });
    } catch (Exception e) {
      return List.of();
    }
  }

  String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "choices 형식이 올바르지 않습니다.");
    }
  }

  static GrammarVariantResponse toVariantResponse(GrammarQuestionVariant v) {
    return new GrammarVariantResponse(v.getVariantId(), v.getBaseQuestion().getQuestionId(),
        v.getVariantQuestion().getQuestionId(), v.getStatus().name(), v.getChangeReason(),
        v.getReviewNote());
  }

  Specification<GrammarQuestionProfile> buildSpec(SearchFilters f) {
    return (root, cq, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      var questionJoin = root.join("question", JoinType.INNER);
      if (f.schoolLevel() != null) {
        ps.add(cb.equal(root.get("schoolLevel"), f.schoolLevel()));
      }
      if (f.format() != null) {
        ps.add(cb.equal(root.get("questionFormat"), f.format()));
      }
      if (f.difficulty() != null) {
        ps.add(cb.equal(root.get("difficulty"), f.difficulty()));
      }
      if (f.reviewStatus() != null) {
        ps.add(cb.equal(root.get("reviewStatus"), f.reviewStatus()));
      } else {
        // 반려(REJECTED) 문항은 기본 검색에서 제외(검수 화면은 reviewStatus=REJECTED로 조회)
        ps.add(cb.notEqual(root.get("reviewStatus"), GrammarReviewStatus.REJECTED));
      }
      if (f.rightsStatus() != null) {
        ps.add(cb.equal(root.get("rightsStatus"), f.rightsStatus()));
      }
      if (f.minConfidence() != null) {
        ps.add(cb.or(cb.isNull(root.get("classificationConfidence")),
            cb.ge(root.get("classificationConfidence"), f.minConfidence())));
      }
      if (f.maxConfidence() != null) {
        ps.add(cb.or(cb.isNull(root.get("classificationConfidence")),
            cb.le(root.get("classificationConfidence"), f.maxConfidence())));
      }
      if (f.search() != null && !f.search().isBlank()) {
        String like = "%" + f.search().strip().toLowerCase() + "%";
        ps.add(cb.or(
            cb.like(cb.lower(questionJoin.get("questionText")), like),
            cb.like(cb.lower(root.get("keywords")), like)));
      }
      if (f.tagIds() != null && !f.tagIds().isEmpty()) {
        Subquery<Long> sub = cq.subquery(Long.class);
        var gt = sub.from(QuestionGrammarTag.class);
        sub.select(gt.get("id"));
        sub.where(cb.equal(gt.get("question"), questionJoin),
            gt.get("tag").get("tagId").in(f.tagIds()));
        ps.add(cb.exists(sub));
      }
      return cb.and(ps.toArray(new Predicate[0]));
    };
  }

  public static BigDecimal reviewThreshold() {
    return REVIEW_CONFIDENCE_THRESHOLD;
  }
}
