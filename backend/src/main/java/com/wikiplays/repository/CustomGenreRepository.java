package com.wikiplays.repository;

import com.wikiplays.entity.CustomGenre;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomGenreRepository extends JpaRepository<CustomGenre, Long> {

    List<CustomGenre> findByOrderByPlayCountDescCreatedAtDesc(Pageable pageable);

    List<CustomGenre> findByCreatorIdOrderByCreatedAtDesc(String creatorId);
}
