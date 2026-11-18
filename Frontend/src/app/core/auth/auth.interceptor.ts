import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const url = new URL(request.url, window.location.origin);
  if (url.origin !== window.location.origin || !url.pathname.startsWith('/api/')) return next(request);
  const auth = inject(AuthService);
  // The browser sends the HttpOnly cookie; Angular's XSRF interceptor handles CSRF.
  return next(request).pipe(catchError((error: unknown) => {
    if (error instanceof HttpErrorResponse && error.status === 401
        && !url.pathname.endsWith('/auth/login') && !url.pathname.endsWith('/auth/session')) auth.clearLocalSession();
    return throwError(() => error);
  }));
};
