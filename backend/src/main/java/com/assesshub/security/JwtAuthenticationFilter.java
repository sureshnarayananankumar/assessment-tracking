package com.assesshub.security;

import com.assesshub.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests carrying "Authorization: Bearer &lt;jwt&gt;". The user is re-loaded from the
 * database so disabled / locked accounts and role changes are enforced immediately.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            try {
                Long userId = jwtService.validateAndGetUserId(token);
                userRepository.findById(userId)
                        .map(UserPrincipal::from)
                        .filter(p -> p.isEnabled() && p.isAccountNonLocked())
                        .ifPresent(principal -> {
                            var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected bearer token: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
