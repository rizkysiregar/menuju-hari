package com.menujuhari.media.application;

public class PhotoInvalidException extends RuntimeException {

	public PhotoInvalidException() {
		super("File bukan gambar yang valid");
	}
}
