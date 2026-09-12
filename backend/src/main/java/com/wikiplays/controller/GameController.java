package com.wikiplays.controller;

import com.wikiplays.entity.User;
import com.wikiplays.game.GameException;
import com.wikiplays.game.GameSessionService;
import com.wikiplays.game.SessionView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * サーバー側ゲームセッション API (A モード / デイリー)。
 * 答えはサーバーだけが持ち、クライアントは開示済み段落と 4 択を受け取って 1 文字ずつ送る。
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameSessionService service;

    public GameController(GameSessionService service) {
        this.service = service;
    }

    public record AnswerBody(String character, String playerId) {}
    public record PlayerBody(String playerId) {}

    @PostMapping("/start")
    public ResponseEntity<SessionView> start(@RequestBody GameSessionService.StartRequest req, Authentication auth) {
        return ResponseEntity.ok(service.start(req, principal(auth, req.playerId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionView> get(
        @PathVariable("id") String id,
        @RequestParam(value = "playerId", required = false) String playerId,
        Authentication auth
    ) {
        return ResponseEntity.ok(service.get(id, principal(auth, playerId)));
    }

    @PostMapping("/{id}/reveal")
    public ResponseEntity<SessionView> reveal(@PathVariable("id") String id, @RequestBody(required = false) PlayerBody body, Authentication auth) {
        return ResponseEntity.ok(service.reveal(id, principal(auth, body == null ? null : body.playerId())));
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<SessionView> answer(@PathVariable("id") String id, @RequestBody AnswerBody body, Authentication auth) {
        return ResponseEntity.ok(service.answer(id, body.character(), principal(auth, body.playerId())));
    }

    @PostMapping("/{id}/giveup")
    public ResponseEntity<SessionView> giveUp(@PathVariable("id") String id, @RequestBody(required = false) PlayerBody body, Authentication auth) {
        return ResponseEntity.ok(service.giveUp(id, principal(auth, body == null ? null : body.playerId())));
    }

    @PostMapping("/{id}/next")
    public ResponseEntity<SessionView> next(@PathVariable("id") String id, @RequestBody(required = false) PlayerBody body, Authentication auth) {
        return ResponseEntity.ok(service.next(id, principal(auth, body == null ? null : body.playerId())));
    }

    @ExceptionHandler(GameException.class)
    public ResponseEntity<Map<String, String>> handle(GameException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    private static GameSessionService.Principal principal(Authentication auth, String playerId) {
        User user = (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
        return new GameSessionService.Principal(user, playerId);
    }
}
