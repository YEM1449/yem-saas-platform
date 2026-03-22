export interface SocieteDto {
  id: string; nom: string; key?: string; nomCommercial?: string;
  pays: string; actif: boolean; planAbonnement: string; periodeEssai: boolean;
  emailDpo?: string; dpoNom?: string; emailContact?: string; telephone?: string;
  adresse?: string; adresseSiege?: string; ville?: string;
  logoUrl?: string; couleurPrimaire?: string; langueDefaut?: string; devise?: string;
  baseJuridiqueDefaut?: string; numeroAgrement?: string; typeActivite?: string;
  complianceScore: number; version: number; createdAt: string; updatedAt: string;
}

export interface SocieteDetailDto extends SocieteDto {
  formeJuridique?: string; capitalSocial?: number; siretIce?: string; rc?: string;
  ifNumber?: string; patente?: string; tvaNumber?: string; cnssNumber?: string;
  codePostal?: string; region?: string; telephone2?: string; siteWeb?: string;
  linkedinUrl?: string; telephoneDpo?: string; numeroCndp?: string; numeroCnil?: string;
  dateDeclarationCndp?: string; dateDeclarationCnil?: string; dureeRetentionJours?: number;
  carteProfessionnelle?: string; caisseGarantie?: string;
  assuranceRc?: string; dateAgrement?: string; dateExpirationAgrement?: string;
  zonesIntervention?: string; couleurSecondaire?: string; fuseauHoraire?: string;
  formatDate?: string; mentionsLegales?: string;
  maxUtilisateurs?: number; maxBiens?: number; maxContacts?: number; maxProjets?: number;
  dateDebutAbonnement?: string; dateFinAbonnement?: string;
  dateSuspension?: string; raisonSuspension?: string; createdById?: string;
  notesInternes?: string;
}

export interface SocieteStatsDto {
  totalMembres: number; membresActifs: number;
  totalContacts: number; totalBiens: number; totalProjets: number; totalContrats: number;
  maxUtilisateurs?: number; maxBiens?: number; maxContacts?: number; maxProjets?: number;
}

export interface SocieteComplianceDto {
  score: number; hasNom: boolean; hasEmailDpo: boolean; hasAdresse: boolean;
  hasRegistreNumber: boolean; hasDpoNom: boolean; hasBaseJuridique: boolean;
  missingFields: string[];
}

export interface MembreSocieteDto {
  userId: string; prenom?: string; nom?: string; email?: string;
  role: string; actif: boolean; dateAjout: string; dateRetrait?: string;
}

export interface CreateSocieteRequest {
  nom: string; pays: string; emailDpo?: string; planAbonnement?: string; notesInternes?: string;
}

export interface UpdateSocieteRequest {
  version: number; nom?: string; pays?: string; emailDpo?: string; dpoNom?: string;
  numeroCndp?: string; numeroCnil?: string; baseJuridiqueDefaut?: string;
  planAbonnement?: string; maxUtilisateurs?: number; maxBiens?: number;
  maxContacts?: number; maxProjets?: number; notesInternes?: string;
}

export interface AddMembreRequest { userId: string; role: string; }

export interface InviteUserRequest {
  email: string;
  prenom: string;
  nomFamille: string;
  role: string;
  telephone?: string;
  poste?: string;
}

export interface UpdateMembreRoleRequest { nouveauRole: string; raison?: string; }

export interface ImpersonateResponse {
  token: string; targetUserId: string; targetSocieteId: string;
  targetUserEmail: string; targetRole: string; ttlSeconds: number;
}

export interface PageResponse<T> {
  content: T[]; totalElements: number; totalPages: number; size: number; number: number;
}
