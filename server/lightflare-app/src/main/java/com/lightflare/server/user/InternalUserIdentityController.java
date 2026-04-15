package com.lightflare.server.user;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/users/{id}/identities")
public class InternalUserIdentityController {

    private final AppUserIdentityService appUserIdentityService;

    @GetMapping
    public List<AppUserIdentityResponse> listIdentities(@PathVariable("id") String id,
                                                        HttpServletRequest request) {
        return appUserIdentityService.listIdentities(id, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppUserIdentityResponse createIdentity(@PathVariable("id") String id,
                                                  @RequestBody AppUserIdentityRequest createRequest,
                                                  HttpServletRequest request) {
        return appUserIdentityService.createIdentity(id, createRequest, request);
    }

    @PutMapping("/{identityId}")
    public AppUserIdentityResponse updateIdentity(@PathVariable("id") String id,
                                                  @PathVariable("identityId") String identityId,
                                                  @RequestBody AppUserIdentityRequest updateRequest,
                                                  HttpServletRequest request) {
        return appUserIdentityService.updateIdentity(id, identityId, updateRequest, request);
    }

    @DeleteMapping("/{identityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIdentity(@PathVariable("id") String id,
                               @PathVariable("identityId") String identityId,
                               HttpServletRequest request) {
        appUserIdentityService.deleteIdentity(id, identityId, request);
    }
}
