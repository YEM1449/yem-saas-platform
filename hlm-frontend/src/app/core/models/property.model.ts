export interface Property {
  id: string;
  type: string;
  status: string;
  referenceCode: string;
  title: string;
  description: string | null;
  price: number | null;
  currency: string | null;
  address: string | null;
  city: string | null;
  region: string | null;
  surfaceAreaSqm: number | null;
  bedrooms: number | null;
  bathrooms: number | null;
  createdAt: string;
}
