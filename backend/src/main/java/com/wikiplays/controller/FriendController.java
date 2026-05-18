package com.wikiplays.controller;

import com.wikiplays.entity.Friendship;
import com.wikiplays.entity.User;
import com.wikiplays.repository.FriendshipRepository;
import com.wikiplays.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendController(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    /** フレンド招待を送る (メールアドレス指定)。 */
    @PostMapping("/invite")
    @Transactional
    public ResponseEntity<?> invite(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) {
            return ResponseEntity.status(401).build();
        }
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "メールアドレスを指定してください"));
        Optional<User> targetOpt = userRepository.findByEmail(email);
        if (targetOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "ユーザーが見つかりません"));
        User target = targetOpt.get();
        if (target.getId().equals(me.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "自分自身は招待できません"));
        }
        // 既存関係チェック (双方向)
        if (friendshipRepository.findByRequesterIdAndAddresseeId(me.getId(), target.getId()).isPresent()
            || friendshipRepository.findByRequesterIdAndAddresseeId(target.getId(), me.getId()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "既に招待済みまたはフレンドです"));
        }
        Friendship f = new Friendship();
        f.setRequesterId(me.getId());
        f.setAddresseeId(target.getId());
        f.setStatus("PENDING");
        f.setCreatedAt(Instant.now());
        friendshipRepository.save(f);
        return ResponseEntity.ok(Map.of("id", f.getId()));
    }

    /** フレンド一覧 (関係 + 相手ユーザー情報)。 */
    @GetMapping
    public ResponseEntity<?> list(
        Authentication auth,
        @RequestParam(value = "status", required = false) String status
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) {
            return ResponseEntity.status(401).build();
        }
        List<Friendship> friendships = friendshipRepository.findByUserAndStatus(me.getId(), status);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friendship f : friendships) {
            Long otherId = f.getRequesterId().equals(me.getId()) ? f.getAddresseeId() : f.getRequesterId();
            boolean isInviter = f.getRequesterId().equals(me.getId());
            userRepository.findById(otherId).ifPresent(other -> {
                result.add(Map.of(
                    "id", f.getId(),
                    "status", f.getStatus(),
                    "isInviter", isInviter,
                    "userId", other.getId(),
                    "displayName", other.getDisplayName(),
                    "email", other.getEmail(),
                    "createdAt", f.getCreatedAt().toString()
                ));
            });
        }
        return ResponseEntity.ok(result);
    }

    /** 招待を承認。 */
    @PostMapping("/{id}/accept")
    @Transactional
    public ResponseEntity<?> accept(@PathVariable("id") Long id, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) return ResponseEntity.status(401).build();
        Optional<Friendship> opt = friendshipRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Friendship f = opt.get();
        if (!f.getAddresseeId().equals(me.getId())) return ResponseEntity.status(403).build();
        f.setStatus("ACCEPTED");
        f.setRespondedAt(Instant.now());
        return ResponseEntity.ok().build();
    }

    /** 関係を削除 (拒否 or フレンド解除)。 */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> remove(@PathVariable("id") Long id, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User me)) return ResponseEntity.status(401).build();
        Optional<Friendship> opt = friendshipRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Friendship f = opt.get();
        if (!f.getRequesterId().equals(me.getId()) && !f.getAddresseeId().equals(me.getId())) {
            return ResponseEntity.status(403).build();
        }
        friendshipRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
