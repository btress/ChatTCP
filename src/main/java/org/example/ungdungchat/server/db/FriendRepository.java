package org.example.ungdungchat.server.db;

import org.example.ungdungchat.shared.model.FriendRequest;
import org.example.ungdungchat.shared.model.Friendship;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FriendRepository {
    private final Map<String, FriendRequest> requests = new ConcurrentHashMap<>();
    private final Map<String, Friendship> friendships = new ConcurrentHashMap<>();
    private final Set<String> blockedPairs = Collections.synchronizedSet(new HashSet<>());

    public void saveRequest(FriendRequest req) {
        if (req.getId() == null) {
            req.setId(UUID.randomUUID().toString());
        }
        requests.put(req.getId(), req);
    }

    public FriendRequest findRequestById(String id) {
        return requests.get(id);
    }

    public List<FriendRequest> getPendingRequestsForUser(String userId) {
        List<FriendRequest> list = new ArrayList<>();
        for (FriendRequest r : requests.values()) {
            if (userId.equals(r.getToUserId()) && FriendRequest.STATUS_PENDING.equals(r.getStatus())) {
                list.add(r);
            }
        }
        return list;
    }

    public void acceptRequest(String requestId) {
        FriendRequest r = requests.get(requestId);
        if (r != null) {
            r.setStatus(FriendRequest.STATUS_ACCEPTED);
            Friendship f = new Friendship(r.getFromUserId(), r.getToUserId());
            f.setId(UUID.randomUUID().toString());
            friendships.put(f.getId(), f);
        }
    }

    public void rejectRequest(String requestId) {
        FriendRequest r = requests.get(requestId);
        if (r != null) {
            r.setStatus(FriendRequest.STATUS_REJECTED);
        }
    }

    public List<String> getFriendUserIds(String userId) {
        Set<String> friendIds = new HashSet<>();
        for (Friendship f : friendships.values()) {
            if (Friendship.STATUS_ACCEPTED.equals(f.getStatus())) {
                if (userId.equals(f.getUser1Id())) friendIds.add(f.getUser2Id());
                else if (userId.equals(f.getUser2Id())) friendIds.add(f.getUser1Id());
            }
        }
        return new ArrayList<>(friendIds);
    }

    public boolean areFriends(String u1, String u2) {
        return getFriendUserIds(u1).contains(u2);
    }

    public void removeFriend(String u1, String u2) {
        friendships.values().removeIf(f ->
                (u1.equals(f.getUser1Id()) && u2.equals(f.getUser2Id())) ||
                (u2.equals(f.getUser1Id()) && u1.equals(f.getUser2Id())));
    }

    public void blockUser(String blockerId, String blockedId) {
        blockedPairs.add(blockerId + ":" + blockedId);
        removeFriend(blockerId, blockedId);
    }

    public void unblockUser(String blockerId, String blockedId) {
        blockedPairs.remove(blockerId + ":" + blockedId);
    }

    public boolean isBlocked(String u1, String u2) {
        // Returns true if u1 blocked u2 OR u2 blocked u1
        return blockedPairs.contains(u1 + ":" + u2) || blockedPairs.contains(u2 + ":" + u1);
    }
}
