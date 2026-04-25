export interface Property {
  id: string;
  type: string;
  category: string | null;
  status: string;
  referenceCode: string;
  title: string;
  description: string | null;
  notes: string | null;
  price: number | null;
  currency: string | null;
  commissionRate: number | null;
  estimatedValue: number | null;
  address: string | null;
  city: string | null;
  region: string | null;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  titleDeedNumber: string | null;
  cadastralReference: string | null;
  ownerName: string | null;
  legalStatus: string | null;
  surfaceAreaSqm: number | null;
  landAreaSqm: number | null;
  bedrooms: number | null;
  bathrooms: number | null;
  floors: number | null;
  parkingSpaces: number | null;
  hasGarden: boolean | null;
  hasPool: boolean | null;
  buildingYear: number | null;
  floorNumber: number | null;
  zoning: string | null;
  isServiced: boolean | null;
  listedForSale: boolean;
  projectId: string | null;
  projectName: string | null;
  buildingName: string | null;
  immeubleId: string | null;
  immeubleName: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string | null;
  deletedAt: string | null;
  publishedAt: string | null;
  soldAt: string | null;
  reservedAt: string | null;
  orientation: string | null;
  trancheId: string | null;
}

export interface PropertyMedia {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  sortOrder: number;
  uploadedAt: string;
}

export interface ImportRowError {
  row: number;
  message: string;
}

export interface ImportResult {
  imported: number;
  errors: ImportRowError[];
}
