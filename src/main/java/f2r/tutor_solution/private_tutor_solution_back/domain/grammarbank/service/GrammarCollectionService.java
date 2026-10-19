package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollection;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollection.CollectionKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarCollectionItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarTag;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.QuestionGrammarTag;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.GrammarCollectionItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.GrammarCollectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.GrammarTagRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.QuestionGrammarTagRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 문제 선택함(BASKET)과 문제집 제작 목록(BOOK). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrammarCollectionService {

  private final GrammarCollectionRepository collectionRepository;
  private final GrammarCollectionItemRepository itemRepository;
  private final GrammarTagRepository tagRepository;
  private final QuestionGrammarTagRepository questionTagRepository;
  private final GrammarBankService bankService;

  public List<GrammarCollectionResponse> list(AuthenticatedPrincipal principal) {
    return collectionRepository.findByOwnerUserIdOrderByCreatedAtDesc(principal.userId()).stream()
        .map(c -> toResponse(c, false)).toList();
  }

  public GrammarCollectionResponse detail(AuthenticatedPrincipal principal, Long collectionId) {
    return toResponse(loadOwned(principal, collectionId), true);
  }

  @Transactional
  public GrammarCollectionResponse create(AuthenticatedPrincipal principal,
      GrammarCollectionRequest request) {
    if (request.title() == null || request.title().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title이 필요합니다.");
    }
    GrammarCollection collection = collectionRepository.save(GrammarCollection.builder()
        .ownerUserId(principal.userId())
        .kind(request.kind() != null ? request.kind() : CollectionKind.BASKET)
        .title(request.title().strip())
        .description(request.description())
        .build());
    addQuestions(collection, request.questionIds());
    return toResponse(collection, true);
  }

  @Transactional
  public GrammarCollectionResponse update(AuthenticatedPrincipal principal, Long collectionId,
      GrammarCollectionRequest request) {
    GrammarCollection collection = loadOwned(principal, collectionId);
    collection.updateMeta(request.title(), request.description());
    return toResponse(collectionRepository.save(collection), true);
  }

  @Transactional
  public void delete(AuthenticatedPrincipal principal, Long collectionId) {
    collectionRepository.delete(loadOwned(principal, collectionId));
  }

  @Transactional
  public GrammarCollectionResponse addItems(AuthenticatedPrincipal principal, Long collectionId,
      List<Long> questionIds) {
    GrammarCollection collection = loadOwned(principal, collectionId);
    addQuestions(collection, questionIds);
    return toResponse(collection, true);
  }

  @Transactional
  public GrammarCollectionResponse removeItems(AuthenticatedPrincipal principal, Long collectionId,
      List<Long> questionIds) {
    GrammarCollection collection = loadOwned(principal, collectionId);
    itemRepository.deleteByCollection_CollectionIdAndQuestion_QuestionIdIn(collectionId, questionIds);
    return toResponse(collection, true);
  }

  /** questionIds 순서대로 order_num을 재부여한다. 목록에 없는 항목은 그대로 뒤로 밀린다. */
  @Transactional
  public GrammarCollectionResponse reorder(AuthenticatedPrincipal principal, Long collectionId,
      List<Long> questionIds) {
    GrammarCollection collection = loadOwned(principal, collectionId);
    List<GrammarCollectionItem> items =
        itemRepository.findByCollection_CollectionIdOrderByOrderNumAsc(collectionId);
    Map<Long, GrammarCollectionItem> byQuestion = new HashMap<>();
    items.forEach(item -> byQuestion.put(item.getQuestion().getQuestionId(), item));
    int order = 1;
    for (Long questionId : questionIds) {
      GrammarCollectionItem item = byQuestion.remove(questionId);
      if (item != null) {
        item.updateOrderNum(order++);
        itemRepository.save(item);
      }
    }
    for (GrammarCollectionItem item : byQuestion.values()) {
      item.updateOrderNum(order++);
      itemRepository.save(item);
    }
    return toResponse(collection, true);
  }

  /** 선택함 → 문제집(BOOK) 복제. */
  @Transactional
  public GrammarCollectionResponse promoteToBook(AuthenticatedPrincipal principal, Long basketId,
      String title) {
    GrammarCollection basket = loadOwned(principal, basketId);
    List<GrammarCollectionItem> items =
        itemRepository.findByCollection_CollectionIdOrderByOrderNumAsc(basketId);
    if (items.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택함이 비어 있습니다.");
    }
    GrammarCollection book = collectionRepository.save(GrammarCollection.builder()
        .ownerUserId(principal.userId())
        .kind(CollectionKind.BOOK)
        .title(title != null && !title.isBlank() ? title.strip() : basket.getTitle() + " 문제집")
        .description(basket.getDescription())
        .build());
    for (GrammarCollectionItem item : items) {
      itemRepository.save(GrammarCollectionItem.builder()
          .collection(book)
          .question(item.getQuestion())
          .orderNum(item.getOrderNum())
          .build());
    }
    return toResponse(book, true);
  }

  GrammarCollection loadOwned(AuthenticatedPrincipal principal, Long collectionId) {
    GrammarCollection collection = collectionRepository.findById(collectionId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선택함/문제집이 없습니다."));
    if (!collection.getOwnerUserId().equals(principal.userId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "소유자만 접근할 수 있습니다.");
    }
    return collection;
  }

  private void addQuestions(GrammarCollection collection, List<Long> questionIds) {
    if (questionIds == null) {
      return;
    }
    int nextOrder = (int) itemRepository.countByCollection_CollectionId(collection.getCollectionId());
    for (Long questionId : questionIds) {
      if (itemRepository.findByCollection_CollectionIdAndQuestion_QuestionId(
          collection.getCollectionId(), questionId).isPresent()) {
        continue;
      }
      GrammarQuestionProfile profile = bankService.requireProfile(questionId);
      itemRepository.save(GrammarCollectionItem.builder()
          .collection(collection)
          .question(profile.getQuestion())
          .orderNum(++nextOrder)
          .build());
    }
  }

  private GrammarCollectionResponse toResponse(GrammarCollection collection, boolean withItems) {
    long itemCount = itemRepository.countByCollection_CollectionId(collection.getCollectionId());
    List<GrammarCollectionItemResponse> items = List.of();
    if (withItems) {
      List<GrammarCollectionItem> rows =
          itemRepository.findByCollection_CollectionIdOrderByOrderNumAsc(collection.getCollectionId());
      Map<Long, List<String>> tagNames = loadTagNames(rows.stream()
          .map(i -> i.getQuestion().getQuestionId()).toList());
      items = rows.stream().map(i -> new GrammarCollectionItemResponse(
          i.getQuestion().getQuestionId(), i.getOrderNum(),
          GrammarBankService.excerpt(i.getQuestion().getQuestionText()),
          tagNames.getOrDefault(i.getQuestion().getQuestionId(), List.of()))).toList();
    }
    return new GrammarCollectionResponse(collection.getCollectionId(), collection.getKind().name(),
        collection.getTitle(), collection.getDescription(), collection.getDesigncraftJobId(),
        itemCount, collection.getCreatedAt(), items);
  }

  private Map<Long, List<String>> loadTagNames(List<Long> questionIds) {
    Map<Long, List<String>> out = new HashMap<>();
    if (questionIds.isEmpty()) {
      return out;
    }
    for (QuestionGrammarTag gt : questionTagRepository.findByQuestion_QuestionIdIn(questionIds)) {
      out.computeIfAbsent(gt.getQuestion().getQuestionId(), k -> new ArrayList<>())
          .add(gt.getTag().getNameKo());
    }
    return out;
  }
}
