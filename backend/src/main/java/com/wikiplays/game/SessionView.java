package com.wikiplays.game;

import java.util.List;

/** クライアントに返すセッション状態。答えに繋がる情報は含めない。 */
public record SessionView(
    String sessionId,
    String mode,
    String genre,
    String scope,
    Long communityGenreId,
    Long dailyChallengeId,
    String difficulty,
    int intervalMs,
    int totalQuestions,
    int index,
    boolean finished,
    int totalScore,
    int maxScore,
    QuestionView question,
    Summary summary
) {
    public record Slot(String skip, List<String> options) {}

    public record QuestionView(
        int paragraphCount,
        int revealed,
        /** 開示済み段落 (記事末尾側)。 */
        List<String> paragraphs,
        List<Slot> slots,
        int answerLength,
        int pos,
        boolean lifeUsed,
        String missedChar,
        boolean locked,
        boolean answered,
        QuestionResult result
    ) {}

    public record QuestionResult(
        String title,
        String pageUrl,
        boolean correct,
        int score,
        int revealedCount,
        int correctChars,
        int totalInputChars,
        String wrongChar,
        boolean lifeUsed
    ) {}

    public record Summary(
        List<QuestionResult> results,
        Xp xp,
        Boolean dailyRecorded
    ) {}

    public record Xp(
        int xpGained,
        boolean xpCapped,
        boolean leveledUp,
        long xp,
        int level,
        long xpIntoLevel,
        long xpForNextLevel,
        int dailyRemaining,
        int streakDays
    ) {}
}
