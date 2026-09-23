import { cardCompanyTestWorkflow } from './cardCompanyTestWorkflow.js';

// Registry — the "workflow adapter" layer on top of the generic collector engine
// (Section 6: Local Collector core stays generic, workflows plug in by source_code).
const REGISTRY = {
  [cardCompanyTestWorkflow.code]: cardCompanyTestWorkflow,
};

export function resolveWorkflow(sourceCode) {
  const workflow = REGISTRY[sourceCode];
  if (!workflow) throw new Error(`No workflow registered for sourceCode=${sourceCode}`);
  return workflow;
}
