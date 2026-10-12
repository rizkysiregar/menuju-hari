package com.menujuhari.media.application;

public class MediaNotFoundException extends RuntimeException {

	public MediaNotFoundException() {
		super("Media tidak ditemukan");
	}
}
