package com.lightflare.server.user;

import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final AppUserRepository appUserRepository;
    private final AppUserIdentityService appUserIdentityService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    public UserPageResponse listUsers(int page, int size, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        boolean superAdmin = AppRoles.isSuperAdmin(currentUser.getRole());
        long totalItems = superAdmin
                ? appUserRepository.countUsersWithUserOrAdminRole()
                : appUserRepository.countUsersWithUserRole();
        List<UserResponse> items = (superAdmin
                ? appUserRepository.findUserAndAdminPage(normalizedSize, (long) normalizedPage * normalizedSize)
                : appUserRepository.findUserPage(normalizedSize, (long) normalizedPage * normalizedSize))
                .stream()
                .map(this::toResponse)
                .toList();

        return UserPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    public UserResponse getUser(String id, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        return toResponse(requireManageableUser(id, currentUser));
    }

    public UserResponse createUser(CreateUserRequest createRequest, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        validateCreateRequest(createRequest);

        String normalizedUsername = normalizeUsername(createRequest.getUsername());
        String normalizedEmail = normalizeEmail(createRequest.getEmail());
        String normalizedRole = normalizeCreatableRole(createRequest.getRole(), currentUser);
        ensureUniqueUser(normalizedUsername, normalizedEmail, null);

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setDisplayName(StringUtils.hasText(createRequest.getDisplayName()) ? createRequest.getDisplayName().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(createRequest.getPassword()));
        user.setStatus(normalizeStatus(createRequest.getStatus()));
        user.setRole(normalizedRole);
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
        return toResponse(authService.findExistingUser(user.getId()));
    }

    public UserResponse updateUser(String id, UpdateUserRequest updateRequest, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        if (updateRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        AppUser user = requireManageableUser(id, currentUser);
        String normalizedUsername = normalizeUsername(updateRequest.getUsername());
        String normalizedEmail = normalizeEmail(updateRequest.getEmail());
        ensureUniqueUser(normalizedUsername, normalizedEmail, id);

        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setDisplayName(StringUtils.hasText(updateRequest.getDisplayName()) ? updateRequest.getDisplayName().trim() : null);
        user.setStatus(normalizeStatus(updateRequest.getStatus()));
        user.setUpdatedAt(DateUtils.now());
        int updated = appUserRepository.updateUserProfile(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.getUpdatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one app_user row to be updated but got " + updated);
        }
        return toResponse(authService.findExistingUser(user.getId()));
    }

    public UserResponse updatePassword(String id, UpdateUserPasswordRequest updateRequest, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        if (updateRequest == null || !StringUtils.hasText(updateRequest.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        AppUser user = requireManageableUser(id, currentUser);
        user.setPasswordHash(passwordEncoder.encode(updateRequest.getNewPassword()));
        user.setMustChangePassword(updateRequest.getMustChangePassword() == null || updateRequest.getMustChangePassword());
        user.setUpdatedAt(DateUtils.now());
        int updated = appUserRepository.updateUserPassword(
                user.getId(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getUpdatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one app_user row to be updated but got " + updated);
        }
        AppUser savedUser = authService.findExistingUser(user.getId());
        authService.revokeUserSessions(savedUser.getId());
        return toResponse(savedUser);
    }

    public void deleteUser(String id, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        requireManageableUser(id, currentUser);
        appUserIdentityService.deleteIdentitiesForUser(id);
        authService.revokeUserSessions(id);

        int deleted = appUserRepository.deleteUserById(id);
        if (deleted != 1) {
            throw new IllegalStateException("Expected one app_user row to be deleted but got " + deleted);
        }
    }

    private void validateCreateRequest(CreateUserRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (!StringUtils.hasText(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
    }

    private void ensureUniqueUser(String username, String email, String excludedUserId) {
        if (appUserRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(excludedUserId))
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (StringUtils.hasText(email) && appUserRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludedUserId))
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase() : "ACTIVE";
    }

    private String normalizeCreatableRole(String requestedRole, AppUser currentUser) {
        String normalizedRole = StringUtils.hasText(requestedRole)
                ? requestedRole.trim().toUpperCase()
                : AppRoles.USER;

        if (AppRoles.isUser(normalizedRole)) {
            return AppRoles.USER;
        }
        if (AppRoles.isAdmin(normalizedRole) && AppRoles.isSuperAdmin(currentUser.getRole())) {
            return AppRoles.ADMIN;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create user with role " + normalizedRole);
    }

    private AppUser requireManageableUser(String id, AppUser currentUser) {
        AppUser user = authService.findExistingUser(id);
        if (AppRoles.isSuperAdmin(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (AppRoles.isAdmin(user.getRole()) && !AppRoles.isSuperAdmin(currentUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private UserResponse toResponse(AppUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .status(user.getStatus())
                .role(user.getRole())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
