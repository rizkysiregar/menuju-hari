package com.menujuhari.media.application;

public class PhotoTooLargeException extends RuntimeException {

	public PhotoTooLargeException() {
		super("Ukuran foto melebihi batas");
	}
}
