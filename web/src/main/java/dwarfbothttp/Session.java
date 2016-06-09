package dwarfbothttp;

import Code.Tileset;
import Code.TilesetManager;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import spark.Request;

/**
 * @author Jacob Sims
 */
public class Session {
	private static ArrayList<Tileset> supportedTilesets; // Use one set of these for all sessions.

	private boolean live;
	private LiveSession liveSession;
	private ArchivedSession archivedSession;

	public Session(String _id) {
		live = true;
		liveSession = new LiveSession();
		liveSession.setId(_id);
	}

	public Session(LiveSession _liveSession) {
		live = true;
		liveSession = _liveSession;
	}

	public Session(ArchivedSession _archivedSession) {
		live = false;
		archivedSession = _archivedSession;
	}

	public void setImageFromUpload(Request request) throws UploadFailedException {
		if (!live) {
			unarchive();
		}
		liveSession.setImageFromUpload(request);
	}

	public void unarchive() {
		if (!live) {
			try {
				liveSession = archivedSession.convertToLive();
			} catch (UnarchiveFailedException e) {
				e.printStackTrace();
				throw new Error("Could not unarchive a session. This should have been tested at the beginning " +
						"of the program, but if you touched the files while the program was running, that would " +
						"cause this error.", e);
			}
			archivedSession = null;
			live = true;
		}
	}

	public boolean archive() {
		// Returns true if the archive was successful.
		if (!live) {
			return true;
		}
		try {
			archivedSession = ArchivedSession.convertFromLive(liveSession);
			live = false;
			liveSession = null;
			return true;
		} catch (UnarchiveFailedException e) {
			// There was an internal error or a file was tampered with during execution.
			// However, this Session will still work; it just won't be archived.
			e.printStackTrace();
			return false;
		} catch (IllegalStateException e) {
			// It could not be archived because the session is still decoding.
			return false;
		}
	}

	public void startDecoding() {
		if (live) {
			liveSession.startDecoding();
		}
		// Archived sessions only exist if they have already finished decoding.
	}

	public String statusJson() {
		if (live) {
			return liveSession.statusJson();
		} else {
			return archivedSession.statusJson();
		}
	}

	public BufferedImage getToConvert() {
		if (!live) {
			unarchive();
		}
		return liveSession.getToConvert();
	}

	public boolean isDecodingFinished() {
		if (!live) {
			return true;
		}
		return liveSession.isDecodingFinished();
	}

	public BufferedImage renderToTileset(Tileset tileset) {
		if (!live) {
			unarchive();
		}
		return liveSession.renderToTileset(tileset);
	}

	public FailureReport createFailureReport() {
		if (!live) {
			unarchive();
		}
		return liveSession.createFailureReport();
	}

	public static Tileset tilesetWithPath(String path) {
		for (Tileset supportedTileset : supportedTilesets) {
			if (supportedTileset.getImagePath().equals(path)) {
				return supportedTileset;
			}
		}
		return null;
	}

	public String getId() {
		if (live) {
			return liveSession.getId();
		} else {
			return archivedSession.getId();
		}
	}

	public static ArrayList<Tileset> getSupportedTilesets() {
		return supportedTilesets;
	}

	static {
		TilesetManager tilesetManager = new TilesetManager();
		supportedTilesets = tilesetManager.getTilesets();
	}
}
