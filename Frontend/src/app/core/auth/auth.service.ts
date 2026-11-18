import { computed, Injectable, OnDestroy, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, map, of, switchMap, tap, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthSession, LoginCredentials, LoginResponse } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/auth`;
  private readonly sessionState = signal<AuthSession | null>(null);
  private expirationTimer: ReturnType<typeof setTimeout> | null = null;
  private sessionRevision = 0;
  private readonly activityEvents = ['pointerdown', 'pointermove', 'keydown', 'input', 'wheel', 'touchstart'];
  private readonly activityTimer: ReturnType<typeof setInterval>;
  private pendingActivity = false;
  private lastActivityAt = 0;
  private lastActivityRequestAt = 0;
  private activityInFlight = false;
  private readonly onUserActivity = (event: Event) => {
    if (!event.isTrusted || document.hidden || !this.sessionState()) return;
    this.lastActivityAt = Date.now();
    this.pendingActivity = true;
    this.flushActivity();
  };
  private readonly onFocus = () => {
    if (this.sessionState()) this.refreshSession().subscribe();
  };
  private readonly channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('control-ppp-auth');
  readonly session = this.sessionState.asReadonly();
  readonly isAuthenticated = computed(() => this.sessionState() !== null);
  readonly closingSession = signal(false);
  readonly logoutError = signal('');
  readonly sessionNotice = signal('');

  constructor(private readonly http: HttpClient, private readonly router: Router) {
    // Remove credentials left by previous versions. Never persist new credentials.
    sessionStorage.removeItem('control-ppp-session');
    if (this.channel) this.channel.onmessage = event => {
      if (event.data === 'logout') this.clearLocalSession();
      if (event.data === 'changed') {
        this.clearLocalSession(false);
        this.refreshSession().subscribe(() => {
          if (this.sessionState()) void this.router.navigateByUrl('/inicio');
        });
      }
      if (event.data === 'activity' && this.sessionState()) this.refreshSession().subscribe();
    };
    window.addEventListener('focus', this.onFocus);
    this.activityEvents.forEach(name => window.addEventListener(name, this.onUserActivity, { passive: true }));
    this.activityTimer = setInterval(() => this.flushActivity(), 30000);
  }

  initialize(): Observable<void> {
    return this.http.get<void>(`${this.baseUrl}/csrf`).pipe(
      switchMap(() => this.refreshSession(false)), timeout({ first: 10000 }), catchError(() => of(undefined)),
    );
  }

  login(credentials: LoginCredentials): Observable<LoginResponse> {
    const revision = ++this.sessionRevision;
    this.activityInFlight = false;
    this.pendingActivity = false;
    return this.http.get<void>(`${this.baseUrl}/csrf`).pipe(
      switchMap(() => this.http.post<LoginResponse>(`${this.baseUrl}/login`, credentials)),
      timeout({ first: 15000 }),
      tap(response => {
        if (revision !== this.sessionRevision) return;
        this.lastActivityRequestAt = 0;
        this.acceptSession(response);
        this.channel?.postMessage('changed');
      }),
    );
  }

  refreshSession(redirect = true): Observable<void> {
    const revision = this.sessionRevision;
    return this.http.get<LoginResponse>(`${this.baseUrl}/session`).pipe(
      timeout({ first: 10000 }),
      tap(response => { if (revision === this.sessionRevision) this.acceptSession(response); }),
      map(() => undefined),
      catchError(() => {
        if (revision === this.sessionRevision) this.clearLocalSession(redirect);
        return of(undefined);
      }),
    );
  }

  markPasswordChanged(): void {
    this.sessionNotice.set('Tu contraseña se actualizó. Inicia sesión con la nueva contraseña.');
    this.channel?.postMessage('logout');
    this.clearLocalSession();
  }

  logout(): void {
    if (this.closingSession()) return;
    this.closingSession.set(true);
    this.logoutError.set('');
    this.http.get<void>(`${this.baseUrl}/csrf`).pipe(
      switchMap(() => this.http.post<void>(`${this.baseUrl}/logout`, {})),
      timeout({ first: 10000 }), finalize(() => this.closingSession.set(false)),
    ).subscribe({
      next: () => { this.channel?.postMessage('logout'); this.clearLocalSession(); },
      error: () => this.logoutError.set('No se pudo cerrar la sesión. Revisa la conexión e inténtalo nuevamente.'),
    });
  }

  clearLocalSession(redirect = true): void {
    this.sessionRevision++;
    if (this.expirationTimer !== null) clearTimeout(this.expirationTimer);
    this.expirationTimer = null;
    this.pendingActivity = false;
    this.activityInFlight = false;
    this.lastActivityAt = 0;
    this.lastActivityRequestAt = 0;
    this.sessionState.set(null);
    if (redirect && this.router.url !== '/login') void this.router.navigateByUrl('/login', { replaceUrl: true });
  }

  private acceptSession(response: LoginResponse): void {
    const expiresAt = Date.parse(response.expiresAt);
    let idleExpiresAt = Date.parse(response.idleExpiresAt);
    const current = this.sessionState();
    // A delayed GET must not replace a newer activity acknowledgement.
    if (current?.user.id === response.user.id && Math.abs(current.expiresAt - expiresAt) < 1000) {
      idleExpiresAt = Math.max(idleExpiresAt, current.idleExpiresAt);
    }
    idleExpiresAt = Math.min(idleExpiresAt, expiresAt);
    if (!Number.isFinite(expiresAt) || !Number.isFinite(idleExpiresAt) || idleExpiresAt <= Date.now()) {
      this.clearLocalSession(); return;
    }
    this.sessionState.set({ user: response.user, expiresAt, idleExpiresAt });
    if (this.expirationTimer !== null) clearTimeout(this.expirationTimer);
    this.expirationTimer = setTimeout(() => {
      if (Date.now() >= expiresAt) this.clearLocalSession();
      else this.refreshSession().subscribe(); // Another tab may have recorded activity.
    }, Math.max(0, idleExpiresAt - Date.now()));
  }

  private flushActivity(): void {
    const current = this.sessionState();
    const now = Date.now();
    if (!current || !this.pendingActivity || document.hidden || this.activityInFlight
        || now - this.lastActivityAt > 60000 || now >= current.expiresAt) return;
    if (now - this.lastActivityRequestAt < 30000 && current.idleExpiresAt - now > 60000) return;
    const revision = this.sessionRevision;
    this.pendingActivity = false;
    this.lastActivityRequestAt = now;
    this.activityInFlight = true;
    this.http.get<void>(`${this.baseUrl}/csrf`).pipe(
      switchMap(() => revision === this.sessionRevision
        ? this.http.post<{ expiresAt: string; idleExpiresAt: string }>(`${this.baseUrl}/activity`, {}) : EMPTY),
      timeout({ first: 10000 }),
      finalize(() => { if (revision === this.sessionRevision) this.activityInFlight = false; }),
    ).subscribe({
      next: response => {
        const active = this.sessionState();
        if (revision !== this.sessionRevision || !active) return;
        this.acceptSession({ ...response, user: active.user });
        this.channel?.postMessage('activity');
      },
      error: error => {
        if (revision !== this.sessionRevision) return;
        if (error.status === 401) this.clearLocalSession();
        else this.pendingActivity = true;
      },
    });
  }

  ngOnDestroy(): void {
    clearInterval(this.activityTimer);
    if (this.expirationTimer !== null) clearTimeout(this.expirationTimer);
    window.removeEventListener('focus', this.onFocus);
    this.activityEvents.forEach(name => window.removeEventListener(name, this.onUserActivity));
    this.channel?.close();
  }
}
