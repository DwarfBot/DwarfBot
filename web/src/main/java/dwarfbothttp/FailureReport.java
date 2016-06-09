package dwarfbothttp;

import Code.TilesetDetected;

import java.awt.image.BufferedImage;

/**
 * @author Jacob Sims
 */
public class FailureReport {
	private int seedx;
	private int seedy;
	private String detectedTilesetPath;
	private BufferedImage toConvert;
	private String sessionId;

	public FailureReport(int _seedx, int _seedy, TilesetDetected _tilesetDetected, BufferedImage _toConvert, String _sessionId) {
		seedx = _seedx;
		seedy = _seedy;
		detectedTilesetPath = _tilesetDetected.getTileset().getImagePath();
		toConvert = _toConvert;
		sessionId = _sessionId;
	}

	@Override
	public String toString() {
		return
				"   Seed: " + seedx + ", " + seedy + "\n" +
				"Tileset: " + detectedTilesetPath;
	}

	public int getSeedx() {
		return seedx;
	}

	public int getSeedy() {
		return seedy;
	}

	public String getDetectedTilesetPath() {
		return detectedTilesetPath;
	}

	public BufferedImage getToConvert() {
		return toConvert;
	}

	public String getSessionId() {
		return sessionId;
	}
}
