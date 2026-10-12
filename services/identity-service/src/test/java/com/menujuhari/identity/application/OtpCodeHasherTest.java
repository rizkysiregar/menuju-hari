package com.menujuhari.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OtpCodeHasherTest {

	@Test
	void saltHarusBerbentukHex32Karakter() {
		String salt = OtpCodeHasher.newSalt();
		assertThat(salt).hasSize(32).matches("[0-9a-f]{32}");
	}

	@Test
	void hashDeterministikDanBedaUntukInputBeda() {
		String h1 = OtpCodeHasher.hash("abc", "123456");
		String h2 = OtpCodeHasher.hash("abc", "123456");
		String h3 = OtpCodeHasher.hash("abd", "123456");

		assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(h1).isNotEqualTo(h3);
	}

	@Test
	void matchesMenghasilkanTrueHanyaUntukKodeYangBenar() {
		String salt = OtpCodeHasher.newSalt();
		String hash = OtpCodeHasher.hash(salt, "042913");

		assertThat(OtpCodeHasher.matches("042913", salt, hash)).isTrue();
		assertThat(OtpCodeHasher.matches("042914", salt, hash)).isFalse();
		assertThat(OtpCodeHasher.matches("042913", "salt-bedas", hash)).isFalse();
	}
}
