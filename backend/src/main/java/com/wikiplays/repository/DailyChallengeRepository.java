package com.wikiplays.repository;

import com.wikiplays.entity.DailyChallenge;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, Long> {

    Optional<DailyChallenge> findByDateAndScopeKeyAndGenreKey(LocalDate date, String scopeKey, String genreKey);

    /** アーカイブ用: 日付降順で取得。 */
    List<DailyChallenge> findByOrderByDateDescIdDesc(Pageable pageable);
}
