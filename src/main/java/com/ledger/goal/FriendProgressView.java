package com.ledger.goal;

/**
 * Deliberately carries no monetary field. The only signal exposed to friends is
 * a rounded integer percentage.
 */
public record FriendProgressView(Long userId, String month, int percent) {
}
