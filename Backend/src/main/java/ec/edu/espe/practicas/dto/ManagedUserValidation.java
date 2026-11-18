package ec.edu.espe.practicas.dto;

/** Input rules shared by user creation and editing. */
public final class ManagedUserValidation {
    public static final String IDENTIFICATION = "[A-Za-z0-9]{1,10}";
    public static final String NAME = "[\\p{L}][\\p{L}\\p{M}]*(?:(?: +|['’.\\-])[\\p{L}][\\p{L}\\p{M}]*)*\\.?";
    public static final String EMAIL = "[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+";

    private ManagedUserValidation() { }

    public static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
