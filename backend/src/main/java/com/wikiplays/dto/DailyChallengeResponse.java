package com.wikiplays.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyChallengeResponse(
    Long id,
    LocalDate date,
    String scope,
    String genre,
    /** 問題数。記事本文はゲームセッション経由でのみ配信する。 */
    int questionCount,
    long playerCount,
    int topScore,
    /** 認証ユーザーが今日のチャレンジを既プレイ済みならその点数、未プレイなら null。 */
    Integer myScore
) {}
