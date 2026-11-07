package com.learningLadder.learningLadder.config.resendConfig.resendEmailTemplateAsync;
import com.learningLadder.learningLadder.config.resendConfig.emailService.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class ResendEmailTemplateAsync {

    private static final String APP_NAME = "Learning Ladder";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private record Theme(String color, String tint, String icon) { }

    private static final Theme REGISTRATION = new Theme("#4F46E5", "#EEF2FF", "&#9993;");    // indigo
    private static final Theme RESEND_OTP   = new Theme("#D97706", "#FFFBEB", "&#128260;");  // amber
    private static final Theme ACTIVATED    = new Theme("#16A34A", "#F0FDF4", "&#9989;");    // green
    private static final Theme FORGOT       = new Theme("#7C3AED", "#F5F3FF", "&#128273;");  // violet
    private static final Theme RESET_OK     = new Theme("#2563EB", "#EFF6FF", "&#128274;");  // blue
    private static final Theme BLOCKED      = new Theme("#DC2626", "#FEF2F2", "&#9940;");    // red
    private static final Theme UNBLOCKED    = new Theme("#65A30D", "#F7FEE7", "&#128275;");  // lime
    private static final Theme MAGIC_LINK   = new Theme("#C026D3", "#FDF4FF", "&#10024;");   // fuchsia
    private static final Theme VERIFIED     = new Theme("#0891B2", "#ECFEFF", "&#128737;");  // cyan

    private static final String WARN_ACCENT = "#F59E0B";
    private static final String WARN_BG = "#FFFBEB";

    private final EmailService emailService;
    private final String supportEmail;

    public ResendEmailTemplateAsync(
            EmailService emailService,
            @Value("${app.support-email:support@learningladder.com}")
            String supportEmail
    ) {
        this.emailService = emailService;
        this.supportEmail = supportEmail;
    }

    // =====================================================================
    // 1. Registration: OTP for email verification
    // =====================================================================
    @Async
    public void sendRegistrationOtpEmail(String to, String firstName, String otpCode, int expiryMinutes) {
        String content = greeting(firstName)
                + p("Welcome to <strong>" + APP_NAME + "</strong>! Use the code below to verify your "
                + "email address and finish setting up your account.")
                + otpBox(REGISTRATION, otpCode)
                + p("This code expires in <strong>" + minutes(expiryMinutes) + "</strong>.")
                + callout(WARN_ACCENT, WARN_BG, "<strong>Keep it private.</strong> " + APP_NAME
                + " will never ask you for this code by phone, chat or email.")
                + p("If you didn't create an account, you can safely ignore this email.");

        dispatch(to, "Verify your " + APP_NAME + " account",
                layout(REGISTRATION, "Your " + APP_NAME + " verification code", "Verify your email address", content),
                "Your " + APP_NAME + " verification code is " + otpCode + ". It expires in " + minutes(expiryMinutes) + ".");
    }

    // =====================================================================
    // 2. Resend OTP (previous code expired)
    // =====================================================================
    @Async
    public void sendResendOtpEmail(String to, String firstName, String otpCode, int expiryMinutes) {
        String content = greeting(firstName)
                + p("Your previous verification code has expired, so we've generated a new one for you.")
                + otpBox(RESEND_OTP, otpCode)
                + p("This new code expires in <strong>" + minutes(expiryMinutes) + "</strong>. "
                + "Any earlier codes no longer work.")
                + callout(WARN_ACCENT, WARN_BG, "<strong>Didn't ask for a new code?</strong> "
                + "Ignore this email or contact us at " + supportLink(RESEND_OTP) + ".");

        dispatch(to, "Your new " + APP_NAME + " verification code",
                layout(RESEND_OTP, "Here is your new verification code", "Here's your new code", content),
                "Your new " + APP_NAME + " verification code is " + otpCode + ". It expires in " + minutes(expiryMinutes) + ".");
    }

    // =====================================================================
    // 3. OTP verified successfully: account activated, user can log in
    // =====================================================================
    @Async
    public void sendAccountActivatedEmail(String to, String firstName, String loginUrl) {
        String content = greeting(firstName)
                + p("Great news! Your verification was successful and your <strong>" + APP_NAME
                + "</strong> account is now active. You can log in and get started right away.")
                + button(ACTIVATED, "Log in to your account", loginUrl)
                + p("We're excited to have you on board.");

        dispatch(to, "Your " + APP_NAME + " account is ready",
                layout(ACTIVATED, "Your account is verified. You can now log in.", "You're all set!", content),
                "Your " + APP_NAME + " account is now active. Log in here: " + loginUrl);
    }

    // =====================================================================
    // 4. Forgot password
    // =====================================================================
    @Async
    public void sendForgotPasswordEmail(String to, String firstName, String resetLink, int expiryMinutes) {
        String content = greeting(firstName)
                + p("We received a request to reset the password for your " + APP_NAME + " account. "
                + "Click the button below to choose a new password.")
                + button(FORGOT, "Reset my password", resetLink)
                + p("This link expires in <strong>" + minutes(expiryMinutes) + "</strong> and can only be used once.")
                + linkFallback(resetLink)
                + callout(FORGOT.color(), FORGOT.tint(), "<strong>Didn't request this?</strong> "
                + "You can ignore this email. Your password will stay the same.");

        dispatch(to, "Reset your " + APP_NAME + " password",
                layout(FORGOT, "Reset your password", "Forgot your password?", content),
                "Reset your " + APP_NAME + " password using this link (expires in " + minutes(expiryMinutes) + "): " + resetLink);
    }

    // =====================================================================
    // 5. Password reset successfully
    // =====================================================================
    @Async
    public void sendPasswordResetSuccessEmail(String to, String firstName, String loginUrl) {
        String content = greeting(firstName)
                + p("Your " + APP_NAME + " password was changed successfully on <strong>"
                + DATE_TIME.format(Instant.now()) + "</strong>. You can now log in with your new password.")
                + button(RESET_OK, "Log in", loginUrl)
                + callout("#DC2626", "#FEF2F2", "<strong>Wasn't you?</strong> Contact us immediately at "
                + supportLink(RESET_OK) + " so we can secure your account.");

        dispatch(to, "Your " + APP_NAME + " password was changed",
                layout(RESET_OK, "Your password has been reset", "Password reset successful", content),
                "Your " + APP_NAME + " password was changed successfully. If this wasn't you, contact " + supportEmail + ".");
    }

    // =====================================================================
    // 6a. Account blocked
    // =====================================================================
    @Async
    public void sendAccountBlockedEmail(String to, String firstName, String reason) {
        String reasonHtml = (reason == null || reason.isBlank()) ? ""
                : callout(BLOCKED.color(), BLOCKED.tint(), "<strong>Reason:</strong> " + esc(reason));

        String content = greeting(firstName)
                + p("Your <strong>" + APP_NAME + "</strong> account has been <strong style=\"color:"
                + BLOCKED.color() + ";\">blocked</strong>. While it is blocked you will not be able to log in "
                + "or access any services.")
                + reasonHtml
                + p("If you believe this was a mistake, please reach out to our team and we'll look into it.")
                + button(BLOCKED, "Contact support", "mailto:" + supportEmail);

        dispatch(to, "Your " + APP_NAME + " account has been blocked",
                layout(BLOCKED, "Your account has been blocked", "Account blocked", content),
                "Your " + APP_NAME + " account has been blocked. Contact " + supportEmail + " if you think this is a mistake.");
    }

    // =====================================================================
    // 6b. Account unblocked
    // =====================================================================
    @Async
    public void sendAccountUnblockedEmail(String to, String firstName, String loginUrl) {
        String content = greeting(firstName)
                + p("Good news! Your <strong>" + APP_NAME + "</strong> account has been <strong style=\"color:"
                + UNBLOCKED.color() + ";\">unblocked</strong>. You can log in and use all services again.")
                + button(UNBLOCKED, "Log in to your account", loginUrl)
                + p("Thank you for your patience. If you have any questions, contact us at "
                + supportLink(UNBLOCKED) + ".");

        dispatch(to, "Your " + APP_NAME + " account has been unblocked",
                layout(UNBLOCKED, "Your account has been restored", "Welcome back!", content),
                "Your " + APP_NAME + " account has been unblocked. Log in here: " + loginUrl);
    }

    // =====================================================================
    // 7. Magic link login request
    // =====================================================================
    @Async
    public void sendMagicLinkEmail(String to, String firstName, String magicLink, int expiryMinutes) {
        String content = greeting(firstName)
                + p("You asked to sign in to " + APP_NAME + " with a magic link. "
                + "Click the button below to log in. No password needed.")
                + button(MAGIC_LINK, "Sign in to " + APP_NAME, magicLink)
                + p("This link expires in <strong>" + minutes(expiryMinutes) + "</strong> and works only once.")
                + linkFallback(magicLink)
                + callout(MAGIC_LINK.color(), MAGIC_LINK.tint(), "<strong>Didn't request this?</strong> "
                + "Ignore this email. Nobody can sign in without clicking this link, so don't forward it to anyone.");

        dispatch(to, "Your " + APP_NAME + " magic sign-in link",
                layout(MAGIC_LINK, "Your magic sign-in link is here", "Your magic link", content),
                "Sign in to " + APP_NAME + " (expires in " + minutes(expiryMinutes) + "): " + magicLink);
    }

    // =====================================================================
    // 8. Email address verified (OTP confirmed via email)
    // =====================================================================
    @Async
    public void sendEmailVerifiedEmail(String to, String firstName) {
        String content = greeting(firstName)
                + p("Your email address <strong>" + esc(to) + "</strong> was successfully verified on <strong>"
                + DATE_TIME.format(Instant.now()) + "</strong>.")
                + callout(VERIFIED.color(), VERIFIED.tint(), "This address will now be used for account notifications, "
                + "password resets and secure sign-in.")
                + p("If you didn't verify this email address, please contact us at " + supportLink(VERIFIED) + ".");

        dispatch(to, "Your email address has been verified",
                layout(VERIFIED, "Your email address is verified", "Email verified", content),
                "Your email address was verified on " + APP_NAME + ".");
    }

    // =====================================================================
    // Shared layout and building blocks
    // =====================================================================

    private String layout(Theme t, String preheader, String title, String content) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>{{title}}</title>
                </head>
                <body style="margin:0;padding:0;background-color:#F3F4F6;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">{{preheader}}</div>
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#F3F4F6;padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;width:100%;background-color:#FFFFFF;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                        <tr>
                          <td align="center" bgcolor="{{color}}" style="background-color:{{color}};padding:32px 24px 28px 24px;">
                            <div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;color:#FFFFFF;font-weight:600;">{{appName}}</div>
                            <div style="margin:16px auto 0 auto;width:64px;height:64px;line-height:64px;border-radius:50%;background-color:rgba(255,255,255,0.22);font-size:30px;color:#FFFFFF;text-align:center;">{{icon}}</div>
                            <h1 style="margin:16px 0 0 0;font-size:24px;line-height:1.3;color:#FFFFFF;font-weight:700;">{{title}}</h1>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px 36px;color:#374151;font-size:15px;line-height:1.7;">{{content}}</td>
                        </tr>
                        <tr><td style="padding:0 36px;"><div style="border-top:1px solid #E5E7EB;"></div></td></tr>
                        <tr>
                          <td align="center" style="padding:20px 36px 28px 36px;color:#9CA3AF;font-size:12px;line-height:1.6;">
                            Need help? Contact us at <a href="mailto:{{support}}" style="color:{{color}};text-decoration:none;">{{support}}</a><br>
                            &copy; {{year}} {{appName}}. This is an automated message, please do not reply.
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{preheader}}", esc(preheader))
                .replace("{{title}}", esc(title))
                .replace("{{color}}", t.color())
                .replace("{{icon}}", t.icon())
                .replace("{{appName}}", APP_NAME)
                .replace("{{support}}", esc(supportEmail))
                .replace("{{year}}", String.valueOf(Year.now().getValue()))
                .replace("{{content}}", content);   // last, so user data can never be re-interpreted as a placeholder
    }

    private String greeting(String firstName) {
        return p("Hi <strong>" + esc(firstName) + "</strong>,");
    }

    private String p(String html) {
        return "<p style=\"margin:0 0 16px 0;\">" + html + "</p>";
    }

    private String otpBox(Theme t, String code) {
        return "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin:24px 0;\"><tr>"
                + "<td align=\"center\" style=\"background-color:" + t.tint() + ";border:2px dashed " + t.color()
                + ";border-radius:10px;padding:20px 12px;\">"
                + "<div style=\"font-size:12px;letter-spacing:1.5px;text-transform:uppercase;color:#6B7280;margin-bottom:8px;\">Your verification code</div>"
                + "<div style=\"font-family:'Courier New',Courier,monospace;font-size:38px;font-weight:700;letter-spacing:10px;color:"
                + t.color() + ";\">" + esc(code) + "</div>"
                + "</td></tr></table>";
    }

    private String button(Theme t, String label, String url) {
        return "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" align=\"center\" style=\"margin:24px auto;\"><tr>"
                + "<td align=\"center\" bgcolor=\"" + t.color() + "\" style=\"border-radius:8px;\">"
                + "<a href=\"" + esc(url) + "\" target=\"_blank\" style=\"display:inline-block;padding:14px 32px;font-size:16px;"
                + "font-weight:600;color:#FFFFFF;text-decoration:none;border-radius:8px;\">" + esc(label) + "</a>"
                + "</td></tr></table>";
    }

    private String callout(String accent, String bg, String html) {
        return "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin:16px 0;\"><tr>"
                + "<td style=\"background-color:" + bg + ";border-left:4px solid " + accent
                + ";border-radius:6px;padding:14px 16px;font-size:14px;line-height:1.6;color:#374151;\">"
                + html + "</td></tr></table>";
    }

    private String linkFallback(String url) {
        return "<p style=\"margin:0 0 16px 0;font-size:12px;color:#6B7280;word-break:break-all;\">"
                + "If the button doesn't work, copy and paste this link into your browser:<br>"
                + "<a href=\"" + esc(url) + "\" style=\"color:#6B7280;\">" + esc(url) + "</a></p>";
    }

    private String supportLink(Theme t) {
        return "<a href=\"mailto:" + esc(supportEmail) + "\" style=\"color:" + t.color()
                + ";font-weight:600;text-decoration:none;\">" + esc(supportEmail) + "</a>";
    }

    private String minutes(int m) {
        return m == 1 ? "1 minute" : m + " minutes";
    }

    /** Escapes anything user-controlled before it goes into HTML. */
    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    /** Emails run in the background, so failures are logged here instead of bubbling to the caller. */
    private void dispatch(String to, String subject, String html, String text) {
        try {
            emailService.send(List.of(to), subject, html, text);
        } catch (Exception ex) {
            log.error("Failed to send email [subject={}]: {}", subject, ex.getMessage());
        }
    }
}