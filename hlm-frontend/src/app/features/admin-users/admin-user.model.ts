export type MembreStatut = 'ACTIF' | 'INVITE' | 'INVITATION_EXPIREE' | 'BLOQUE' | 'RETIRE';

export interface MembreDto {
  id: string;
  email: string;
  prenom: string;
  nomFamille: string;
  nomComplet: string;
  telephone?: string;
  poste?: string;
  role: string;
  actif: boolean;
  enabled: boolean;
  compteBloque: boolean;
  derniereConnexion?: string;
  dateAjout?: string;
  invitationEnvoyeeAt?: string;
  invitationExpireAt?: string;
  statut: MembreStatut;
  version: number;
}

export interface InviterUtilisateurRequest {
  email: string;
  prenom: string;
  nomFamille: string;
  telephone?: string;
  poste?: string;
  role: string;
  langueInterface?: string;
  messagePersonnalise?: string;
}

export interface ChangerRoleRequest {
  nouveauRole: string;
  version: number;
}

export interface ModifierUtilisateurRequest {
  prenom?: string;
  nomFamille?: string;
  telephone?: string;
  poste?: string;
}

export interface RetirerUtilisateurRequest {
  raison?: string;
  version: number;
}

export interface UserDataExport {
  userId: string;
  email: string;
  exportedAt: string;
  [key: string]: unknown;
}
