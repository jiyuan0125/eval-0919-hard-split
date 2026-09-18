package com.ledger.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    boolean existsByOwnerIdAndFriendId(Long ownerId, Long friendId);

    List<Friendship> findByOwnerId(Long ownerId);
}
