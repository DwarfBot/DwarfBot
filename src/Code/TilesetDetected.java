package Code;

public class TilesetDetected {
	
	int basex;
	int basey;
	int tilesetID;
	
	public TilesetDetected(int basex_, int basey_, int tilesetID_) {
		basex = basex_;
		basey = basey_;
		tilesetID = tilesetID_;
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
}
