package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.entity.PasswordToken.Purpose;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.shared.mail.OutgoingEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

/** Builds the account emails. They are in Chinese only, as the administrators read Chinese. */
@Component
public class PasswordEmailComposer {

    private static final String SET_PASSWORD_PATH = "/admin/reset-password?token=";

    private final String publicOrigin;

    public PasswordEmailComposer(@Value("${app.public-origin:http://localhost:3000}") String publicOrigin) {
        this.publicOrigin = publicOrigin.endsWith("/") ? publicOrigin.substring(0, publicOrigin.length() - 1) : publicOrigin;
    }

    public OutgoingEmail compose(User user, Purpose purpose, String rawToken) {
        String link = publicOrigin + SET_PASSWORD_PATH + rawToken;
        String validity = describe(PasswordTokenService.validityOf(purpose));
        boolean invitation = purpose == Purpose.INVITATION;

        String subject = invitation ? "APSONIC 管理后台账号邀请" : "APSONIC 管理后台密码重置";
        String intro = invitation
                ? "管理员已为您创建了 APSONIC 管理后台账号（用户名：" + user.getUsername() + "）。请点击下方链接设置密码，设置完成后即可登录。"
                : "我们收到了重置您账号密码的请求。请点击下方链接设置新密码。";
        String outro = invitation
                ? "如果您并不需要此账号，请忽略本邮件。"
                : "如果这不是您本人的操作，请忽略本邮件，您的密码不会改变。";
        String button = invitation ? "设置密码" : "重置密码";
        String expiry = "该链接 " + validity + "内有效，且只能使用一次。";
        String greeting = user.getUsername() + "，您好：";

        String text = String.join("\n\n", greeting, intro, link, expiry, outro) + "\n";
        String html = "<div style=\"font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;font-size:15px;line-height:1.7;color:#222\">"
                + "<p>" + HtmlUtils.htmlEscape(greeting) + "</p>"
                + "<p>" + HtmlUtils.htmlEscape(intro) + "</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;padding:10px 22px;background:#00a651;color:#fff;text-decoration:none;border-radius:8px\">"
                + button + "</a></p>"
                + "<p style=\"color:#666;font-size:13px\">如果按钮无法点击，请复制下面的链接到浏览器打开：<br>" + HtmlUtils.htmlEscape(link) + "</p>"
                + "<p>" + expiry + "</p><p style=\"color:#666\">" + HtmlUtils.htmlEscape(outro) + "</p></div>";
        return new OutgoingEmail(user.getEmail(), subject, text, html);
    }

    private static String describe(Duration validity) {
        return validity.toHours() + " 小时";
    }
}
