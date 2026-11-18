package ec.edu.espe.practicas.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClientIpResolver {
    private final Set<String> trustedProxies;

    public ClientIpResolver(List<String> trustedProxyIps) {
        trustedProxies = trustedProxyIps.stream().filter(ip -> !ip.isBlank()).map(ip -> {
            String normalized = numericAddress(ip.trim());
            if (normalized == null) throw new IllegalArgumentException("La IP del proxy confiable no es válida.");
            return normalized;
        }).collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String peer = numericAddress(request.getRemoteAddr());
        if (peer == null) return "unknown";
        if (trustedProxies.contains(peer)) {
            String client = numericAddress(request.getHeader("X-Real-IP"));
            if (client != null) return client;
        }
        return peer;
    }

    private static String numericAddress(String address) {
        if (address == null || address.length() > 45) return null;
        // Only numeric literals reach InetAddress: never resolve a client-supplied hostname.
        if (!(address.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")
                || (address.contains(":") && address.matches("[0-9a-fA-F:.]+")))) return null;
        try {
            return InetAddress.getByName(address).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
