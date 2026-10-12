package com.menujuhari.identity.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/**
 * Implementasi produksi: kirim OTP via SMTP (Gmail).
 * Kredensial dibaca dari environment/.env (MAIL_USERNAME, MAIL_PASSWORD).
 *
 * CATATAN EKSTRAKSI: saat notification-service berdiri, kirim email berpindah
 * ke sana via event `otp.requested` (Redis Streams) — antarmuka OtpMailer
 * sengaja kecil agar ekstraksi tidak menyentuh logika OTP.
 */
@Component
@ConditionalOnProperty(name = "app.mail.console-fallback", havingValue = "false", matchIfMissing = true)
public class SmtpOtpMailer implements OtpMailer {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpOtpMailer(JavaMailSender mailSender, com.menujuhari.identity.infra.config.IdentityProperties props) {
		this.mailSender = mailSender;
		this.from = props.mail().from();
	}

	@Override
	@Async("mailExecutor")
	public void sendOtp(String to, String code) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setTo(to);
			helper.setFrom(from);
			helper.setSubject("Kode Masuk Menuju Hari");
			helper.setText(htmlBody(code), true);
			mailSender.send(message);
		}
		catch (Exception e) {
			// MVP: tanpa retry otomatis. User dapat meminta ulang OTP
			// setelah cooldown. Logging gagal kirim untuk observability.
			org.slf4j.LoggerFactory.getLogger(SmtpOtpMailer.class)
					.error("Gagal mengirim OTP ke {}: {}", mask(to), e.getMessage());
		}
	}

	private String htmlBody(String code) {
		return """
				<div style="font-family: Arial, Helvetica, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px;">
				  <h2 style="color: #1f2937;">Kode Masuk Menuju Hari</h2>
				  <p style="color: #4b5563;">Gunakan kode berikut untuk masuk ke akun Anda:</p>
				  <div style="font-size: 32px; font-weight: bold; letter-spacing: 8px; padding: 16px 0; color: #111827;">%s</div>
				  <p style="color: #4b5563;">Kode berlaku selama <b>5 menit</b> dan hanya dapat digunakan satu kali.</p>
				  <p style="color: #9ca3af; font-size: 12px;">Jangan bagikan kode ini kepada siapa pun, termasuk yang mengaku petugas Menuju Hari.</p>
				</div>
				""".formatted(code);
	}

	private String mask(String email) {
		int at = email.indexOf('@');
		if (at <= 2) {
			return "***" + email.substring(Math.max(0, at));
		}
		return email.substring(0, 2) + "***" + email.substring(at);
	}
}
