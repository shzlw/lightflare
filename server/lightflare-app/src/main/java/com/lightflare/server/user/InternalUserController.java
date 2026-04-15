package com.lightflare.server.user;

import com.lightflare.server.user.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/users")
public class InternalUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    public UserPageResponse listUsers(@RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "size", defaultValue = "20") int size,
                                      HttpServletRequest request) {
        return userAdminService.listUsers(page, size, request);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable("id") String id, HttpServletRequest request) {
        return userAdminService.getUser(id, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody CreateUserRequest createRequest, HttpServletRequest request) {
        return userAdminService.createUser(createRequest, request);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable("id") String id,
                                   @RequestBody UpdateUserRequest updateRequest,
                                   HttpServletRequest request) {
        return userAdminService.updateUser(id, updateRequest, request);
    }

    @PostMapping("/{id}/password")
    public UserResponse updatePassword(@PathVariable("id") String id,
                                       @RequestBody UpdateUserPasswordRequest updateRequest,
                                       HttpServletRequest request) {
        return userAdminService.updatePassword(id, updateRequest, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable("id") String id, HttpServletRequest request) {
        userAdminService.deleteUser(id, request);
    }
}
