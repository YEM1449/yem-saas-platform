import { Injectable } from '@angular/core';

// Status types used across the application
export type VenteStatut =
  | 'PROSPECT' | 'OPTION' | 'RESERVE' | 'EN_RETRACTATION' | 'ACOMPTE'
  | 'COMPROMIS' | 'FINANCEMENT' | 'ACTE'
  | 'LIVRE_AVEC_RESERVES' | 'RESERVES_LEVEES' | 'LIVRE_DEFINITIF' | 'ANNULE';
export type PropertyStatus = 'DRAFT' | 'ACTIVE' | 'RESERVED' | 'SOLD' | 'WITHDRAWN' | 'ARCHIVED';
export type ContactStatus = 'NEW_PROSPECT' | 'QUALIFIED_PROSPECT' | 'ACTIVE_CLIENT' | 'COMPLETED_CLIENT' | 'LOST' | 'REFERRAL';
export type ContractStatus = 'PENDING' | 'GENERATED' | 'SIGNED';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED';

@Injectable({
  providedIn: 'root'
})
export class StatusService {

  // Vente status mappings
  private readonly VENTE_LABELS: Record<VenteStatut, string> = {
    PROSPECT: 'Prospect',
    OPTION: 'Option',
    RESERVE: 'Réservé',
    EN_RETRACTATION: 'Délai de rétractation',
    ACOMPTE: 'Acompte',
    COMPROMIS: 'Compromis',
    FINANCEMENT: 'Financement',
    ACTE: 'Acte notarié',
    LIVRE_AVEC_RESERVES: 'Livré (réserves)',
    RESERVES_LEVEES: 'Réserves levées',
    LIVRE_DEFINITIF: 'Livré',
    ANNULE: 'Annulé',
  };

  private readonly VENTE_CLASSES: Record<VenteStatut, string> = {
    PROSPECT: 'badge-info',
    OPTION: 'badge-info',
    RESERVE: 'badge-info',
    EN_RETRACTATION: 'badge-warning',
    ACOMPTE: 'badge-info',
    COMPROMIS: 'badge-info',
    FINANCEMENT: 'badge-warning',
    ACTE: 'badge-primary',
    LIVRE_AVEC_RESERVES: 'badge-warning',
    RESERVES_LEVEES: 'badge-primary',
    LIVRE_DEFINITIF: 'badge-success',
    ANNULE: 'badge-error',
  };

  private readonly VENTE_DESCRIPTIONS: Record<VenteStatut, string> = {
    PROSPECT: 'Intérêt commercial initial — pas encore d\'engagement',
    OPTION: 'Bien bloqué temporairement (24-72h) en attente de réservation',
    RESERVE: 'Réservation signée avec dépôt de garantie (≤ 5%, Art. 618-4)',
    EN_RETRACTATION: 'Délai légal de rétractation en cours (7 jours, Art. 618-3)',
    ACOMPTE: 'Acompte versé par l\'acquéreur',
    COMPROMIS: 'Avant-contrat signé — financement et conditions suspensives en cours',
    FINANCEMENT: 'Dossier de financement déposé — en attente d\'accord bancaire',
    ACTE: 'Acte authentique signé devant notaire — transfert de propriété effectué',
    LIVRE_AVEC_RESERVES: 'Bien livré avec réserves à lever',
    RESERVES_LEVEES: 'Toutes les réserves de livraison sont levées',
    LIVRE_DEFINITIF: 'Bien remis à l\'acquéreur — vente finalisée',
    ANNULE: 'Vente annulée — voir motif dans la fiche',
  };

  // Property status mappings
  private readonly PROPERTY_LABELS: Record<PropertyStatus, string> = {
    DRAFT: 'Brouillon',
    ACTIVE: 'Disponible',
    RESERVED: 'Réservé',
    SOLD: 'Vendu',
    WITHDRAWN: 'Retiré',
    ARCHIVED: 'Archivé',
  };

  private readonly PROPERTY_CLASSES: Record<PropertyStatus, string> = {
    DRAFT: 'badge-draft',
    ACTIVE: 'badge-active',
    RESERVED: 'badge-reserved',
    SOLD: 'badge-sold',
    WITHDRAWN: 'badge-withdrawn',
    ARCHIVED: 'badge-archived',
  };

