package com.wikiplays.game;

/** ゲーム API のエラー。HTTP ステータスとユーザー向けメッセージを持つ。 */
public class GameException extends RuntimeException {
    private final int status;

    public GameException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
