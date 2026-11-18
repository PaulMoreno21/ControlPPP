import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  const mustChangePassword = auth.session()?.user.passwordChangeRequired === true;
  if (mustChangePassword && !state.url.startsWith('/cuenta/seguridad')) {
    return router.createUrlTree(['/cuenta/seguridad']);
  }
  return true;
};