  // Contact status mappings
  private readonly CONTACT_LABELS: Record<ContactStatus, string> = {
    NEW_PROSPECT: 'Nouveau prospect',
    QUALIFIED_PROSPECT: 'Prospect qualifié',
    ACTIVE_CLIENT: 'Client actif',
    COMPLETED_CLIENT: 'Client finalisé',
    LOST: 'Perdu',
    REFERRAL: 'Parrainage',
  };

  private readonly CONTACT_CLASSES: Record<ContactStatus, string> = {
    NEW_PROSPECT: 'badge-new-prospect',
    QUALIFIED_PROSPECT: 'badge-qualified-prospect',
    ACTIVE_CLIENT: 'badge-active-client',
    COMPLETED_CLIENT: 'badge-completed-client',
    LOST: 'badge-lost',
    REFERRAL: 'badge-referral',
  };

  // Contract status mappings
  private readonly CONTRACT_LABELS: Record<ContractStatus, string> = {
    PENDING: 'En attente',
    GENERATED: 'Généré',
    SIGNED: 'Signé',
  };

  private readonly CONTRACT_CLASSES: Record<ContractStatus, string> = {
    PENDING: 'badge-pending',
    GENERATED: 'badge-info',
    SIGNED: 'badge-success',
  };

  // Task status mappings
  private readonly TASK_LABELS: Record<TaskStatus, string> = {
    OPEN: 'Ouvert',
    IN_PROGRESS: 'En cours',
    COMPLETED: 'Terminé',
    CANCELLED: 'Annulé',
  };

  private readonly TASK_CLASSES: Record<TaskStatus, string> = {
    OPEN: 'badge-info',
    IN_PROGRESS: 'badge-warning',
    COMPLETED: 'badge-success',
    CANCELLED: 'badge-error',
  };

  // Reservation status mappings
  private readonly RESERVATION_LABELS: Record<ReservationStatus, string> = {
    PENDING: 'En attente',
    CONFIRMED: 'Confirmé',
    EXPIRED: 'Expiré',
    CANCELLED: 'Annulé',
  };

  private readonly RESERVATION_CLASSES: Record<ReservationStatus, string> = {
    PENDING: 'badge-pending',
    CONFIRMED: 'badge-confirmed',
    EXPIRED: 'badge-expired',
    CANCELLED: 'badge-cancelled',
  };

  // Vente methods
  getVenteLabel(status: VenteStatut): string {
    return this.VENTE_LABELS[status] ?? status;
  }

  getVenteClass(status: VenteStatut): string {
    return this.VENTE_CLASSES[status] ?? '';
  }

  getVenteDescription(status: VenteStatut): string {
    return this.VENTE_DESCRIPTIONS[status] ?? '';
  }

  // Property methods
  getPropertyLabel(status: PropertyStatus): string {
    return this.PROPERTY_LABELS[status] ?? status;
  }

  getPropertyClass(status: PropertyStatus): string {
    return this.PROPERTY_CLASSES[status] ?? '';
  }

  // Contact methods
  getContactLabel(status: ContactStatus): string {
    return this.CONTACT_LABELS[status] ?? status;
  }

  getContactClass(status: ContactStatus): string {
    return this.CONTACT_CLASSES[status] ?? '';
  }

  // Contract methods
  getContractLabel(status: ContractStatus): string {
    return this.CONTRACT_LABELS[status] ?? status;
  }

  getContractClass(status: ContractStatus): string {
    return this.CONTRACT_CLASSES[status] ?? '';
  }

  // Task methods
  getTaskLabel(status: TaskStatus): string {
    return this.TASK_LABELS[status] ?? status;
  }

  getTaskClass(status: TaskStatus): string {
    return this.TASK_CLASSES[status] ?? '';
  }

  // Reservation methods
  getReservationLabel(status: ReservationStatus): string {
    return this.RESERVATION_LABELS[status] ?? status;
  }

  getReservationClass(status: ReservationStatus): string {
    return this.RESERVATION_CLASSES[status] ?? '';
  }
}