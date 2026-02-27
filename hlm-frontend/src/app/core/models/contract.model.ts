export type SaleContractStatus = 'DRAFT' | 'SIGNED' | 'CANCELED';

export interface ContractResponse {
  id: string;
  tenantId: string;
  projectId: string;
  projectName: string;
  propertyId: string;
  buyerContactId: string;
  agentId: string;
  status: SaleContractStatus;
  agreedPrice: number;
  listPrice: number | null;
  sourceDepositId: string | null;
  createdAt: string;
  signedAt: string | null;
  canceledAt: string | null;
  buyerType: 'PERSON' | 'COMPANY' | null;
  buyerDisplayName: string | null;
  buyerPhone: string | null;
  buyerEmail: string | null;
  buyerIce: string | null;
  buyerAddress: string | null;
}

export interface ContractPage {
  content: ContractResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
