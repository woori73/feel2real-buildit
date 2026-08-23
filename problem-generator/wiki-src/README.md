# BuildIt Wiki source workspace

This directory is the GitHub-side source workspace for the Wiki UI at `/problem-generator/wiki/`.

## V1 integration rules

- Preserve the existing problem-generator backend, DB, Digital Twin, n8n, question DB, exam-paper, student wrong-note, textbook DB, and school DB behavior.
- Treat the Wiki as an educator workspace, not a one-shot generator.
- The first screen is an Educator State / Current Scenario that asks what the educator is preparing and routes into existing tools.
- Every production result should eventually be stored as reusable context for Educator Brain.
- BuildIt Assistant should use inexpensive/free models for routing, summarization and state updates; high-quality problem/material generation is routed to the premium generation model.
- Do not merge this branch until the recovered Wiki source and production API mapping have been regression-checked.

## First UX state

`BuildIt logo + flowing wave → Educator State → BuildIt Assistant → existing creation/student/brain functions`

The initial scenario asks the educator which students they are preparing for and what they want to create. Returning users should see their current goal, known context, missing context, and next action.
