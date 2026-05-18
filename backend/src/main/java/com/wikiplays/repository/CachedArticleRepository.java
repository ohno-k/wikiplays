package com.wikiplays.repository;

import com.wikiplays.entity.CachedArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CachedArticleRepository extends JpaRepository<CachedArticle, Long> {

    Optional<CachedArticle> findByTitle(String title);

    boolean existsByTitle(String title);

    /** scope, genre に該当する記事をランダムに 1 件返す (PostgreSQL/H2 両対応)。 */
    @Query(value =
        "SELECT * FROM cached_article " +
        "WHERE (:scope IS NULL OR scope = :scope) AND (:genre IS NULL OR genre = :genre) " +
        "ORDER BY RANDOM() LIMIT 1",
        nativeQuery = true)
    Optional<CachedArticle> findRandomByScopeAndGenre(
        @Param("scope") String scope,
        @Param("genre") String genre
    );

    /** scope, genre 指定で count 件返す (デイリーチャレンジ用)。 */
    @Query(value =
        "SELECT * FROM cached_article " +
        "WHERE (:scope IS NULL OR scope = :scope) AND (:genre IS NULL OR genre = :genre) " +
        "ORDER BY RANDOM() LIMIT :n",
        nativeQuery = true)
    List<CachedArticle> findRandomSample(
        @Param("scope") String scope,
        @Param("genre") String genre,
        @Param("n") int n
    );

    /** scope, genre のキャッシュ件数。 */
    long countByScopeAndGenre(String scope, String genre);

    /** scope と genre のあらゆる組み合わせの中で総数。 */
    @Query("SELECT COUNT(c) FROM CachedArticle c")
    long countAll();
}
