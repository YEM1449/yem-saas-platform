export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELED';

export interface Task {
  id: string;
  societeId: string;
  assigneeId: string;
  contactId?: string;
  propertyId?: string;
  title: string;
  description?: string;
  dueDate?: string;
  status: TaskStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  dueDate?: string;
  assigneeId?: string;
  contactId?: string;
  propertyId?: string;
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  dueDate?: string;
  assigneeId?: string;
  status?: TaskStatus;
}

export interface TaskPage {
  content: Task[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
