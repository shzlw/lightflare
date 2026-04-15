package com.lightflare.server.auth;

import com.lightflare.server.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthUserResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
    }

    @PostMapping("/password")
    public AuthUserResponse updatePassword(@RequestBody UpdatePasswordRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        return authService.updatePassword(request, httpRequest, httpResponse);
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpServletRequest request) {
        return authService.getCurrentUserResponse(request);
    }
}
