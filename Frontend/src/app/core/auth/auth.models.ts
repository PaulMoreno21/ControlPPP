export interface LoginCredentials {
  email: string;
  password: string;
}

export interface AuthenticatedUser {
  id: number;
  name: string;
  email: string;
  roles: string[];
  accountType?: 'INSTITUTIONAL' | 'LOCAL';
  passwordChangeRequired?: boolean;
}

export interface LoginResponse {
  expiresAt: string;
  idleExpiresAt: string;
  user: AuthenticatedUser;
}

export interface AuthSession {
  expiresAt: number;
  idleExpiresAt: number;
  user: AuthenticatedUser;
}
