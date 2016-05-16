package Code;

public class Tileset {
	
	String imgPath;
	String author;
	String nickname;
	String dateCreated;
	int tileWidth;
	int tileHeight;
	
	public Tileset(String imgPath_, String author_, String nickname_, String date, int twidth_, int theight_) {
		imgPath = imgPath_;
		author = author_;
		nickname = nickname_;
		dateCreated = date;
		tileWidth = twidth_;
		tileHeight = theight_;
	}
	
	public String getImagePath() {
		return imgPath;
	}
	
	public String getAuthor() {
		return author;
	}

	/**
	 * 
	 * @return The nickname of this tileset. Returns null if there is no nickname.
	 */
	public String getNickname() {
		return nickname;
	}
	
	public String getDateCreated() {
		return dateCreated;
	}
	
	public int getTileWidth() {
		return tileWidth;
	}
	
	public int getTileHeight() {
		return tileHeight;
	}
	
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj.getClass().equals(Tileset.class)) {
			Tileset obj2 = (Tileset)obj;
			if ((obj2.getAuthor().equalsIgnoreCase(author) && obj2.getDateCreated().equalsIgnoreCase(dateCreated)) || obj2.getNickname().equalsIgnoreCase(nickname)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String toString() {
		String toReturn = "";
		
		toReturn += "Tileset creator: " + author;
		if (nickname != null) {
			toReturn += " (nickname: " + nickname + ")";
		}
		toReturn += "\nCreated on: " + dateCreated;
		toReturn += "\nImage path: " + imgPath;
		toReturn += "\nTile size: " + tileWidth + "x" + tileHeight;
		
		return toReturn;
	}
}
