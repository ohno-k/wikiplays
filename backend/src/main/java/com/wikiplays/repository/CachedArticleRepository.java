package com.wikiplays.repository;

import com.wikiplays.entity.CachedArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

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

    /**
     * scope, genre 指定 + 知名度スコアの範囲 [minScore, maxScoreExclusive) で count 件返す。
     * 範囲は FameScorer の tier 境界 (絶対値) をそのまま渡す。スコア未計算 (null) の記事は対象外。
     */
    @Query(value =
        "SELECT * FROM cached_article " +
        "WHERE (:scope IS NULL OR scope = :scope) AND (:genre IS NULL OR genre = :genre) " +
        "  AND fame_score IS NOT NULL AND fame_score >= :minScore AND fame_score < :maxScore " +
        "ORDER BY RANDOM() LIMIT :n",
        nativeQuery = true)
    List<CachedArticle> findRandomSampleByFameRange(
        @Param("scope") String scope,
        @Param("genre") String genre,
        @Param("minScore") double minScore,
        @Param("maxScore") double maxScoreExclusive,
        @Param("n") int n
    );

    /** scope, genre 指定で知名度スコアが minScore 以上の記事数 (常識レベルの在庫確認用)。 */
    @Query(value =
        "SELECT COUNT(*) FROM cached_article " +
        "WHERE (:scope IS NULL OR scope = :scope) AND (:genre IS NULL OR genre = :genre) " +
        "  AND fame_score IS NOT NULL AND fame_score >= :minScore",
        nativeQuery = true)
    long countByFameScoreAtLeast(
        @Param("scope") String scope,
        @Param("genre") String genre,
        @Param("minScore") double minScore
    );

    /**
     * 閲覧数未取得 (data_json の recentPageViews が null) の記事を id 順に最大 limit 件。
     * 更新しながら舐めるので offset ではなく「前回の最後の id より大きい」で進める。
     */
    @Query(value =
        "SELECT * FROM cached_article " +
        "WHERE id > :afterId AND data_json LIKE '%\"recentPageViews\":null%' " +
        "ORDER BY id LIMIT :n",
        nativeQuery = true)
    List<CachedArticle> findWithoutPageViewsAfterId(@Param("afterId") long afterId, @Param("n") int n);

    /** 知名度スコア未計算の記事 (バックフィル用)。 */
    List<CachedArticle> findByFameScoreIsNull(Pageable pageable);

    /** scope, genre のキャッシュ件数。 */
    long countByScopeAndGenre(String scope, String genre);

    /** scope と genre のあらゆる組み合わせの中で総数。 */
    @Query("SELECT COUNT(c) FROM CachedArticle c")
    long countAll();

    /** コミュニティジャンル指定でランダムに 1 件。 */
    @Query(value =
        "SELECT * FROM cached_article WHERE community_genre_id = :cid " +
        "ORDER BY RANDOM() LIMIT 1",
        nativeQuery = true)
    Optional<CachedArticle> findRandomByCommunityGenreId(@Param("cid") Long communityGenreId);

    /**
     * コミュニティジャンル指定でランダムに 1 件 (指定タイトルを除外)。
     * セッション中に既に出題した記事と被らないようにするためのもの。
     */
    @Query(value =
        "SELECT * FROM cached_article WHERE community_genre_id = :cid " +
        "AND title NOT IN (:excludeTitles) " +
        "ORDER BY RANDOM() LIMIT 1",
        nativeQuery = true)
    Optional<CachedArticle> findRandomByCommunityGenreIdExcluding(
        @Param("cid") Long communityGenreId,
        @Param("excludeTitles") List<String> excludeTitles
    );

    /** コミュニティジャンル指定でキャッシュ件数。 */
    long countByCommunityGenreId(Long communityGenreId);
}
