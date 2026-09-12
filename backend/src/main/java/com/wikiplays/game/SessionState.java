package com.wikiplays.game;

import java.util.ArrayList;
import java.util.List;

/**
 * game_session.state_json に保存する進行状態。Jackson でそのまま直列化する。
 * サーバーだけが持つ情報 (タイトル・答えの文字・全段落) を含むので、クライアントには渡さない。
 */
public class SessionState {

    public List<Question> questions = new ArrayList<>();
    /** 現在の問題インデックス (0 始まり)。 */
    public int current = 0;
    /** セッション終了時に付与した XP (ログインユーザーのみ)。 */
    public Xp xp;
    /** デイリーのスコアを記録できたか (デイリー以外は null)。 */
    public Boolean dailyRecorded;

    public static class Xp {
        public int gained;
        public boolean capped;
        public boolean leveledUp;
        public long xp;
        public int level;
        public long xpIntoLevel;
        public long xpForNextLevel;
        public int dailyRemaining;
        public int streakDays;
    }

    public static class Question {
        public String title;
        public String pageUrl;
        /** マスク済み段落 (記事の先頭から順)。開示は末尾から行う。 */
        public List<String> paragraphs = new ArrayList<>();
        /** 答えの文字列 (1 要素 = 1 文字)。記号は options が空。 */
        public List<String> chars = new ArrayList<>();
        /** 各位置の 4 択。記号位置は空リスト。 */
        public List<List<String>> options = new ArrayList<>();

        /** 問題を配信した時刻 (epoch ms)。時間経過による自動開示の基準。 */
        public long startedAtMs;
        /** クライアントの明示的な開示要求で開いた段落数。 */
        public int revealed = 1;
        /** 最初の 1 文字を入力した時点で確定した開示段落数 (null は未確定)。 */
        public Integer lockedRevealed;
        /** 入力済み位置。 */
        public int pos = 0;
        /** ライフを 1 つ使ったか (1 回のミスは許容、スコア半減)。 */
        public boolean lifeUsed = false;
        /** ライフを使ったときに押した誤答文字 (同じ位置の選択肢から除外する)。 */
        public String missedChar;
        public boolean answered = false;
        public boolean correct = false;
        public int score = 0;
        public String wrongChar;
        public int correctChars = 0;
        public int totalInputChars = 0;
        public int revealedCount = 1;
    }
}
