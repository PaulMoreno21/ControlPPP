import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export function roleGuard(requiredRole: string | string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const roles = auth.session()?.user.roles ?? [];
    const allowedRoles = Array.isArray(requiredRole) ? requiredRole : [requiredRole];
    return allowedRoles.some((role) => roles.includes(role)) ? true : router.createUrlTree(['/inicio']);
  };
}
