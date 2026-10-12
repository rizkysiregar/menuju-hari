package com.menujuhari.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.menujuhari.identity.domain.otp.OtpChallenge;
import com.menujuhari.identity.domain.otp.OtpChallengeRepository;
import com.menujuhari.identity.domain.otp.OtpStatus;
import com.menujuhari.identity.domain.user.User;
import com.menujuhari.identity.domain.user.UserRepository;
import com.menujuhari.identity.domain.user.UserRole;
import com.menujuhari.identity.infra.config.IdentityProperties;
import com.menujuhari.identity.infra.ratelimit.RedisRateLimiter;
import com.menujuhari.shared.security.SessionService;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

	@Mock
	OtpChallengeRepository otpRepository;

	@Mock
	UserRepository userRepository;

	@Mock
	SessionService sessionService;

	@Mock
	RedisRateLimiter rateLimiter;

	@Mock
	OtpMailer mailer;

	IdentityProperties props = new IdentityProperties(
			new IdentityProperties.Otp(Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5, 10, 20),
			new IdentityProperties.Mail("Menuju Hari <no-reply@menujuhari.id>", false));

	OtpService otpService;

	@BeforeEach
	void setUp() {
		otpService = new OtpService(otpRepository, userRepository, sessionService, rateLimiter, mailer, props);
	}

	private OtpChallenge challengeWithCode(String code) {
		String salt = OtpCodeHasher.newSalt();
		return new OtpChallenge("budi@example.com", OtpCodeHasher.hash(salt, code), salt,
				Instant.now().plusSeconds(300));
	}

	@Test
	void requestOtpMenyimpanChallengeDanMengirimEmail() {
		otpService.requestOtp("  Budi@Example.COM ", "127.0.0.1");

		verify(rateLimiter).requirePermitOnce(anyString(), eq(Duration.ofSeconds(60)));
		verify(otpRepository).save(any(OtpChallenge.class));
		verify(mailer).sendOtp(eq("budi@example.com"), anyString());
	}

	@Test
	void requestOtpDitolakSaatCooldownAktif() {
		org.mockito.Mockito.doThrow(new RateLimitExceededException())
				.when(rateLimiter).requirePermitOnce(anyString(), any(Duration.class));

		assertThatThrownBy(() -> otpService.requestOtp("budi@example.com", "127.0.0.1"))
				.isInstanceOf(RateLimitExceededException.class);
		verify(otpRepository, never()).save(any(OtpChallenge.class));
	}

	@Test
	void verifyKodeBenarMembuatUserDanSesi() {
		OtpChallenge challenge = challengeWithCode("123456");
		when(otpRepository.findFirstByEmailAndStatusOrderByCreatedAtDesc("budi@example.com", OtpStatus.ACTIVE))
				.thenReturn(Optional.of(challenge));
		when(userRepository.findByEmailAndDeletedAtIsNull("budi@example.com")).thenReturn(Optional.empty());
		// Simulasikan Hibernate: id terisi saat save
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User saved = inv.getArgument(0);
			org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", java.util.UUID.randomUUID());
			return saved;
		});
		when(sessionService.create(any(java.util.UUID.class), eq("budi@example.com"), eq("CUSTOMER")))
				.thenReturn("token-rahasia");

		OtpService.VerifyResult result = otpService.verify("budi@example.com", "123456");

		assertThat(result.sessionToken()).isEqualTo("token-rahasia");
		assertThat(result.user().getEmail()).isEqualTo("budi@example.com");
		assertThat(result.user().getRole()).isEqualTo(UserRole.CUSTOMER);
		verify(otpRepository).updateStatus(challenge.getId(), OtpStatus.VERIFIED);
	}

	@Test
	void verifyKodeSalahMenambahPercobaanDanGagal() {
		OtpChallenge challenge = challengeWithCode("123456");
		when(otpRepository.findFirstByEmailAndStatusOrderByCreatedAtDesc("budi@example.com", OtpStatus.ACTIVE))
				.thenReturn(Optional.of(challenge));
		when(otpRepository.incrementAttempts(challenge.getId())).thenReturn(1);

		assertThatThrownBy(() -> otpService.verify("budi@example.com", "999999"))
				.isInstanceOf(OtpVerificationException.class);
		verify(otpRepository, never()).updateStatus(challenge.getId(), OtpStatus.VERIFIED);
	}

	@Test
	void verifyLewatBatasPercobaanMenandaiChallengeExhausted() {
		OtpChallenge challenge = challengeWithCode("123456");
		when(otpRepository.findFirstByEmailAndStatusOrderByCreatedAtDesc("budi@example.com", OtpStatus.ACTIVE))
				.thenReturn(Optional.of(challenge));
		when(otpRepository.incrementAttempts(challenge.getId())).thenReturn(5);

		assertThatThrownBy(() -> otpService.verify("budi@example.com", "999999"))
				.isInstanceOf(OtpVerificationException.class);
		verify(otpRepository).updateStatus(challenge.getId(), OtpStatus.EXHAUSTED);
	}

	@Test
	void verifyChallengeKedaluwarsaDitolak() {
		String salt = OtpCodeHasher.newSalt();
		OtpChallenge challenge = new OtpChallenge("budi@example.com", OtpCodeHasher.hash(salt, "123456"), salt,
				Instant.now().minusSeconds(10));
		when(otpRepository.findFirstByEmailAndStatusOrderByCreatedAtDesc("budi@example.com", OtpStatus.ACTIVE))
				.thenReturn(Optional.of(challenge));

		assertThatThrownBy(() -> otpService.verify("budi@example.com", "123456"))
				.isInstanceOf(OtpVerificationException.class);
		verify(otpRepository).updateStatus(challenge.getId(), OtpStatus.EXPIRED);
	}
}
