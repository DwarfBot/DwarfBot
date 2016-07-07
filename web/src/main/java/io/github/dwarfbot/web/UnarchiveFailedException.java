package io.github.dwarfbot.web;

/**
 * @author Jacob Sims
 */
public class UnarchiveFailedException extends Exception {
	public UnarchiveFailedException() {}

	public UnarchiveFailedException(String message) {
		super(message);
	}

	public UnarchiveFailedException(Throwable cause) {
		super(cause);
	}

	public UnarchiveFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
