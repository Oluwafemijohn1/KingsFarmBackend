package com.kingsfarm.kingsfarmbackend.relief.dto;

import com.kingsfarm.kingsfarmbackend.relief.ReliefGrant;
import com.kingsfarm.kingsfarmbackend.user.Role;

import java.time.Instant;

public record ReliefGrantResponse(
        Long id,
        Long onLeaveUserId,
        String onLeaveUsername,
        String onLeaveName,
        Role onLeaveRole,
        Long granteeUserId,
        String granteeUsername,
        String granteeName,
        Role granteeRole,
        String reason,
        String grantedBy,
        Instant grantedAt,
        boolean active,
        Instant revokedAt,
        String revokedBy
) {
    public static ReliefGrantResponse from(ReliefGrant g) {
        return new ReliefGrantResponse(
                g.getId(),
                g.getOnLeaveUser().getId(), g.getOnLeaveUser().getUsername(), g.getOnLeaveUser().getFullName(), g.getOnLeaveUser().getRole(),
                g.getGranteeUser().getId(), g.getGranteeUser().getUsername(), g.getGranteeUser().getFullName(), g.getGranteeUser().getRole(),
                g.getReason(), g.getGrantedBy(), g.getGrantedAt(), g.isActive(), g.getRevokedAt(), g.getRevokedBy()
        );
    }
}
