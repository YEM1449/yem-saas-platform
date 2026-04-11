export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  requiresSocieteSelection?: boolean;
  societes?: SocieteChoice[];
}

export interface SocieteChoice {
  id: string;
  nom: string;
}

export interface SwitchSocieteRequest {
  societeId: string;
}

export interface MeResponse {
  userId: string;
  societeId: string;
  role?: string;
  platformRole?: string;
  langueInterface?: string;
  societeLogoUrl?: string;
  isImpersonating?: boolean;
  impersonationTargetEmail?: string;
  prenom?: string;
  email?: string;
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
