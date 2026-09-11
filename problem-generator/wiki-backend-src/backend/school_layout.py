"""School Layout Profiles — rendering parameters, not design copies.

Each profile only tunes the Common Exam Renderer (backend/weekly_paper.py):
paper size, column geometry, spacing, passage boxes, constructed-response
space, footer. No school logos, fonts, or scanned artifacts are reproduced.

Evidence grades (see investigation report):
- CONFIRMED: verified in IMAGE PAST_EXAM id=57~63 body content or in the
  Production round-1 FINAL HTML (final_deliverables id=1, 2).
- INFERRED: reasonably derived from the above (e.g. same A4 family metrics).
- UNKNOWN: undecidable without the original image binary. UNKNOWN slots keep
  the existing stable renderer values — no invented numbers.
"""
from __future__ import annotations

DEFAULT_PROFILE = {
    # CONFIRMED (round-1 FINAL CSS): A4 portrait, fixed 210x297mm pages.
    'paper_size': 'A4 portrait',
    'page_width_mm': 210,
    'page_height_mm': 297,
    # CONFIRMED (round-1 FINAL CSS): 2 flex columns.
    'columns': 2,
    # INFERRED: round-1 FINAL uses gap 7mm and no divider rule; the past-exam
    # body does not expose physical metrics, so the stable value is kept.
    'column_gap_mm': 7,
    'divider': None,  # UNKNOWN: keep None (no invented divider).
    # CONFIRMED (round-1 FINAL CSS padding 12/11/10mm).
    'margin_top_mm': 12,
    'margin_side_mm': 11,
    'margin_bottom_mm': 10,
    # CONFIRMED: question blocks keep together; group-end gap separates units.
    'question_spacing_mm': 3.2,
    'group_end_spacing_mm': 6.0,
    # INFERRED: slack distribution cap from the existing renderer.
    'max_spread_mm': 2.2,
    # CONFIRMED (round-1 FINAL CSS): boxed passages, 1mm/4.6mm rhythm.
    'passage_padding_mm': 1.5,
    'passage_indent_mm': 4.6,
    # CONFIRMED (past-exam bodies id=58~63): conditions BOX + free answer space.
    # Minimum answer lines mirror the established answer-line rhythm.
    'constructed_min_answer_lines': 2,
    # CONFIRMED (past-exam bodies): "총7쪽 중 N쪽" + school name footer.
    'footer_style': 'page_of_total',
    # CONFIRMED (round-1 FINAL CSS): 9.5pt body.
    'base_font_pt': 9.5,
}

# Evidence: values identical to DEFAULT_PROFILE unless noted. The past-exam
# bodies (공통지문 boxes, 보기 a~f combos, 서답형 condition boxes, per-question
# scores) confirm the same A4 2-column family; physical metrics stay inherited.
SHINGU_MIDDLE_2 = dict(
    DEFAULT_PROFILE,
    school='신구중',
    grade='2학년',
)

PROFILES = {
    ('신구중', '2학년'): SHINGU_MIDDLE_2,
}


def get_profile(school: str = '', grade: str = '') -> dict:
    """Return the school layout profile; unknown schools fall back to defaults."""
    base = PROFILES.get((school.strip(), grade.strip()), DEFAULT_PROFILE)
    return dict(base)
