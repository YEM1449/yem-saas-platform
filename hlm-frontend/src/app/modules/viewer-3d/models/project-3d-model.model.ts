export interface Lot3dMappingEntry {
  meshId:         string;
  immeubleMeshId: string | null;
  trancheMeshId:  string | null;
  lotId:          string;
  lotRef:         string;
  typology:       string | null;
  surface:        number | null;
  prix:           number | null;
}

export interface Project3dModel {
  id:              string;
  projetId:        string;
  glbPresignedUrl: string;
  expiresAt:       string;
  dracoCompressed: boolean;
  mappings:        Lot3dMappingEntry[];
}

export interface UploadUrlResponse {
  uploadUrl: string;
  fileKey:   string;
  expiresAt: string;
}

export interface LoadProgress {
  loaded: number;
  total:  number;
  /** 0–1 */
  ratio:  number;
}
