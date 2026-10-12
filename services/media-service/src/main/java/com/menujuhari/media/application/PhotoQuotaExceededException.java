package com.menujuhari.media.application;

public class PhotoQuotaExceededException extends RuntimeException {

	public PhotoQuotaExceededException() {
		super("Kuota foto terlampaui");
	}
}
