package ec.edu.espe.practicas.bootstrap;

import ec.edu.espe.practicas.dto.CreateManagedUserRequest;
import ec.edu.espe.practicas.model.RoleName;
import jakarta.validation.Validation;
import java.io.Console;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit terminal-only bootstrap. Does not start Spring, HTTP or scheduled tasks. */
public final class FirstAdministratorCommand {
    private FirstAdministratorCommand() { }

    public static int run(String[] args) {
        Console console = System.console();
        if (args.length != 1 || console == null) {
            System.err.println("Ejecuta java -jar <archivo.jar> --crear-admin-general desde una terminal interactiva, sin argumentos adicionales ni redirecciones.");
            return 2;
        }
        char[] password = null;
        char[] confirmation = null;
        try {
            console.printf("Crear el primer Administrador General%n");
            String identification = required(console.readLine("Identificación: "));
            String firstName = required(console.readLine("Nombres: "));
            String lastName = required(console.readLine("Apellidos: "));
            String email = required(console.readLine("Correo: ")).toLowerCase(Locale.ROOT);
            password = console.readPassword("Contraseña (mínimo 12 caracteres; no se muestra): ");
            confirmation = console.readPassword("Confirma la contraseña: ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Las contraseñas no coinciden o se canceló el ingreso.");
            }
            var input = new CreateManagedUserRequest(identification, firstName, lastName, email,
                    RoleName.ADMIN_GENERAL, true, null, List.of(RoleName.ADMIN_GENERAL));
            validate(input, password);
            String hash = new BCryptPasswordEncoder().encode(CharBuffer.wrap(password));
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');

            Properties local = new Properties();
            Path envFile = Path.of(".env");
            if (Files.exists(envFile)) {
                try (Reader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) { local.load(reader); }
            }
            String url = setting("ORACLE_JDBC_URL", local);
            String username = setting("ORACLE_USERNAME", local);
            String databasePassword = setting("ORACLE_PASSWORD", local);
            DriverManager.setLoginTimeout(20);
            try (Connection connection = DriverManager.getConnection(url, username, databasePassword)) {
                create(connection, input, hash);
            }
            console.printf("Administrador General creado. Ya puedes iniciar sesión con tu correo y contraseña.%n");
            return 0;
        } catch (IllegalArgumentException invalidInput) {
            console.printf("%s%n", invalidInput.getMessage());
            return 2;
        } catch (Exception failure) {
            console.printf("No se pudo crear el administrador. Revisa la conexión, el esquema instalado y sus permisos. No se confirmó la creación.%n");
            return 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    static void validate(CreateManagedUserRequest input, char[] password) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var errors = factory.getValidator().validate(input);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(errors.stream().map(error -> error.getMessage())
                        .sorted().findFirst().orElse("Revisa los datos ingresados."));
            }
        }
        String value = new String(password);
        if (value.isBlank() || value.length() < 12) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres.");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña es demasiado larga. Usa una más corta.");
        }
    }

    static void create(Connection connection, CreateManagedUserRequest input, String hash) throws SQLException {
        connection.setAutoCommit(false);
        try {
            // Serializes competing bootstrap commands and the existing global-administrator policy.
            long roleId = scalar(connection,
                    "SELECT TCWROLES_ID FROM TCWROLES WHERE TCWROLES_NOMBRE = 'ADMIN_GENERAL' FOR UPDATE WAIT 15");
            long existing = scalar(connection, "SELECT COUNT(*) FROM TCWUSUARIOSROLES "
                    + "WHERE TCWROLES_ID = ? AND TCWLABORATORIOS_ID IS NULL", roleId);
            if (existing > 0) throw new IllegalArgumentException(
                    "Ya existe un Administrador General. Gestiona las cuentas desde Usuarios.");
            if (scalar(connection, "SELECT COUNT(*) FROM TCWUSUARIOS WHERE TCWUSUARIOS_CEDULA = ? "
                    + "OR LOWER(TCWUSUARIOS_CORREO) = LOWER(?)", input.nationalId(), input.email()) > 0) {
                throw new IllegalArgumentException("Ya existe una cuenta con esa identificación o correo.");
            }
            long userId = scalar(connection, "SELECT SEQ_CWUSUARIOS.NEXTVAL FROM DUAL");
            execute(connection, "INSERT INTO TCWUSUARIOS (TCWUSUARIOS_ID, TCWUSUARIOS_CEDULA, "
                    + "TCWUSUARIOS_NOMBRE, TCWUSUARIOS_APELLIDO, TCWUSUARIOS_CORREO, TCWUSUARIOS_CLAVE_HASH, "
                    + "TCWUSUARIOS_ACTIVO, TCWUSUARIOS_TIPO_CUENTA, TCWUSUARIOS_CAMBIAR_CLAVE) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 1, 'LOCAL', 0)", userId, input.nationalId(),
                    input.firstName(), input.lastName(), input.email(), hash);
            execute(connection, "INSERT INTO TCWUSUARIOSROLES (TCWUSUARIOSROLES_ID, TCWUSUARIOS_ID, "
                    + "TCWROLES_ID, TCWLABORATORIOS_ID) VALUES (SEQ_CWUSUARIOSROLES.NEXTVAL, ?, ?, NULL)", userId, roleId);
            execute(connection, "INSERT INTO TCWAUDITORIAS (TCWAUDITORIAS_ID, TCWUSUARIOS_ID, "
                    + "TCWAUDITORIAS_ACCION, TCWAUDITORIAS_ENTIDAD, TCWAUDITORIAS_ENTIDAD_ID, "
                    + "TCWAUDITORIAS_FECHA, TCWAUDITORIAS_DETALLE) "
                    + "VALUES (SEQ_CWAUDITORIAS.NEXTVAL, ?, 'CREAR_USUARIO', 'TCWUSUARIOS', ?, LOCALTIMESTAMP, ?)",
                    userId, userId, "Se creó el primer Administrador General " + input.firstName() + " "
                            + input.lastName() + " desde la consola de instalación.");
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
    }

    private static long scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException(
                        "No se encontró el rol inicial. Instala primero CTRLPRAC_CREAR.sql en un esquema vacío.");
                return result.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            if (statement.executeUpdate() != 1) throw new SQLException("No se completó la operación.");
        }
    }

    private static String required(String value) {
        if (value == null) throw new IllegalArgumentException("Se canceló el ingreso de datos.");
        return value.strip();
    }

    private static String setting(String name, Properties local) {
        String value = System.getenv(name);
        if (value == null) value = local.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta configurar " + name + ".");
        return value;
    }
}
