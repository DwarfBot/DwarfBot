package Code;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by jacob on 2016-05-22.
 */
public class TileFitter {
	private ArrayList<Tileset> tilesets;
	private BufferedImage toConvert;
	private boolean artistic;
	private int similarityThreshold;

	public TileFitter(ArrayList<Tileset> _tilesets, boolean _artistic, BufferedImage _toConvert, int _similarityThreshold) {
		tilesets = _tilesets;
		artistic = _artistic;
		toConvert = _toConvert;
		similarityThreshold = _similarityThreshold;
	}

	/**
	 * Get match information from the tileset. This includes base coordinates and the quality of the match (the number
	 * of tiles that are similar to the ones shown in the image)
	 * @param tileset The tileset to retrieve information for.
	 * @param rng The random number generator to use.
	 * @return a TilesetDetected object with the match coordinates and quality.
	 */
	public TilesetDetected matchForTileset(final Tileset tileset, final Random rng) {
		//I make multiple attempts to match a tileset, in case of error.
		//I do not expect the tile grid to line up with the edges of the image, so I vary the tile starting position, using offsetx and offsety.
		//I check every tile in the tileset to see if it matches.
		//If the tile from the tileset is the same color, I abandon the attempt. It leads to false positives in black areas.
		BufferedImage tilesetImg = null;
		try {
			tilesetImg = tileset.loadImage();
		} catch (IOException e) {
			throw new Error("Could not load the tileset image.", e);
		}

		//Read in some vars.
		int tileWidth = tileset.getTileWidth();
		int tileHeight = tileset.getTileHeight();

		//Does the tileset use alpha or a pink background?
		boolean tilesetUsesAlpha = false;
		BufferedImage tileImg = tilesetImg.getSubimage(0, 2 * tileHeight, tileWidth, tileHeight);

		Color c = new Color(tileImg.getRGB(0, 0), true);
		tilesetUsesAlpha = c.getAlpha() != 255;

		boolean tilesetMatches = false;

		int x = 0;
		int y = 0;

		int offsetx = 0;
		int offsety = 0;

	TestTileset:
		for (double attempts = 0; attempts < 5 && !tilesetMatches; attempts++) {
			if (toConvert.getWidth() < tileWidth || toConvert.getWidth() < tileHeight) {
				break TestTileset; //Tiles from this tileset cannot fit inside the image.
			}
			if (toConvert.getWidth() < 2 * tileWidth) {
				//The image is not two tiles wide. Be conservative.
				x = 0;
			} else {
				x = rng.nextInt(toConvert.getWidth() - 2 * tileWidth);
			}
			if (toConvert.getWidth() < tileHeight*2) {
				//The image is not two tiles tall. Be conservative.
				y = 0;
			} else {
				y = rng.nextInt(toConvert.getHeight() - 2*tileHeight);
			}

			int spaceAllotedX = Math.min(toConvert.getWidth() - tileWidth, tileWidth);//How much space can we vary
			int spaceAllotedY = Math.min(toConvert.getHeight() - tileHeight, tileHeight);
			BufferedImage[] tileImages = new BufferedImage[256];
			for (int tile = 0; tile < 256; tile++) {
				int tileCol = tile%16;
				int tileRow = (tile - tileCol)/16;
				tileImages[tile] = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);
			}

		TestAttempt:
			for (offsetx = 0; offsetx < spaceAllotedX; offsetx++) {
				for (offsety = 0; offsety < spaceAllotedY; offsety++) {
					BufferedImage sampleImg = toConvert.getSubimage(x + offsetx, y + offsety, tileWidth, tileHeight);

					//Check if sampleImg is all black. If true, ignore with minor attempt penalty.
					//The reason I do it here and not later is because the checkSimlarity method only returns a boolean
					boolean sameColor = true;
					int baseColor = sampleImg.getRGB(0, 0);

				checkTileSameColor:
					for (int col = 0; col < tileWidth; col++) {
						for (int row = 0; row < tileHeight; row++) {
							int sampleC = sampleImg.getRGB(col, row);
							if (baseColor != sampleC) {
								sameColor = false;
								break checkTileSameColor;
							}
						}
					}
					if (sameColor) {
						attempts -= 0.6;//0.4 attempt penalty
						break TestAttempt;
					}

					//Check each tile in the tileset.
					for (int tile = 0; tile < 256; tile++) {
						//Does it match?
						if (checkSimilarity(sampleImg, tileImages[tile], tilesetUsesAlpha) != null) {
							tilesetMatches = true;


							break TestTileset;
						}
					}
				}
			}
		}

