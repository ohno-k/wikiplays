package com.wikiplays.repository;

import com.wikiplays.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);

    /** ユーザーが関わるフレンド関係 (両方向)。 */
    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.requesterId = :userId OR f.addresseeId = :userId)
          AND (:status IS NULL OR f.status = :status)
        ORDER BY f.createdAt DESC
        """)
    List<Friendship> findByUserAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /** ユーザーが受け取った保留中の招待。 */
    List<Friendship> findByAddresseeIdAndStatus(Long addresseeId, String status);
}
