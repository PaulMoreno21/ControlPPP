export interface LaboratorySummary {
  id: number;
  name: string;
  location: string | null;
}

export interface CreateLaboratoryRequest {
  name: string;
  location: string | null;
}

export type ManagedRole = 'PRACTICANTE' | 'TUTOR_ESPE' | 'ADMIN_LABORATORIO' | 'ADMIN_GENERAL';

export interface ManagedUser {
  id: number;
  nationalId: string;
  firstName: string;
  lastName: string;
  email: string;
  active: number;
  accountType: 'INSTITUTIONAL' | 'LOCAL';
  roles: ManagedRole[];
}

export interface CreateManagedUserRequest {
  nationalId: string;
  firstName: string;
  lastName: string;
  email: string;
  roles: ManagedRole[];
  localAccount: boolean;
  password?: string;
}

export type UpdateManagedUserRequest = Pick<
  CreateManagedUserRequest,
  'nationalId' | 'firstName' | 'lastName' | 'email' | 'roles'
>;
