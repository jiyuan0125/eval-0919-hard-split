package com.ledger.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "friendship", uniqueConstraints = @UniqueConstraint(
        name = "uk_friend_pair", columnNames = {"owner_id", "friend_id"}))
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "friend_id", nullable = false)
    private Long friendId;

    protected Friendship() {
    }

    public Friendship(Long ownerId, Long friendId) {
        this.ownerId = ownerId;
        this.friendId = friendId;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getFriendId() {
        return friendId;
    }
}
