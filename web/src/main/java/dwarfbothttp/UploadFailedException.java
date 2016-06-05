package dwarfbothttp;

/**
 * @author Jacob Sims
 */
public class UploadFailedException extends Exception {
	public UploadFailedException() {}

	public UploadFailedException(String message) {
		super(message);
	}

	public UploadFailedException(Throwable cause) {
		super(cause);
	}

	public UploadFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
