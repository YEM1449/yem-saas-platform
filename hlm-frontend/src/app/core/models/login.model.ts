export interface LoginRequest {
  tenantKey: string;
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface MeResponse {
  userId: string;
  tenantId: string;
  role?: string;
}
