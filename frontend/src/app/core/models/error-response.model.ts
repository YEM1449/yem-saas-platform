export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  correlationId?: string;
  fieldErrors?: FieldError[];
}

export interface FieldError {
  field: string;
  message: string;
}
