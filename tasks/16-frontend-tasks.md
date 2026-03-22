# Task 16 — Angular Frontend for Task/Follow-Up Management

## Priority: HIGH
## Effort: 3 hours
## Backend API: `/api/tasks` (7 endpoints, ready)

## What to Build

Agents need a UI to create, view, and manage follow-up tasks linked to contacts and properties.

## Backend Endpoints to Consume

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/tasks` | Create task |
| GET | `/api/tasks/{id}` | Get task detail |
| PUT | `/api/tasks/{id}` | Update task |
| DELETE | `/api/tasks/{id}` | Delete task |
| GET | `/api/tasks?status=OPEN&assigneeId=...&page=0&size=20` | List tasks (paginated) |
| GET | `/api/tasks/by-contact/{contactId}` | Tasks for a contact |
| GET | `/api/tasks/by-property/{propertyId}` | Tasks for a property |

Check the actual DTOs by reading:
```bash
cat hlm-backend/src/main/java/com/yem/hlm/backend/task/api/dto/CreateTaskRequest.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/task/api/dto/TaskResponse.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/task/api/dto/UpdateTaskRequest.java
cat hlm-backend/src/main/java/com/yem/hlm/backend/task/domain/TaskStatus.java
```

## Files to Create

```
hlm-frontend/src/app/features/tasks/
├── task.model.ts              # TypeScript interfaces
├── task.service.ts            # HttpClient API calls
├── tasks.component.ts         # Task list (paginated, filterable by status/assignee)
├── tasks.component.html       # Template
├── tasks.component.css        # Styles
├── task-form.component.ts     # Create/edit task dialog or inline form
├── task-form.component.html
└── task-form.component.css
```

## Model

```typescript
export interface Task {
  id: string;
  assigneeId: string;
  contactId?: string;
  propertyId?: string;
  title: string;
  description?: string;
  dueDate?: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELED';
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  dueDate?: string;
  contactId?: string;
  propertyId?: string;
  assigneeId?: string;
}
```

## Route Registration

Add to `app.routes.ts` inside the `app` children:

```typescript
{ path: 'tasks', loadComponent: () => import('./features/tasks/tasks.component').then(m => m.TasksComponent) },
```

## Shell Navigation

Add "Tâches" link to `shell.component.ts` navigation, visible to all CRM roles (ADMIN, MANAGER, AGENT).

## Integration Points

- On `contact-detail.component.ts`: add a "Tâches" tab/section showing tasks linked to that contact (call `/api/tasks/by-contact/{id}`)
- On `property-detail.component.ts`: add a "Tâches" section showing tasks linked to that property (call `/api/tasks/by-property/{id}`)
- Quick-create button on both detail pages to create a task pre-filled with contactId or propertyId

## UI Design

Follow existing patterns from `contacts.component.ts`:
- Table with columns: title, assignee, due date, status, actions
- Status badges (color-coded: OPEN=blue, IN_PROGRESS=amber, DONE=green, CANCELED=gray)
- Filter bar: status dropdown, assignee search
- Create button → form modal or separate route
- Overdue tasks highlighted (dueDate < now && status != DONE/CANCELED)

## Acceptance Criteria

- [ ] Task list page with pagination and status filter
- [ ] Create/edit task form with title, description, due date, contact/property link
- [ ] Status transitions work (OPEN → IN_PROGRESS → DONE)
- [ ] Tasks appear on contact detail and property detail pages
- [ ] Route `/app/tasks` accessible to all CRM roles
- [ ] Frontend builds successfully
