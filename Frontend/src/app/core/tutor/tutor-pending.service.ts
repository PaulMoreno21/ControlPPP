import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { EMPTY, Subject, catchError, debounceTime, filter, fromEvent, merge, of, switchMap, timeout, timer } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { TutorService } from './tutor.service';
import { TutorPendingCounts } from './tutor.models';

@Injectable({ providedIn: 'root' })
export class TutorPendingService {
  private readonly auth = inject(AuthService);
  private readonly tutor = inject(TutorService);
  private readonly refreshRequests = new Subject<void>();
  private readonly state = signal<{ tutorId: number; counts: TutorPendingCounts } | null>(null);
  private readonly tutorId = computed(() => {
    const session = this.auth.session();
    return session?.user.roles.includes('TUTOR_ESPE') && !session.user.passwordChangeRequired
      ? session.user.id : null;
  });
  private readonly currentCounts = computed(() => {
    const state = this.state();
    return state?.tutorId === this.tutorId() ? state.counts : null;
  });
  readonly total = computed(() => this.currentCounts()?.total ?? null);
  private readonly byTrainee = computed(() => {
    const state = this.currentCounts();
    return state === null ? null : new Map(state.trainees.map(item => [item.traineeId, item.pendingActivitiesCount]));
  });

  constructor() {
    effect((onCleanup) => {
      const tutorId = this.tutorId();
      this.state.set(null);
      if (tutorId === null) return;
      const windowEvents = typeof window === 'undefined' ? EMPTY : merge(
        fromEvent(window, 'focus'), fromEvent(document, 'visibilitychange'),
      );
      const subscription = merge(timer(0, 30_000), windowEvents, this.refreshRequests, this.tutor.reviewCompleted)
        .pipe(
          debounceTime(100),
          filter(() => typeof document === 'undefined' || !document.hidden),
          switchMap(() => this.tutor.pendingCounts().pipe(
            timeout({ first: 12000 }), catchError(() => of(null)),
          )),
        )
        .subscribe(counts => this.state.set(counts === null ? null : { tutorId, counts }));
      onCleanup(() => subscription.unsubscribe());
    });
  }

  countFor(traineeId: number): number | null {
    const counts = this.byTrainee();
    return counts === null ? null : counts.get(traineeId) ?? 0;
  }

  refresh(): void {
    this.refreshRequests.next();
  }
}
