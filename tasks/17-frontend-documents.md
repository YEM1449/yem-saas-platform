# Task 17 — Angular Frontend for Document Attachments

## Priority: HIGH
## Effort: 2 hours
## Backend API: `/api/documents` (4 endpoints, ready)

## What to Build

A reusable document upload/list widget that can be embedded in contact detail, contract detail, deposit detail, and property detail pages.

## Backend Endpoints to Consume

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/documents?entityType=CONTACT&entityId={uuid}` | Upload document (multipart) |
| GET | `/api/documents?entityType=CONTACT&entityId={uuid}` | List documents for entity |
| GET | `/api/documents/{id}/download` | Download document |
| DELETE | `/api/documents/{id}` | Delete document |

Check the actual DTOs:
```bash
cat hlm-backend/src/main/java/com/yem/hlm/backend/document/api/dto/DocumentResponse.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/document/domain/DocumentEntityType.java
```

## Files to Create

A **reusable component** (not a routed page) that can be embedded anywhere:

```
hlm-frontend/src/app/features/documents/
├── document.model.ts              # TypeScript interfaces
├── document.service.ts            # HttpClient API calls
├── document-list.component.ts     # Reusable: shows docs + upload for an entity
├── document-list.component.html
└── document-list.component.css
```

## Model

```typescript
export type DocumentEntityType = 'CONTACT' | 'CONTRACT' | 'DEPOSIT' | 'PROPERTY' | 'RESERVATION';

export interface DocumentResponse {
  id: string;
  entityType: DocumentEntityType;
  entityId: string;
  fileName: string;
  mimeType: string;
  fileSize?: number;
  description?: string;
  uploadedBy: string;
  createdAt: string;
}
```

## Reusable Component Design

The component accepts `entityType` and `entityId` as inputs:

```typescript
@Component({
  selector: 'app-document-list',
  standalone: true,
  // ...
})
export class DocumentListComponent implements OnInit {
  @Input({ required: true }) entityType!: DocumentEntityType;
  @Input({ required: true }) entityId!: string;
  
  documents: DocumentResponse[] = [];
  uploading = false;
  
  // Load documents on init
  // Upload via file input
  // Download via service.download()
  // Delete with confirmation
}
```

## Integration Points

Embed the component on existing detail pages:

### `contact-detail.component.html`
```html
<app-document-list entityType="CONTACT" [entityId]="contact.id" />
```

### `contract-detail.component.html`
```html
<app-document-list entityType="CONTRACT" [entityId]="contract.id" />
```

### `property-detail.component.html`
```html
<app-document-list entityType="PROPERTY" [entityId]="property.id" />
```

## UI Design

- File list table: name, type icon (PDF/image/other), size, upload date, actions (download/delete)
- Upload area: drag-and-drop or file picker button
- Optional description field on upload
- File type icons based on mimeType
- Delete with confirmation dialog
- Loading spinner during upload

## Acceptance Criteria

- [ ] Reusable `DocumentListComponent` works with any entity type
- [ ] File upload (multipart) works
- [ ] File download works (opens in new tab or triggers download)
- [ ] File delete with confirmation works
- [ ] Component embedded on contact-detail, contract-detail, property-detail pages
- [ ] Frontend builds successfully
