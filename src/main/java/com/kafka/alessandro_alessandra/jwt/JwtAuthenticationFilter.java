package com.kafka.alessandro_alessandra.jwt;

import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final PublicKey publicKey;

    public JwtAuthenticationFilter() {
        log.info("Initializing JwtAuthenticationFilter...");
        this.publicKey = loadPublicKey();
        log.info("JwtAuthenticationFilter initialized successfully.");
    }

    /**
     * Filters incoming HTTP requests to authenticate JWT tokens.
     *
     * Extracts the JWT from the Authorization header, validates it,
     * and sets the authentication in the SecurityContext if valid.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (jwt != null && validateToken(jwt)) {
                String username = getUsernameFromToken(jwt);

                List<GrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_USER")
                );
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                    );
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(
                    authentication
                );
                log.info(
                    "JWT validated and user '{}' authenticated.",
                    username
                );
            } else if (jwt == null) {
                log.info("No JWT token found in Authorization header.");
            } else {
                log.info("JWT token found but not valid.");
            }
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            log.warn("Cannot set user authentication: {}", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response
                .getWriter()
                .write("{\"error\": \"Invalid or expired JWT token\"}");
        }
    }

    /**
     * Extracts the JWT token from the Authorization header of the HTTP request.
     *
     * @param request the HTTP servlet request
     * @return the JWT token if present and prefixed with "Bearer", otherwise null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Extracts the username (subject) from the provided JWT token.
     *
     * @param token the JWT token
     * @return the username (subject) contained in the token
     * @throws UnsupportedJwtException if the token algorithm is not RS256
     */
    public String getUsernameFromToken(String token) {
        Jws<Claims> jws = Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token);

        if (!"RS256".equals(jws.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException(
                "Only RS256 tokens are supported."
            );
        }

        return jws.getPayload().getSubject();
    }

    /**
     * Validates the provided JWT token.
     * <p>
     * Parses the token using the public key, checks if the algorithm is RS256,
     * and handles possible exceptions such as malformed, expired, or unsupported tokens.
     *
     * @param authToken the JWT token to validate
     * @return true if the token is valid and uses RS256, false otherwise
     */
    public boolean validateToken(String authToken) {
        try {
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(authToken);

            if (!"RS256".equals(jws.getHeader().getAlgorithm())) {
                log.warn(
                    "Invalid JWT algorithm: {}. Only RS256 is supported.",
                    jws.getHeader().getAlgorithm()
                );
                return false;
            }

            return true;
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Loads the RSA public key from the `public.pem` file located in the classpath.
     * <p>
     * Reads the PEM file, removes header and footer, decodes the Base64 content,
     * and generates a `PublicKey` instance using the RSA algorithm.
     *
     * @return the loaded RSA public key
     * @throws RuntimeException if the public key cannot be loaded or parsed
     */
    private PublicKey loadPublicKey() {
        try (
            InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("public.pem");
        ) {
            if (is == null) {
                log.error("public.pem file not found in classpath");
                throw new RuntimeException(
                    "public.pem file not found in classpath"
                );
            }
            String publicKeyPEM = new String(is.readAllBytes())
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey key = keyFactory.generatePublic(keySpec);
            log.info("Public key loaded successfully from public.pem");
            return key;
        } catch (Exception e) {
            log.error("Failed to load public key from PEM file", e);
            throw new RuntimeException(
                "Failed to load public key from PEM file",
                e
            );
        }
    }
}
