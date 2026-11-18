import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
})
export class LoginPage {
  readonly currentYear = new Date().getFullYear();
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly isSubmitting = signal(false);
  readonly showPassword = signal(false);
  readonly errorMessage = signal('');
  readonly infoMessage = signal(this.auth.sessionNotice());

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  showRecoveryInfo(kind: 'usuario' | 'contrasena' | 'ayuda'): void {
    const messages = {
      usuario: 'Para iniciar sesión con la cuenta local, usa el correo asociado a tu usuario.',
      contrasena: 'Contacta al administrador para restablecer tu contraseña local. Al ingresar con la contraseña temporal, el sistema te pedirá crear una nueva.',
      ayuda: 'Si no puedes acceder, solicita ayuda al administrador del sistema.',
    };
    this.infoMessage.set(messages[kind]);
  }

  submit(): void {
    this.auth.sessionNotice.set('');
    this.errorMessage.set('');
    this.infoMessage.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: (response) => void this.router.navigateByUrl(
          response.user.passwordChangeRequired ? '/cuenta/seguridad' : '/inicio'),
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(this.toFriendlyMessage(error)),
      });
  }

  private toFriendlyMessage(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'No se pudo conectar con el servidor. Inténtalo nuevamente.';
    }
    if (error.status === 401) {
      return 'El correo o la contraseña no son correctos.';
    }

    const detail = error.error?.detail;
    return typeof detail === 'string' && detail.trim()
      ? detail
      : 'No fue posible iniciar sesión. Inténtalo nuevamente.';
  }
}
