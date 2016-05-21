package Code;

import java.awt.Color;

public class Tile {
	
	Color backgroundColor;
	Color foregroundColor;
	int id;
	
	public Tile(Color backgroundColor_, Color foregroundColor_) {
		backgroundColor = backgroundColor_;
		foregroundColor = foregroundColor_;
	}
	
	public void setID(int id_) {
		id = id_;
	}
	
	public Color getForegroundColor() {
		return foregroundColor;
	}
	
	public Color getBackgroundColor() {
		return backgroundColor;
	}
	
	public int getID() {
		return id;
	}
}
