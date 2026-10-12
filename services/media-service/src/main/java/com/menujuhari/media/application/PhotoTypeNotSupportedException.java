package com.menujuhari.media.application;

public class PhotoTypeNotSupportedException extends RuntimeException {

	public PhotoTypeNotSupportedException() {
		super("Tipe foto tidak didukung (hanya JPEG/PNG)");
	}
}
