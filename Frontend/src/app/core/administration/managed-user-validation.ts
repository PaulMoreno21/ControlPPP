import { ValidatorFn, Validators } from '@angular/forms';

export const USER_IDENTIFICATION = /^[A-Za-z0-9]{1,10}$/;
export const USER_NAME = /^[\p{L}][\p{L}\p{M}]*(?:(?: +|['’.\-])[\p{L}][\p{L}\p{M}]*)*\.?$/u;
export const USER_EMAIL = /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/;

// Validate the value that will actually be sent, without changing passwords.
export function trimmed(validator: ValidatorFn): ValidatorFn {
  return (control) => validator({ value: String(control.value ?? '').trim() } as typeof control);
}

export const userIdentityValidators = {
  nationalId: [trimmed(Validators.required), trimmed(Validators.pattern(USER_IDENTIFICATION))],
  firstName: [trimmed(Validators.required), trimmed(Validators.maxLength(100)), trimmed(Validators.pattern(USER_NAME))],
  lastName: [trimmed(Validators.required), trimmed(Validators.maxLength(100)), trimmed(Validators.pattern(USER_NAME))],
  email: [trimmed(Validators.required), trimmed(Validators.email), trimmed(Validators.maxLength(150)), trimmed(Validators.pattern(USER_EMAIL))],
};
