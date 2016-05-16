package Code;

import java.util.ArrayList;

public class DecodedImage {
	
	ArrayList<Tile> tiles;
	int tilesWide;
	int tilesTall;
	
	public DecodedImage(ArrayList<Tile> tiles_, int tilesWide_, int tilesTall_) {
		tiles = tiles_;
		tilesWide = tilesWide_;
		tilesTall = tilesTall_;
	}
	
	public ArrayList<Tile> getTiles() {
		return tiles;
	}
	
	public int getTilesWide() {
		return tilesWide;
	}
	
	public int getTilesTall() {
		return tilesTall;
	}
}
