package com.wikiplays.repository;

import com.wikiplays.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    /** 受信者として保留中のチャレンジ。 */
    List<Challenge> findByRecipientIdAndStatusOrderByCreatedAtDesc(Long recipientId, String status);

    /** ユーザーが関わる全チャレンジ。 */
    List<Challenge> findByCreatorIdOrRecipientIdOrderByCreatedAtDesc(Long creatorId, Long recipientId);
}
