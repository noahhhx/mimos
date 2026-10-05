/** The parts of oidc-client-ts's `UserManager` a session needs (a fake in tests). */
export interface SessionManager<U extends SessionUser> {
  getUser(): Promise<U | null>;
  signinSilent(): Promise<U | null>;
  removeUser(): Promise<void>;
}

export interface SessionUser {
  access_token: string;
  /** Seconds until the access token expires; unknown when absent. */
  expires_in?: number;
}

/**
 * Renew a token this close to expiry before sending it, so it cannot
 * expire on its way to the API.
 */
export const MIN_TOKEN_VALIDITY_SECONDS = 30;

/**
 * The signed-in user with a usable access token, or null once the session
 * is over. A stored user whose token has expired (a tab left open past
 * Keycloak's session, a laptop asleep) is renewed with its refresh token;
 * if that fails, the user is removed, which signs the app out (the
 * provider hears `userUnloaded`) so pages ask to sign in again instead of
 * failing every request with a 401. Concurrent callers share one renewal.
 */
export function createSession<U extends SessionUser>(manager: SessionManager<U>): () => Promise<U | null> {
  let renewing: Promise<U | null> | null = null;

  const renew = async (): Promise<U | null> => {
    try {
      const renewed = await manager.signinSilent();
      if (renewed) {
        return renewed;
      }
    } catch {
      // The refresh token is expired or revoked: the session is over.
    }
    await manager.removeUser();
    return null;
  };

  return async () => {
    const user = await manager.getUser();
    if (!user) {
      return null;
    }
    if (user.expires_in === undefined || user.expires_in > MIN_TOKEN_VALIDITY_SECONDS) {
      return user;
    }
    renewing ??= renew().finally(() => {
      renewing = null;
    });
    return renewing;
  };
}
