export interface CommissionDTO {
  contractId: string;
  agentId: string;
  agentEmail: string;
  projectName: string;
  propertyRef: string;
  signedAt: string;
  agreedPrice: number;
  ratePercent: number;
  fixedAmount: number;
  commissionAmount: number;
}

export interface CommissionRuleResponse {
  id: string;
  societeId: string;
  projectId: string | null;
  projectName: string | null;
  ratePercent: number;
  fixedAmount: number | null;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export interface CommissionRuleRequest {
  projectId?: string;
  ratePercent: number;
  fixedAmount?: number;
  effectiveFrom: string;
  effectiveTo?: string;
}
