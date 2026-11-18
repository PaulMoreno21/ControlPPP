package ec.edu.espe.practicas.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ec.edu.espe.practicas.dto.CreateManagedUserRequest;
import ec.edu.espe.practicas.model.RoleName;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class FirstAdministratorCommandTest {
    private final CreateManagedUserRequest input = new CreateManagedUserRequest("AB12345678", "Ana", "Pérez",
            "ana@example.com", RoleName.ADMIN_GENERAL, true, null, List.of(RoleName.ADMIN_GENERAL));

    private Connection connection(long administrators, long duplicates, boolean failRoleInsert) throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet result = mock(ResultSet.class);
            when(statement.executeQuery()).thenReturn(result);
            when(result.next()).thenReturn(true);
            long value = sql.contains("COUNT(*) FROM TCWUSUARIOSROLES") ? administrators
                    : sql.contains("COUNT(*) FROM TCWUSUARIOS WHERE") ? duplicates : 1;
            when(result.getLong(1)).thenReturn(value);
            if (failRoleInsert && sql.startsWith("INSERT INTO TCWUSUARIOSROLES")) {
                when(statement.executeUpdate()).thenThrow(new SQLException("simulated failure"));
            } else { when(statement.executeUpdate()).thenReturn(1); }
            return statement;
        });
        return connection;
    }

    @Test void createsAccountGlobalRoleAndAuditWithBoundHashAndCommit() throws Exception {
        Connection connection = connection(0, 0, false);
        String hash = new BCryptPasswordEncoder().encode("ClaveDePruebaLarga2026!");
        FirstAdministratorCommand.create(connection, input, hash);
        verify(connection).commit();
        verify(connection, never()).rollback();
        var statements = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection, times(7)).prepareStatement(statements.capture());
        var sql = statements.getAllValues();
        assertTrue(sql.getFirst().contains("FOR UPDATE WAIT 15"));
        assertTrue(sql.stream().anyMatch(value -> value.contains("1, 'LOCAL', 0")));
        assertTrue(sql.stream().anyMatch(value -> value.contains("SEQ_CWUSUARIOSROLES.NEXTVAL, ?, ?, NULL")));
        assertTrue(sql.stream().noneMatch(value -> value.contains(hash) || value.contains(input.email())));
    }

    @Test void existingInactiveOrActiveAdministratorPreventsInserts() throws Exception {
        Connection connection = connection(1, 0, false);
        assertThrows(IllegalArgumentException.class, () -> FirstAdministratorCommand.create(connection, input, "hash"));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
    }

    @Test void duplicateIdentityPreventsInserts() throws Exception {
        Connection connection = connection(0, 1, false);
        assertThrows(IllegalArgumentException.class, () -> FirstAdministratorCommand.create(connection, input, "hash"));
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
    }

    @Test void roleFailureRollsBackAccountAndDoesNotCommit() throws Exception {
        Connection connection = connection(0, 0, true);
        assertThrows(SQLException.class, () -> FirstAdministratorCommand.create(connection, input, "hash"));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @ValueSource(strings = {"corta", "            ", ""})
    void invalidPasswordIsRejected(String password) {
        assertThrows(IllegalArgumentException.class, () -> FirstAdministratorCommand.validate(input, password.toCharArray()));
    }

    @Test void longMultibytePasswordIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FirstAdministratorCommand.validate(input, "á".repeat(37).toCharArray()));
    }

    @Test void acceptsNormalPasswordAndPreservesWhitespace() {
        char[] password = "  ClaveLargaDePrueba!  ".toCharArray();
        assertDoesNotThrow(() -> FirstAdministratorCommand.validate(input, password));
        var encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(java.nio.CharBuffer.wrap(password));
        assertTrue(encoder.matches(java.nio.CharBuffer.wrap(password), hash));
    }

    @Test void sharedUserRulesRejectInvalidNames() {
        var invalid = new CreateManagedUserRequest("123", "<script>", "Pérez", "ana@example.com",
                RoleName.ADMIN_GENERAL, true, null, List.of(RoleName.ADMIN_GENERAL));
        assertThrows(IllegalArgumentException.class,
                () -> FirstAdministratorCommand.validate(invalid, "ClaveDePruebaLarga!".toCharArray()));
    }
}
