export interface User {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: 'ADMIN' | 'USER';
}

export interface DataProcess {
  id: number;
  name: string;
  description: string;
  inputFormat: string;
  outputFormat: string;
  createdAt: string;
  ruleCount: number;
  lastRunStatus: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | null;
  lastRunAt: string | null;
  lastRunExecutionId: number | null;
  ownerId: number;
}

export interface ProcessRule {
  id: number;
  executionOrder: number;
  ruleName: string;
  ruleType: string;
  sourceField: string;
  sourceField2: string | null;
  targetField: string | null;
}

export interface ProcessDetail extends DataProcess {
  rules: ProcessRule[];
}

export interface ProcessFormData {
  name: string;
  description: string;
  inputFormat: string;
  outputFormat: string;
}

export interface ProcessRuleFormData {
  ruleId: number;
  executionOrder: number;
  sourceField: string;
  sourceField2: string | null;
  targetField: string | null;
}

export interface Rule {
  id: number;
  name: string;
  type: string;
  description: string;
}

export interface Execution {
  id: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  processName: string;
  ownerId: number;
}

export interface ExecutionDetail extends Execution {
  processId: number;
  inputFormat: string;
  outputFormat: string;
  durationMs: number | null;
  inputFileName: string | null;
  hasOutput: boolean;
  rules: ProcessRule[];
}

export interface ExecutionContent {
  input: string | null;
  output: string | null;
  inputTruncated: boolean;
  outputTruncated: boolean;
}

export interface PreviewStep {
  order: number;
  ruleType: string;
  label: string;
  record: Record<string, unknown> | null;
}

export interface PreviewResponse {
  status: 'OK' | 'ERROR' | 'NO_RULES';
  processName: string;
  inputFormat: string;
  outputFormat: string;
  recordCount: number;
  previewedCount: number;
  truncated: boolean;
  before: Record<string, unknown>[];
  after: Record<string, unknown>[] | null;
  steps: PreviewStep[];
  failedRuleOrder: number | null;
  errorMessage: string | null;
}