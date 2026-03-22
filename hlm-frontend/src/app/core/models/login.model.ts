export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface MeResponse {
  userId: string;
  societeId: string;
  role?: string;
  platformRole?: string;
}

export interface InvitationDetails {
  prenom: string;
  email: string;
  societeNom: string;
  role: string;
  expireDans: string;
}

export interface ActivationRequest {
  motDePasse: string;
  confirmationMotDePasse: string;
  consentementCgu: boolean;
  consentementCguVersion: string;
}
