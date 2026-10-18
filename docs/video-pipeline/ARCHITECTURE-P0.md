# GPU AI Video Pipeline — P0 Architecture (Issue #779)

Status: DRAFT / No deployment authorization

## Scope
Shared orchestration for two project types: `GRAMMAR_DRAMA` (MASTER/BOOSTER Unit episodes with teacher and recurring student cast) and `AI_AGENT` (original Korean SF story, Korean and English editions, optional English learning mode).

## Core records (schema version 1)
- Project: id, type, sourceAssetRefs, sourceVersion, ownerTenant, rightsStatus
- Episode: id, projectId, unitOrStoryRef, learningObjectives, status, revision
- Character: id, voiceProfilesByLocale, visualReferenceIds, characterBibleVersion
- Scene: id, episodeId, settingRef, orderedShotIds, approvedScriptRevision
- Shot: id, sceneId, camera, prompt, seed, modelVersion, durationTarget, renderJobIds
- Dialogue: id, sceneId, characterId, sourceKo, localizedEn, reviewStatus, timing
- LanguageTrack: episodeId, locale, audioAssetId, subtitleAssetId, syncReviewStatus
- LearningAnnotation: dialogueId, expression, explanationKo, difficulty, quizItems
- RenderJob: id, shotId, backend, status, retries, costEstimate, actualCost, artifacts
- Artifact: id, immutableUri, hash, sourceRevision, mediaType, qcStatus

## State transitions
DRAFT -> SCRIPT_REVIEW -> STORYBOARD -> READY -> RENDERING -> QC -> APPROVED.
Revisions after approval create new versions, never silently replace original assets. Failed jobs can retry by shot with idempotency key; manual approval required for publication.

## Adapters
`ScriptAdapter`, `StoryboardAdapter`, `ImageVideoRenderAdapter`, `VoiceAdapter`, `LipSyncAdapter`, `SubtitleAdapter`, `LearningAdapter`, `QualityGate`.
Initial backend: deterministic MOCK. Future backend: ComfyUI worker behind authenticated queue, bounded concurrency and GPU budget. Never call paid APIs without approval.

## Project-specific rules
### Grammar drama
Map every episode to exact textbook/unit/version and pedagogical objective. Stable teacher/student cast; students ask questions, make mistakes and explain solutions. Human review of English grammar and answer keys required.

### AI AGENT
Preserve the original 14-episode Korean story and character bible. English localization is a derivative track, not a rewrite of the canonical Korean script. Reuse shots where possible; regenerate language-dependent lip sync only when needed. Learning mode references dialogue IDs for bilingual subtitles, replay, shadowing and quizzes.

## Security and operations
Tenant isolation; no production DB migration or AWS change in P0. Existing DesignCraft admission gate remains independent. Track provenance, copyright permissions, render cost, and audit trail. PR and CI before any merge; deploy only with separate authorization.

## Acceptance tests (planned)
1. Validate both project types against v1 schema.
2. Verify canonical source cannot be mutated by localization.
3. Verify shot retry does not duplicate approved artifacts.
4. Verify bilingual dialogue alignment and annotation references.
5. Verify unauthorized tenants cannot access assets.
6. Verify mock rendering works without GPU, API keys, or AWS.

## Unknowns
Actual MASTER/BOOSTER source file structure, available GPU model/VRAM, installed ComfyUI nodes, current DesignCraft interfaces, and commercial model licensing must be inspected before implementation decisions.
