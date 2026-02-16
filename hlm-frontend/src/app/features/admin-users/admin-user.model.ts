export interface AdminUser {
  id: string;
  email: string;
  role: string;
  enabled: boolean;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  role: string;
}

export interface ResetPasswordResponse {
  temporaryPassword: string;
}
