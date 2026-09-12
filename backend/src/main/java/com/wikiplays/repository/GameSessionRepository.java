package com.wikiplays.repository;

import com.wikiplays.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface GameSessionRepository extends JpaRepository<GameSession, String> {

    /** 古いセッションの掃除用。 */
    @Modifying
    @Query("DELETE FROM GameSession s WHERE s.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
