package com.ledger.user;

import com.ledger.common.ApiException;
import com.ledger.common.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    public UserService(UserRepository userRepository, FriendshipRepository friendshipRepository) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional
    public AppUser createUser(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, "name must not be empty");
        }
        if (userRepository.existsByName(name)) {
            throw new ApiException(ErrorCode.CONFLICT, "user name already exists: " + name);
        }
        return userRepository.save(new AppUser(name.trim()));
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "user not found: " + userId));
    }

    @Transactional
    public void addFriend(Long ownerId, Long friendId) {
        if (ownerId.equals(friendId)) {
            throw new ApiException(ErrorCode.VALIDATION, "cannot add yourself as a friend");
        }
        requireUser(ownerId);
        requireUser(friendId);
        if (friendshipRepository.existsByOwnerIdAndFriendId(ownerId, friendId)) {
            return;
        }
        try {
            friendshipRepository.saveAndFlush(new Friendship(ownerId, friendId));
        } catch (DataIntegrityViolationException ex) {
            // concurrent duplicate add - idempotent no-op
        }
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Long ownerId, Long friendId) {
        return friendshipRepository.existsByOwnerIdAndFriendId(ownerId, friendId);
    }

    @Transactional(readOnly = true)
    public void requireFriendship(Long viewerId, Long targetId) {
        if (!areFriends(viewerId, targetId)) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "user " + viewerId + " is not allowed to view user " + targetId);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> friendIds(Long ownerId) {
        return friendshipRepository.findByOwnerId(ownerId).stream()
                .map(Friendship::getFriendId)
                .toList();
    }
}
