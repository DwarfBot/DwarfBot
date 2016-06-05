package Code;

public class TilesetDetected {
	
	private int basex;
	private int basey;
	private Tileset tileset;
	private int matchCount;
	
	public TilesetDetected(int basex_, int basey_, Tileset tileset_, int matchCount_) {
		basex = basex_;
		basey = basey_;
		tileset = tileset_;
		matchCount = matchCount_;
	}
	
	public void setMatchCount(int count) {
		matchCount = count;
	}
	
	public int getBasex() {
		return basex;
	}
	
	public int getBasey() {
		return basey;
	}
	
	public Tileset getTileset() {
		return tileset;
	}

	public int getMatchCount() {
		return matchCount;
	}
}
