package ec.edu.espe.practicas.dto;

import ec.edu.espe.practicas.model.RoleName;
import ec.edu.espe.practicas.model.AccountType;
import ec.edu.espe.practicas.model.User;
import java.util.List;

public record ManagedUserResponse(
        Long id,
        String nationalId,
        String firstName,
        String lastName,
        String email,
        Integer active,
        AccountType accountType,
        List<RoleName> roles) {

    public static ManagedUserResponse from(User user, List<RoleName> roles) {
        return new ManagedUserResponse(
                user.getId(), user.getNationalId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getActive(), user.getAccountType(), roles.stream().distinct().sorted().toList());
    }
}
