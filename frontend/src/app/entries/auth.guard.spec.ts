import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { AuthSession } from './session';

describe('guardas de rota', () => {
  let loggedIn: boolean;

  beforeEach(() => {
    loggedIn = false;
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthSession, useValue: { isLoggedIn: () => loggedIn } }],
    });
  });

  function run(guard: typeof authGuard): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as boolean | UrlTree,
    );
  }

  it('sem sessão, as telas do app levam ao login', () => {
    expect(String(run(authGuard))).toBe('/login');
    expect(run(guestGuard)).toBe(true);
  });

  it('com sessão, o login leva ao Hoje', () => {
    loggedIn = true;

    expect(run(authGuard)).toBe(true);
    expect(String(run(guestGuard))).toBe('/');
  });
});
