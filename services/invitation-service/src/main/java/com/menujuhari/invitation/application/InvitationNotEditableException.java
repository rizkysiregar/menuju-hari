package com.menujuhari.invitation.application;

public class InvitationNotEditableException extends RuntimeException {

	public InvitationNotEditableException() {
		super("Invitation tidak dapat diedit pada status ini");
	}
}
