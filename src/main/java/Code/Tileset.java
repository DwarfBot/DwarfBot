package Code;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Tileset {
	
	private String imgPath;
	private String author;
	private String nickname;
	private String dateCreated;
	private int tileWidth;
	private int tileHeight;
	private int id;
	private boolean usesAlpha;

	public Tileset(String imgPath_, String author_, String nickname_, String date, int twidth_, int theight_, int id_, boolean usesAlpha_) {
		imgPath = imgPath_;
		author = author_;
		nickname = nickname_;
		dateCreated = date;
		tileWidth = twidth_;
		tileHeight = theight_;
		id = id_;
		usesAlpha = usesAlpha_;
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
	
	public int getID() {
		return id;
	}
	
	public boolean usesAlpha() {
		return usesAlpha;
	}

	public BufferedImage loadImage() throws IOException {
		return ImageIO.read(Main.class.getResourceAsStream("/Tilesets" + imgPath));
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

	public boolean usesAlpha() {
		BufferedImage tileImg = null;
		try {
			tileImg = loadImage().getSubimage(0, 2*tileHeight, tileWidth, tileHeight);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Color c = new Color(tileImg.getRGB(0, 0), true);
		return c.getAlpha() != 255;
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
