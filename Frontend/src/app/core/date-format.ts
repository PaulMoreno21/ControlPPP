import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function toDisplayDate(isoDate: string): string {
  const [year, month, day] = isoDate.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

export function toIsoDate(displayDate: string): string | null {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(displayDate.trim());
  if (!match) return null;

  const [, day, month, year] = match;
  const date = new Date(Number(year), Number(month) - 1, Number(day));
  if (date.getFullYear() !== Number(year) || date.getMonth() !== Number(month) - 1 || date.getDate() !== Number(day)) {
    return null;
  }

  return `${year}-${month}-${day}`;
}

export const ddMmYyyyValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  return !value || toIsoDate(value) !== null ? null : { dateFormat: true };
};

export function notAfterDateValidator(maxIsoDate: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const isoDate = toIsoDate(String(control.value ?? ''));
    return isoDate && isoDate > maxIsoDate ? { maxDate: true } : null;
  };
}

