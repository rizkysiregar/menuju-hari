package com.menujuhari.media.application;

public class TrackNotFoundException extends RuntimeException {

	public TrackNotFoundException() {
		super("Track musik tidak ditemukan");
	}
}
