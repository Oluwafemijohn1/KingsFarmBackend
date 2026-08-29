package com.kingsfarm.kingsfarmbackend.relief;

import com.kingsfarm.kingsfarmbackend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A temporary grant letting one staff member ({@code granteeUser}) cover
 * another manager's ({@code onLeaveUser}) module access while they're away —
 * mirrors {@code ReliefGrant} in shared.ts exactly. Administrator-only to
 * create/revoke (see ReliefAccessController). While {@code active}:
 * <ul>
 *   <li>{@code onLeaveUser} cannot log in at all (see AuthService.login) —
 *   an intentionally hard block, not just an access restriction.</li>
 *   <li>{@code granteeUser} gets every module {@code onLeaveUser.role} would
 *   normally reach, additive on top of their own role — implemented as an
 *   extra granted authority baked into their JWT at login/refresh time (see
 *   JwtService/AuthService), since access tokens are already the codebase's
 *   established "recomputed at issuance, not on every request" boundary
 *   (AuthenticatedPrincipal's own javadoc documents the same tradeoff for
 *   account deactivation).</li>
 * </ul>
 * Revoking (or deactivating either party's account — see
 * UserAdminService.setActive) sets {@code active=false} and stamps
 * {@code revokedAt}/{@code revokedBy}; a grant is never deleted, matching
 * every other module's "historical rows are permanent" convention.
 */
@Entity
@Table(name = "relief_grants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReliefGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "on_leave_user_id", nullable = false)
    private User onLeaveUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grantee_user_id", nullable = false)
    private User granteeUser;

    @Column(length = 500)
    private String reason;

    @Column(name = "granted_by", length = 64)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    @PrePersist
    void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }
}
