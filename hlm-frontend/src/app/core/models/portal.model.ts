export interface MagicLinkRequest {
  email: string;
  societeKey: string;
}

export interface MagicLinkResponse {
  message: string;
  magicLinkUrl: string;
}

export interface PortalTokenVerifyResponse {
  accessToken: string;
}

export interface PortalContract {
  id: string;
  propertyRef: string;
  propertyType: string;
  projectName: string;
  status: 'PENDING' | 'GENERATED' | 'SIGNED';
  agreedPrice: number;
  signedAt: string | null;
  docId: string | null;
}


export interface PortalProperty {
  id: string;
  reference: string;
  type: string;
  title: string;
  surfaceAreaSqm: number | null;
  city: string | null;
  address: string | null;
  description: string | null;
  projectName: string;
}

export interface PortalTenantInfo {
  tenantName: string;
  logoUrl: string | null;
  // Legal identity + recorded data-protection info for the legal pages (#025/#026).
  // Any field may be null until the société records it.
  legalName?: string | null;
  rc?: string | null;
  ice?: string | null;
  adresseSiege?: string | null;
  dpoEmail?: string | null;
  dpoName?: string | null;
  cndpNumber?: string | null;
  cndpDeclarationDate?: string | null;
}
