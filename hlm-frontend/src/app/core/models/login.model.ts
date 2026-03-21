export interface LoginRequest {
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
  societeId: string;
  role?: string;
}
