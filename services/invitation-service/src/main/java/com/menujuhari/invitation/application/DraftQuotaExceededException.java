package com.menujuhari.invitation.application;

public class DraftQuotaExceededException extends RuntimeException {

	public DraftQuotaExceededException() {
		super("Kuota draft aktif habis");
	}
}
