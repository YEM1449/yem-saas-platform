export type LotDisplayStatus = 'DISPONIBLE' | 'RESERVE' | 'VENDU' | 'LIVRE';

/** Colour tokens per lot status — matches design system */
export const LOT_STATUS_COLORS: Record<LotDisplayStatus, string> = {
  DISPONIBLE: '#3B82F6',
  RESERVE:    '#F59E0B',
  VENDU:      '#10B981',
  LIVRE:      '#6B7280',
};

export const LOT_STATUS_LABELS: Record<LotDisplayStatus, string> = {
  DISPONIBLE: 'Disponible',
  RESERVE:    'Réservé',
  VENDU:      'Vendu',
  LIVRE:      'Livré',
};

export interface LotStatusSnapshot {
  meshId:   string;
  lotId:    string;
  statut:   LotDisplayStatus;
  typology: string | null;
  surface:  number | null;
  prix:     number | null;
}
