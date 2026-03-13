export interface MagicLinkRequest {
  email: string;
  tenantKey: string;
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
  status: 'DRAFT' | 'SIGNED' | 'CANCELED';
  agreedPrice: number;
  signedAt: string | null;
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
}
