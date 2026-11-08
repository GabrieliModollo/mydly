/** Espelha o login e o cadastro da API (POST /api/v1/auth/login e /register, ADRs 0012 e 0018). */
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
