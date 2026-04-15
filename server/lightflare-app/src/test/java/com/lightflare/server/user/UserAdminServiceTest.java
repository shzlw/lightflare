package com.lightflare.server.user;

import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserAdminServiceTest {

    private FakeAppUserRepository appUserRepository;
    private FakeAppUserIdentityService appUserIdentityService;
    private FakeAuthService authService;
    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        appUserRepository = new FakeAppUserRepository();
        appUserIdentityService = new FakeAppUserIdentityService();
        authService = new FakeAuthService();
        userAdminService = new UserAdminService(
                appUserRepository,
                appUserIdentityService,
                authService,
                new FakePasswordEncoder()
        );
    }

    @Test
    void adminCannotCreateAdminUser() {
        authService.currentUser = user("current-admin", AppRoles.ADMIN);

        assertThrows(ResponseStatusException.class, () -> userAdminService.createUser(createRequest(AppRoles.ADMIN), null));
        assertEquals(0, appUserRepository.insertedUsers.size());
    }

    @Test
    void superAdminCanCreateAdminUser() {
        authService.currentUser = user("current-superadmin", AppRoles.SUPERADMIN);

        UserResponse response = userAdminService.createUser(createRequest(AppRoles.ADMIN), null);

        assertEquals(AppRoles.ADMIN, response.role());
        assertEquals(AppRoles.ADMIN, appUserRepository.insertedUsers.get(0).getRole());
    }

    @Test
    void adminCannotDeleteAdminUser() {
        authService.currentUser = user("current-admin", AppRoles.ADMIN);
        authService.existingUsers.put("target-admin", user("target-admin", AppRoles.ADMIN));

        assertThrows(ResponseStatusException.class, () -> userAdminService.deleteUser("target-admin", null));
        assertEquals(0, appUserRepository.deletedUserIds.size());
        assertEquals(0, authService.revokedSessionUserIds.size());
    }

    @Test
    void superAdminCanDeleteAdminUserAndRevokeSessions() {
        authService.currentUser = user("current-superadmin", AppRoles.SUPERADMIN);
        authService.existingUsers.put("target-admin", user("target-admin", AppRoles.ADMIN));
        appUserRepository.users.put("target-admin", user("target-admin", AppRoles.ADMIN));

        userAdminService.deleteUser("target-admin", null);

        assertEquals(List.of("target-admin"), appUserIdentityService.deletedIdentityUserIds);
        assertEquals(List.of("target-admin"), authService.revokedSessionUserIds);
        assertEquals(List.of("target-admin"), appUserRepository.deletedUserIds);
    }

    private CreateUserRequest createRequest(String role) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("new-admin");
        request.setEmail("admin@example.com");
        request.setDisplayName("New Admin");
        request.setPassword("temporary");
        request.setStatus("ACTIVE");
        request.setRole(role);
        return request;
    }

    private static AppUser user(String id, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(id);
        user.setEmail(id + "@example.com");
        user.setDisplayName(id);
        user.setStatus("ACTIVE");
        user.setRole(role);
        user.setMustChangePassword(Boolean.FALSE);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(user.getCreatedAt());
        return user;
    }

    private class FakeAuthService extends AuthService {

        private AppUser currentUser;
        private final Map<String, AppUser> existingUsers = new HashMap<>();
        private final List<String> revokedSessionUserIds = new ArrayList<>();

        FakeAuthService() {
            super(null, null, null, null, null);
        }

        @Override
        public AppUser requireAdmin(HttpServletRequest request) {
            return currentUser;
        }

        @Override
        public AppUser findExistingUser(String id) {
            return Optional.ofNullable(existingUsers.get(id))
                    .or(() -> appUserRepository.findById(id))
                    .orElseGet(() -> appUser(id));
        }

        @Override
        public void revokeUserSessions(String userId) {
            revokedSessionUserIds.add(userId);
        }

        private AppUser appUser(String id) {
            return existingUsers.computeIfAbsent(id, ignored -> user(id, AppRoles.USER));
        }
    }

    private static class FakeAppUserIdentityService extends AppUserIdentityService {

        private final List<String> deletedIdentityUserIds = new ArrayList<>();

        FakeAppUserIdentityService() {
            super(null, null);
        }

        @Override
        public void deleteIdentitiesForUser(String appUserId) {
            deletedIdentityUserIds.add(appUserId);
        }
    }

    private static class FakePasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded-" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    }

    private static class FakeAppUserRepository implements AppUserRepository {

        private final Map<String, AppUser> users = new HashMap<>();
        private final List<AppUser> insertedUsers = new ArrayList<>();
        private final List<String> deletedUserIds = new ArrayList<>();

        @Override
        public int insertUser(String id,
                              String username,
                              String email,
                              String displayName,
                              String passwordHash,
                              String status,
                              String role,
                              boolean mustChangePassword,
                              OffsetDateTime createdAt,
                              OffsetDateTime updatedAt) {
            AppUser user = new AppUser();
            user.setId(id);
            user.setUsername(username);
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordHash);
            user.setStatus(status);
            user.setRole(role);
            user.setMustChangePassword(mustChangePassword);
            user.setCreatedAt(createdAt);
            user.setUpdatedAt(updatedAt);
            insertedUsers.add(user);
            users.put(id, user);
            return 1;
        }

        @Override
        public int updateUserProfile(String id, String username, String email, String displayName, String status, OffsetDateTime updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateUserPassword(String id, String passwordHash, boolean mustChangePassword, OffsetDateTime updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AppUser> findByUsername(String username) {
            return users.values().stream()
                    .filter(user -> user.getUsername().equalsIgnoreCase(username))
                    .findFirst();
        }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            return users.values().stream()
                    .filter(user -> email.equalsIgnoreCase(user.getEmail()))
                    .findFirst();
        }

        @Override
        public Optional<AppUser> findByUsernameOrEmail(String login) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AppUser> findUserPage(int limit, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AppUser> findUserAndAdminPage(int limit, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countUsers() {
            return users.size();
        }

        @Override
        public long countUsersWithUserRole() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countUsersWithUserOrAdminRole() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteUserById(String id) {
            deletedUserIds.add(id);
            users.remove(id);
            return 1;
        }

        @Override
        public <S extends AppUser> S save(S entity) {
            users.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public <S extends AppUser> Iterable<S> saveAll(Iterable<S> entities) {
            for (S entity : entities) {
                save(entity);
            }
            return entities;
        }

        @Override
        public Optional<AppUser> findById(String id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public boolean existsById(String id) {
            return users.containsKey(id);
        }

        @Override
        public Iterable<AppUser> findAll() {
            return users.values();
        }

        @Override
        public Iterable<AppUser> findAllById(Iterable<String> ids) {
            List<AppUser> found = new ArrayList<>();
            for (String id : ids) {
                findById(id).ifPresent(found::add);
            }
            return found;
        }

        @Override
        public long count() {
            return users.size();
        }

        @Override
        public void deleteById(String id) {
            users.remove(id);
        }

        @Override
        public void delete(AppUser entity) {
            users.remove(entity.getId());
        }

        @Override
        public void deleteAllById(Iterable<? extends String> ids) {
            for (String id : ids) {
                users.remove(id);
            }
        }

        @Override
        public void deleteAll(Iterable<? extends AppUser> entities) {
            for (AppUser entity : entities) {
                users.remove(entity.getId());
            }
        }

        @Override
        public void deleteAll() {
            users.clear();
        }
    }
}
