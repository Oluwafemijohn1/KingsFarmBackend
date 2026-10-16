package com.kingsfarm.kingsfarmbackend.user;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business-event emails. Everything is @Async and takes plain values (never
 * lazy entities): callers gather names/emails inside their own transaction
 * and hand them over, so a slow SMTP server never delays the request and
 * nothing here touches the database.
 */
@Service
public class NotificationService {

    private final EmailService email;

    public NotificationService(EmailService email) {
        this.email = email;
    }

    @Async
    public void openingStockRequested(List<String> adminEmails, String requesterName, String moduleLabel, String scopeLabel, String reason) {
        for (String to : adminEmails) {
            email.send(to, "Edit permission requested: " + moduleLabel,
                    "Edit permission requested", "Hello,",
                    List.of(requesterName + " is asking for permission to edit the locked Opening Stock for " + moduleLabel + " / " + scopeLabel + ".",
                            "Reason given: " + reason),
                    "Review request", email.appLink("/admin/opening-stock"),
                    "Sign in as an Administrator to approve or deny this request.");
        }
    }

    @Async
    public void openingStockResolved(String to, String requesterName, String moduleLabel, String scopeLabel, boolean approved, String resolverName, String modulePath) {
        email.send(to, "Edit permission " + (approved ? "approved" : "denied") + ": " + moduleLabel,
                approved ? "Edit permission approved" : "Edit permission denied",
                "Hello " + requesterName + ",",
                List.of("Your request to edit the Opening Stock for " + moduleLabel + " / " + scopeLabel + " was "
                        + (approved ? "approved" : "denied") + " by " + resolverName + "."
                        + (approved ? " You can now make the change." : "")),
                approved ? "Open " + moduleLabel : "Open the app", email.appLink(approved ? modulePath : "/dashboard"), null);
    }

    @Async
    public void leaveGranted(String onLeaveEmail, String onLeaveName, String granteeEmail, String granteeName, String reason) {
        String why = (reason == null || reason.isBlank()) ? "" : " Reason: " + reason;
        email.send(onLeaveEmail, "You have been marked on leave", "Leave granted", "Hello " + onLeaveName + ",",
                List.of("An administrator has granted your leave. " + granteeName + " will cover your module while you are away, and you will not be able to sign in until the leave is ended." + why),
                null, null, null);
        email.send(granteeEmail, "You are covering for " + onLeaveName, "Relief access granted", "Hello " + granteeName + ",",
                List.of("You now have relief access to cover " + onLeaveName + "'s module while they are on leave." + why,
                        "Sign out and back in if the extra module does not appear."),
                "Open the app", email.appLink("/dashboard"), null);
    }

    @Async
    public void leaveEnded(String onLeaveEmail, String onLeaveName, String granteeEmail, String granteeName) {
        email.send(onLeaveEmail, "Your leave has ended", "Welcome back", "Hello " + onLeaveName + ",",
                List.of("Your leave has been ended by an administrator. You can sign in again."),
                "Sign in", email.appLink("/login"), null);
        email.send(granteeEmail, "Relief access ended", "Relief access ended", "Hello " + granteeName + ",",
                List.of(onLeaveName + " is back, so your relief access to their module has ended."),
                null, null, null);
    }
}
