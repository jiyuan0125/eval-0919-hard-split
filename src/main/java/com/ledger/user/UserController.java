package com.ledger.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateUserRequest request) {
        AppUser user = userService.createUser(request.name());
        return Map.of("id", user.getId(), "name", user.getName());
    }

    @PostMapping("/me/friends")
    public Map<String, Object> addFriend(@CurrentUser Long userId,
                                         @Valid @RequestBody AddFriendRequest request) {
        userService.addFriend(userId, request.friendId());
        return Map.of("friendId", request.friendId(), "status", "FRIEND");
    }

    @GetMapping("/me/friends")
    public List<Map<String, Object>> listFriends(@CurrentUser Long userId) {
        userService.requireUser(userId);
        return userService.friendIds(userId).stream()
                .map(id -> Map.<String, Object>of("id", id))
                .toList();
    }

    public record CreateUserRequest(@NotBlank String name) {
    }

    public record AddFriendRequest(@jakarta.validation.constraints.NotNull Long friendId) {
    }
}
