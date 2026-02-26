export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';

export interface Project {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectKpi {
  projectId: string;
  projectName: string;
  totalProperties: number;
  propertiesByType: Record<string, number>;
  statusBreakdown: Record<string, number>;
  depositsCount: number;
  depositsTotalAmount: number;
  salesCount: number;
  salesTotalAmount: number;
}

export interface ProjectCreateRequest {
  name: string;
  description?: string;
}

export interface ProjectUpdateRequest {
  name?: string;
  description?: string;
  status?: ProjectStatus;
}
