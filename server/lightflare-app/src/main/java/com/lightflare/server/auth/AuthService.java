package com.lightflare.server.auth;

import com.lightflare.server.config.BootstrapProperties;
import com.lightflare.server.config.AuthProperties;
import com.lightflare.server.user.AppUser;
import com.lightflare.server.user.AppUserRepository;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.NoSuchElementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String REQUEST_USER_CONTEXT_ATTR = "currentUser";
    public static final String REQUEST_AUTH_SESSION_ATTR = "currentAuthSession";

    private static final String AUTH_COOKIE_NAME = "SL_AUTH";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final Duration AUTH_SESSION_TTL = Duration.ofDays(7);

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final BootstrapProperties bootstrapProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthUserResponse login(LoginRequest request, HttpServletResponse response) {
        if (request == null || !StringUtils.hasText(request.getLogin()) || !StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login and password are required");
        }

        AppUser user = resolveLoginUser(request);

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is inactive");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        authSessionRepository.deleteExpired(DateUtils.now());
        establishSession(user, response);
        return toAuthUserResponse(user);
    }

    public AuthUserResponse updatePassword(UpdatePasswordRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        if (request == null || !StringUtils.hasText(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        AppUser user = requireCurrentUser(httpRequest);
        OffsetDateTime now = DateUtils.now();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(Boolean.FALSE);
        user.setUpdatedAt(now);
        int updated = appUserRepository.updateUserPassword(
                user.getId(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                now
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one app_user row to be updated but got " + updated);
        }
        AppUser savedUser = findExistingUser(user.getId());
        revokeUserSessions(savedUser.getId());
        establishSession(savedUser, httpResponse);
        return toAuthUserResponse(savedUser);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String rawSessionToken = readAuthCookieValue(request);
        if (StringUtils.hasText(rawSessionToken)) {
            authSessionRepository.deleteBySessionTokenHash(hashToken(rawSessionToken));
        }
        clearAuthCookie(response);
        clearCsrfCookie(response);
    }

    public AuthUserResponse getCurrentUserResponse(HttpServletRequest request) {
        return toAuthUserResponse(requireCurrentUser(request));
    }

    public AppUser requireCurrentUser(HttpServletRequest request) {
        UserContext userContext = requireUserContext(request);
        return appUserRepository.findById(userContext.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public AppUser requireAdmin(HttpServletRequest request) {
        UserContext userContext = requireUserContext(request);
        if (!AppRoles.isAdminLike(userContext.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        }

        return appUserRepository.findById(userContext.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public AppUser requireSuperAdmin(HttpServletRequest request) {
        UserContext userContext = requireUserContext(request);
        if (!AppRoles.isSuperAdmin(userContext.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Superadmin access is required");
        }

        return appUserRepository.findById(userContext.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public Optional<UserContext> resolveUserContext(HttpServletRequest request) {
        AuthSession authSession = resolveAuthSession(request).orElse(null);
        if (authSession == null) {
            return Optional.empty();
        }

        AppUser user = appUserRepository.findById(authSession.getUserId()).orElse(null);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return Optional.empty();
        }

        UserContext userContext = toUserContext(user);
        request.setAttribute(REQUEST_USER_CONTEXT_ATTR, userContext);
        return Optional.of(userContext);
    }

    public Optional<AuthSession> resolveAuthSession(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        Object existing = request.getAttribute(REQUEST_AUTH_SESSION_ATTR);
        if (existing instanceof AuthSession authSession) {
            return Optional.of(authSession);
        }

        String rawSessionToken = readAuthCookieValue(request);
        if (!StringUtils.hasText(rawSessionToken)) {
            return Optional.empty();
        }

        AuthSession authSession = authSessionRepository.findActiveBySessionTokenHash(hashToken(rawSessionToken), OffsetDateTime.now())
                .orElse(null);
        if (authSession == null) {
            return Optional.empty();
        }
        request.setAttribute(REQUEST_AUTH_SESSION_ATTR, authSession);
        return Optional.of(authSession);
    }

    public UserContext requireUserContext(HttpServletRequest request) {
        return resolveUserContext(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public AppUser findExistingUser(String id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
    }

    public void revokeUserSessions(String userId) {
        authSessionRepository.deleteByUserId(userId);
    }

    private AppUser resolveLoginUser(LoginRequest request) {
        if (appUserRepository.countUsers() == 0) {
            return createBootstrapSuperAdminForMatchingLogin(request)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        }

        return appUserRepository.findByUsernameOrEmail(request.getLogin().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }

    private Optional<AppUser> createBootstrapSuperAdminForMatchingLogin(LoginRequest request) {
        String configuredUsername = bootstrapProperties.getSuperadmin().getUsername();
        String configuredEmail = bootstrapProperties.getSuperadmin().getEmail();
        String configuredPassword = bootstrapProperties.getSuperadmin().getPassword();
        if (!StringUtils.hasText(configuredUsername)
                || !StringUtils.hasText(configuredEmail)
                || !StringUtils.hasText(configuredPassword)) {
            return Optional.empty();
        }

        String login = request.getLogin().trim();
        if (!configuredUsername.trim().equalsIgnoreCase(login) || !configuredPassword.equals(request.getPassword())) {
            return Optional.empty();
        }

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(configuredUsername.trim().toLowerCase());
        user.setEmail(configuredEmail.trim().toLowerCase());
        user.setDisplayName("Super Administrator");
        user.setPasswordHash(passwordEncoder.encode(configuredPassword));
        user.setStatus("ACTIVE");
        user.setRole(AppRoles.SUPERADMIN);
        user.setMustChangePassword(Boolean.TRUE);
        user.setCreatedAt(DateUtils.now());
        user.setUpdatedAt(user.getCreatedAt());
        int inserted = appUserRepository.insertUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getRole(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one app_user row to be inserted but got " + inserted);
        }
        return appUserRepository.findById(user.getId());
    }

    private UserContext toUserContext(AppUser user) {
        return new UserContext(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getStatus()
        );
    }

    private String readAuthCookieValue(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookie != null && AUTH_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void establishSession(AppUser user, HttpServletResponse response) {
        String rawSessionToken = newSessionToken();
        String csrfToken = newSessionToken();
        AuthSession authSession = new AuthSession();
        authSession.setId(UUID.randomUUID().toString());
        authSession.setUserId(user.getId());
        authSession.setSessionTokenHash(hashToken(rawSessionToken));
        authSession.setCsrfToken(csrfToken);
        authSession.setCreatedAt(DateUtils.now());
        authSession.setUpdatedAt(authSession.getCreatedAt());
        authSession.setExpiresAt(authSession.getCreatedAt().plus(AUTH_SESSION_TTL));
        int inserted = authSessionRepository.insertSession(
                authSession.getId(),
                authSession.getUserId(),
                authSession.getSessionTokenHash(),
                authSession.getCsrfToken(),
                authSession.getExpiresAt(),
                authSession.getCreatedAt(),
                authSession.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one auth_session row to be inserted but got " + inserted);
        }

        addAuthCookie(response, rawSessionToken, AUTH_SESSION_TTL);
        addCsrfCookie(response, csrfToken, AUTH_SESSION_TTL);
    }

    private void addAuthCookie(HttpServletResponse response, String token, Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(authProperties.getCookie().isSecure())
                .sameSite(authProperties.getCookie().getSameSite())
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void addCsrfCookie(HttpServletResponse response, String token, Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE_NAME, token)
                .httpOnly(false)
                .secure(authProperties.getCookie().isSecure())
                .sameSite(authProperties.getCookie().getSameSite())
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authProperties.getCookie().isSecure())
                .sameSite(authProperties.getCookie().getSameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearCsrfCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE_NAME, "")
                .httpOnly(false)
                .secure(authProperties.getCookie().isSecure())
                .sameSite(authProperties.getCookie().getSameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String newSessionToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private AuthUserResponse toAuthUserResponse(AppUser user) {
        return AuthUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .status(user.getStatus())
                .role(user.getRole())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();
    }
}