		if (tilesetMatches) {
			//See how closely the tileset matches the screenshot.
			int numMatches = 0;

			int basex = (x + offsetx)%tileWidth;
			int basey = (y + offsety)%tileHeight;

			for (int col = 0; col < (toConvert.getWidth()-basex)/tileWidth; col++) {
				for (int row = 0; row < (toConvert.getHeight()-basey)/tileHeight; row++) {
					BufferedImage sampleImg = toConvert.getSubimage(basex + col*tileWidth, basey + row*tileHeight, tileWidth, tileHeight);

					//Check each tile in the tileset.
				tileSearch:
					for (int tile = 0; tile < 256; tile++) {
						if (artistic) {
							//These tiles mess with art rendering.
							if (tile == 219 || tile == 0 || tile == 32 || tile == 158) {
								tile++;
							}
							if (tile == 176) {
								tile = 179;
							}
							if (tile == 255) {
								continue;
							}
						}
						int tileCol = tile%16;
						int tileRow = (tile - tileCol)/16;
						tileImg = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);

						if (checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha) != null) {
							numMatches++;
							break tileSearch;
						}
					}
				}
			}

			if (numMatches == 0) {
				//Contradictory if no matches found when one match is confirmed. Faulty code above!!
				throw new Error();
			}

			return new TilesetDetected(basex, basey, tileset, numMatches);

		} else {
			//Tileset does not match
			return new TilesetDetected(0, 0, tileset, 0);
		}
	}

	private Tile checkSimilarity(BufferedImage sampleImg, BufferedImage tileImg, boolean tilesetUsesAlpha) {
		//Check that two tile images are equal
		//TileImg - The tile image pulled from the tileset.
		//SampleImg - A section of image to compare.
		final Color PINK = new Color(255, 0, 255);

		Color backgroundColor = null;//The background color of the tile.
		Color foregroundColor = null;//The foreground color of the tile.
		//Check all pixels
		Color tileC = null;
		for (int x2 = 0; x2 < tileImg.getWidth(); x2++) {
			for (int y2 = 0; y2 < tileImg.getHeight(); y2++) {
				Color sampleC = new Color(sampleImg.getRGB(x2, y2));//From screenshot
				tileC = new Color(tileImg.getRGB(x2, y2), true);//From tileset

				if (tilesetUsesAlpha) {
					//Currently do not handle alpha tilesets because they are weird.
					return null;
				} else {
					if (tileC.equals(PINK)) {
						if (backgroundColor == null) {
							//Assign background color.
							backgroundColor = new Color(sampleC.getRed(), sampleC.getGreen(), sampleC.getBlue());
						} else {
							//Check for similarity
							if (artistic) {
								if (Math.abs(backgroundColor.getRed() - sampleC.getRed()) < similarityThreshold &&
										Math.abs(backgroundColor.getGreen() - sampleC.getGreen()) < similarityThreshold &&
										Math.abs(backgroundColor.getBlue() - sampleC.getBlue()) < similarityThreshold) {
									//All's good
								} else {
									return null;
								}
							} else {
								if (!sampleC.equals(backgroundColor)) {
									//The sample should be background color, but is not.
									return null;
								}
							}
						}
					} else {
						boolean tileCisGrey = tileC.getRed() == tileC.getBlue() && tileC.getRed() == tileC.getGreen();
						if (foregroundColor == null) {
							if (tileCisGrey) {
								//Assign foreground color.
								double transparency = 255.0/tileC.getRed();
								foregroundColor = new Color(Math.min((int)(transparency*sampleC.getRed()), 255),
										Math.min((int)(transparency*sampleC.getGreen()), 255),
										Math.min((int)(transparency*sampleC.getBlue()), 255));
								if (foregroundColor.equals(backgroundColor)) {
									//Shouldn't be the same.
									return null;
								}
							}
						} else {
							//Check for similarity
							if (tileCisGrey) {
								//Check that sampleS is scalar of foreground color based on how white tileC is

								double transparency = (double)tileC.getRed()/255;
								if (Math.abs(transparency*foregroundColor.getRed() - sampleC.getRed()) < similarityThreshold &&
										Math.abs(transparency*foregroundColor.getGreen() - sampleC.getGreen()) < similarityThreshold &&
										Math.abs(transparency*foregroundColor.getBlue() - sampleC.getBlue()) < similarityThreshold) {
									//All's good
								} else {
									//fail
									return null;
								}
							} else {
								if (!sampleC.equals(tileC)) {
									//The sample should be the tile color, but it is not.
									return null;
								}
							}
						}
					}
				}
			}
		}

		if (foregroundColor == null) {
			foregroundColor = Color.BLACK;//To handle some edge cases.
		}
		if (backgroundColor == null) {
			backgroundColor = Color.BLACK;//To handle some edge cases.
		}

		return new Tile(backgroundColor, foregroundColor);
	}

	public DecodedImage readTiles(int basex, int basey, Tileset tileset) {
		BufferedImage tilesetImg = null;
		try {
			tilesetImg = tileset.loadImage();
		} catch (IOException e) {
			throw new Error("Could not load the tileset.", e);
		}

		int threshold;
		if (artistic) {
			threshold = 10;
		} else {
			threshold = 5;
		}
		int tileWidth = tileset.getTileWidth();//How wide is a tile? Pixels.
		int tileHeight = tileset.getTileHeight();//How tall is a tile?
		System.out.println("Detected:" + tileset.getAuthor() + ":" + tileset.getImagePath());//It's kind of the program to think of us...

		boolean tilesetUsesAlpha = false;//Does the tileset use alpha? This affects how the tileset is rendered.
		BufferedImage tileImg = tilesetImg.getSubimage(0, 2*tileHeight, tileWidth, tileHeight);
		Color c = new Color(tileImg.getRGB(0, 0), true);
		tilesetUsesAlpha = c.getAlpha() != 255;

		ArrayList<Tile> tiles = new ArrayList<Tile>();//Set up some "storage" variables. This stores what tiles are found.
		int convertTileWidth = (toConvert.getWidth()-basex)/tileWidth;//How many tiles wide the image to be converted is.
		int convertTileHeight = (toConvert.getHeight()-basey)/tileHeight;//How many tiles tall the image to be converted is.

		for (int col = 0; col < convertTileWidth; col++) {
			if (col%((int)((convertTileWidth+1)*0.1)) == 0) {
				System.out.println((100.0*col/convertTileWidth) + "%");//Nice to see where we are in the algorithm.
			}
			for (int row = 0; row < convertTileHeight; row++) {
				BufferedImage sampleImg = toConvert.getSubimage(basex + col*tileWidth, basey + row*tileHeight, tileWidth, tileHeight);
				boolean tileFound = false;
				do {
				TestTiles:
					for (int tile = 0; tile < 256; tile++) {
						if (artistic) {
							//These tiles mess with art rendering.
							if (tile == 219 || tile == 0 || tile == 32 || tile == 158) {
								tile++;
							}
							if (tile == 176) {
								tile = 179;
							}
							if (tile == 255) {
								continue;
							}
						}
						int tileCol = tile%16;
						int tileRow = (tile - tileCol)/16;
						tileImg = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);

						Tile tileObj = checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha);

						if (tileObj != null) {
							//Prefer tile 32 or 219 over tile 0 if they match.
							if (tile == 0) {
								Color bg = tileObj.getBackgroundColor();
								if (bg.getRed() + bg.getGreen() + bg.getBlue() < 100) {
									//Anything dim enough is likely an unexplored tile.
									tileImg = tilesetImg.getSubimage(tileCol*tileWidth, (tileRow+2)*tileHeight, tileWidth, tileHeight);
									Tile temp = checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha);
									if (temp != null) {
										//Tile 32 does match
										tileObj = temp;
										tile = 32;
									}
								} else {
									//Anything bright enough is likely an explored tile.
									tileImg = tilesetImg.getSubimage(11*tileWidth, 13*tileHeight, tileWidth, tileHeight);
									Tile temp = checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha);
									if (temp != null) {
										//Tile 32 does match
										tileObj = temp;
										tile = 219;
									}
								}
							}
							tileObj.setID(tile);
							tiles.add(tileObj);
							tileFound = true;
							break TestTiles;
						} else if (tile == 255) {
							if (!artistic) {
								tiles.add(null);//On purpose
								tileFound = true;
								break TestTiles;
							}
						}
					}
					threshold += 10;
				} while (!tileFound);
			}
		}

		return new DecodedImage(tiles, convertTileWidth, convertTileHeight);
	}

	public ArrayList<Tileset> getTilesets() {
		return tilesets;
	}

	public BufferedImage getToConvert() {
		return toConvert;
	}

	public void setToConvert(BufferedImage toConvert) {
		this.toConvert = toConvert;
	}
}
