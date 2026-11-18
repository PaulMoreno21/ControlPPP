import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TutorPendingService } from '../../core/tutor/tutor-pending.service';

const timeFormatter = new Intl.DateTimeFormat('es-EC', {
  hour: 'numeric',
  minute: '2-digit',
  timeZone: 'America/Guayaquil',
});
const dateFormatter = new Intl.DateTimeFormat('es-EC', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'America/Guayaquil',
});

@Component({
  selector: 'app-authenticated-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './authenticated-layout.html',
  styleUrl: './authenticated-layout.scss',
})
export class AuthenticatedLayout implements OnInit {
  readonly auth = inject(AuthService);
  readonly tutorPending = inject(TutorPendingService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly now = signal(new Date());
  readonly currentTime = computed(() => timeFormatter.format(this.now()));
  readonly currentDate = computed(() => dateFormatter.format(this.now()));

  ngOnInit(): void {
    timer(0, 1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.now.set(new Date()));
  }

  hasRole(roles: string[], role: string): boolean {
    return roles.includes(role);
  }

  hasAnyRole(roles: string[], allowed: string[]): boolean {
    return allowed.some((role) => roles.includes(role));
  }

  initials(name: string): string {
    return name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toLocaleUpperCase('es-EC') ?? '')
      .join('');
  }
}
