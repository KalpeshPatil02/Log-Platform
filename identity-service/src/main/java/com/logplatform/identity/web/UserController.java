package com.logplatform.identity.web;

import com.logplatform.identity.dto.Dtos.CreateUserRequest;
import com.logplatform.identity.dto.Dtos.UserResponse;
import com.logplatform.identity.security.CurrentUser;
import com.logplatform.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest req, @AuthenticationPrincipal Jwt jwt) {
        return users.create(req, CurrentUser.from(jwt));
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Page<UserResponse> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                   @AuthenticationPrincipal Jwt jwt) {
        return users.list(CurrentUser.from(jwt), pageable);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.me(CurrentUser.from(jwt));
    }
}
