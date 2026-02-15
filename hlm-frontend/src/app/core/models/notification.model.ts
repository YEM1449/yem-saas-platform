export interface Notification {
  id: string;
  type: string;
  refId: string | null;
  payload: string | null;
  read: boolean;
  createdAt: string;
}
