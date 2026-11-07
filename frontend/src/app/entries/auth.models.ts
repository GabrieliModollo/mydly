/** Espelha o login da API (POST /api/v1/auth/login, ADR 0012). */
export interface SessionUser {
  id: string;
  username: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Até quando o token vale (ISO 8601). */
  expiresAt: string;
  user: SessionUser;
}

/** Credenciais públicas do usuário de demonstração, criado pela migração do backend. */
export const DEMO_CREDENTIALS = { username: 'demo', password: 'demo' } as const;
