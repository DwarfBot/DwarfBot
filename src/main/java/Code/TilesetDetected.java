package Code;

public class TilesetDetected {
	
	private int basex;
	private int basey;
	private int tilesetID;
	private int matchCount;
	
	public TilesetDetected(int basex_, int basey_, int tilesetID_, int matchCount_) {
		basex = basex_;
		basey = basey_;
		tilesetID = tilesetID_;
		matchCount = matchCount_;
	}
	
	public int getBasex() {
		return basex;
	}
	
	public int getBasey() {
		return basey;
	}
	
	public int getTilesetID() {
		return tilesetID;
	}

	public int getMatchCount() {
		return matchCount;
	}
}
