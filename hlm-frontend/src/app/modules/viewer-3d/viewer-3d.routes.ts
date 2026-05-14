import { Routes } from '@angular/router';

export const VIEWER_3D_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./components/project-viewer-3d/project-viewer-3d.component').then(
        m => m.ProjectViewer3dComponent
      ),
  },
  {
    path: 'mappings',
    loadComponent: () =>
      import('./components/mesh-mapping-admin/mesh-mapping-admin.component').then(
        m => m.MeshMappingAdminComponent
      ),
    data: { requiresRole: 'ADMIN' },
  },
];
