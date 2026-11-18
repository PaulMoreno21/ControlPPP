import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
})
export class HomePage {
  readonly auth = inject(AuthService);

  hasRole(roles: string[], role: string): boolean { return roles.includes(role); }

  roleLabel(role: string): string {
    const labels: Record<string, string> = {
      PRACTICANTE: 'Practicante', TUTOR_ESPE: 'Tutor ESPE',
      ADMIN_LABORATORIO: 'Administrador de laboratorio', ADMIN_GENERAL: 'Administrador general',
    };
    return labels[role] ?? role;
  }

  firstName(name: string): string { return name.trim().split(/\s+/)[0] || name; }

  welcomeInstruction(roles: string[]): string {
    if (roles.includes('PRACTICANTE')) return 'Consulta tus actividades y registra las tareas realizadas después de cerrar tu jornada.';
    if (roles.includes('TUTOR_ESPE')) return 'Consulta la lista de practicantes asignados y revisa individualmente sus actividades pendientes.';
    if (roles.includes('ADMIN_GENERAL') || roles.includes('ADMIN_LABORATORIO')) return 'Selecciona una opción de administración para gestionar los recursos habilitados para tus roles.';
    return 'Tu acceso está activo. Las opciones disponibles aparecerán aquí según los roles de tu cuenta.';
  }
}
