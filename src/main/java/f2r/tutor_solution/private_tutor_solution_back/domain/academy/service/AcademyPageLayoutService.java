package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import tools.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageLayout;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyDefaultLayoutFactory;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlock;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyLayoutBlockType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.layout.AcademyPageLayoutSchema;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyPageLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Academy 페이지 레이아웃의 보존 경계.
 *
 * <p>Phase 1 범위: semantic 레이아웃 1건 생성/조회 + allowlist 검증 + 1 Academy 1 레이아웃 보장.
 * 빌더 UI/공개 렌더/수정·재발행은 이후 Phase 로 미룬다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademyPageLayoutService {

  private final AcademyPageLayoutRepository academyPageLayoutRepository;
  private final AcademyDefaultLayoutFactory defaultLayoutFactory;
  private final ObjectMapper objectMapper;

  /**
   * 주어진 semantic 레이아웃을 Academy 의 유일한 레이아웃으로 저장한다.
   *
   * @throws IllegalArgumentException allowlist 외 block 타입이거나 이미 레이아웃이 존재할 때
   */
  @Transactional
  public AcademyPageLayout createLayout(Academy academy, AcademyPageLayoutSchema schema) {
    validateAllowlist(schema);
    if (academyPageLayoutRepository.existsByAcademy_AcademyId(academy.getAcademyId())) {
      throw new IllegalArgumentException("이미 페이지 레이아웃이 존재합니다.");
    }
    AcademyPageLayout layout = AcademyPageLayout.builder()
        .academy(academy)
        .layoutVersion(schema.version())
        .layoutJson(serialize(schema))
        .build();
    return academyPageLayoutRepository.save(layout);
  }

  /** 기존 Academy 데이터로 기본 레이아웃을 생성·저장한다. */
  @Transactional
  public AcademyPageLayout createDefaultLayout(Academy academy) {
    return createLayout(academy, defaultLayoutFactory.generate(academy));
  }

  /**
   * Academy 레이아웃을 upsert 한다(빌더 저장). 저장된 레이아웃이 있으면 내용을 덮어쓰고,
   * 없으면 새로 만든다. 1 Academy 1 레이아웃 불변식을 유지한다.
   *
   * @throws IllegalArgumentException allowlist 외 block 타입이 포함될 때
   */
  @Transactional
  public AcademyPageLayout saveLayout(Academy academy, AcademyPageLayoutSchema schema) {
    validateAllowlist(schema);
    String layoutJson = serialize(schema);
    return academyPageLayoutRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .map(existing -> {
          existing.updateLayout(schema.version(), layoutJson);
          return existing;
        })
        .orElseGet(() -> academyPageLayoutRepository.save(AcademyPageLayout.builder()
            .academy(academy)
            .layoutVersion(schema.version())
            .layoutJson(layoutJson)
            .build()));
  }

  /**
   * Academy 레이아웃을 물리 삭제한다. 삭제 후 {@link #resolveLayoutSchema}는 기본 레이아웃으로
   * fallback 하므로 공개 렌더는 계속 안전하다. 저장된 레이아웃이 없으면 no-op(멱등).
   */
  @Transactional
  public void deleteLayout(Academy academy) {
    academyPageLayoutRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .ifPresent(academyPageLayoutRepository::delete);
  }

  /**
   * Academy 의 레이아웃 스키마를 결정적으로 해석한다(legacy 호환).
   *
   * <p>저장된 레이아웃이 있으면 역직렬화해 반환하고, 없으면(레이아웃을 만들기 전의 레거시 Academy)
   * 기본 레이아웃을 생성해 반환한다. read-only 이며 아무것도 저장하지 않는다.
   *
   * @throws IllegalArgumentException 저장된 layout_json 이 손상돼 역직렬화할 수 없을 때
   */
  public AcademyPageLayoutSchema resolveLayoutSchema(Academy academy) {
    return academyPageLayoutRepository.findByAcademy_AcademyId(academy.getAcademyId())
        .map(layout -> deserialize(layout.getLayoutJson()))
        .orElseGet(() -> defaultLayoutFactory.generate(academy));
  }

  /** allowlist 검증: 모든 block 타입이 허용된 semantic 타입이어야 한다. */
  private void validateAllowlist(AcademyPageLayoutSchema schema) {
    if (schema == null || schema.blocks() == null) {
      throw new IllegalArgumentException("레이아웃 스키마가 비어 있습니다.");
    }
    for (AcademyLayoutBlock block : schema.blocks()) {
      if (block == null || !AcademyLayoutBlockType.isAllowed(block.type())) {
        String type = block == null ? "null" : block.type();
        throw new IllegalArgumentException("허용되지 않은 레이아웃 block 타입입니다: " + type);
      }
    }
  }

  private String serialize(AcademyPageLayoutSchema schema) {
    try {
      return objectMapper.writeValueAsString(schema);
    } catch (Exception e) {
      throw new IllegalArgumentException("레이아웃 직렬화에 실패했습니다.", e);
    }
  }

  private AcademyPageLayoutSchema deserialize(String layoutJson) {
    try {
      return objectMapper.readValue(layoutJson, AcademyPageLayoutSchema.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("저장된 레이아웃을 해석할 수 없습니다.", e);
    }
  }
}
