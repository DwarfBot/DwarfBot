package Code;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * 
 * @author ErnieParke
 * 
 * This code converts an DF image from one tileset to another tileset.
 * 
 * It can also be used to take an image and render some DF pop art. This
 * mode, toggled by setting artistic to true in init(), can take awhile.
 * 
 * Some example images to convert are at Resources/ImageXXX.png
 * 
 */

public class Main {

	private static String IMAGE_IMPORT_PATH;//The location of the image to be converted.
	private static String IMAGE_EXPORT_PATH;//The location of the converted image to be converted.
	
	private static int threshold;
	private static boolean artistic;//Changes parsing slightly for making artistic DF pieces
	
	public static void init() {
		//IMAGE_IMPORT_PATH = "/Image_Vidumec15x15.png";
		IMAGE_IMPORT_PATH = "/e.png";
		IMAGE_EXPORT_PATH = "/Converted.png";
		threshold = 40;
		artistic = false;
	}
	
	public static void main(String [] args) {
		init();
		//findTileset("Vidum");
		//refreshTilesets();
		convertImage(57);
		//14 anikki 8x8
		//57 vidume 15x15 Uses alpha
		//112 - Good for rendering too.
	}
	
	@SuppressWarnings("unused")
	private static void refreshTilesets() {
		TilesetManager bot = new TilesetManager();
		bot.refreshTilesets();
	}
	
	@SuppressWarnings("unused")
	private static void printTileset(int ID) {
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();
		
		for (int i = 0; i < tilesets.size(); i++) {
			if (i == ID) {
				System.out.println(tilesets.get(ID));
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static void findTileset(String author) {
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();
		
		for (int i = 0; i < tilesets.size(); i++) {
			Tileset tileset = tilesets.get(i);
			if (tileset.getAuthor().contains(author)) {
				System.out.println(i);
			}
		}
	}
	
	private static void convertImage(int tilesetIDConvertTo) {
		int numTilesetsToCheck = 1;//How many tilesets are we checking against?
		
		//Load in tilesets
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();
		ArrayList<Integer> tilesetMatchCount = new ArrayList<Integer>();//How closely does a tileset match the image to convert?
		int bestTilesetMatch = 0;
		ArrayList<Integer> basexList = new ArrayList<Integer>();
		ArrayList<Integer> baseyList = new ArrayList<Integer>();
		
		//Read in our image.
		BufferedImage toConvert = loadImage(IMAGE_IMPORT_PATH);
		
		//Neccessary data management
		Random rng = new Random();
		
		int offsetx = 0;
		int offsety = 0;
		int tilesetID;

		//The way I detect tilesets:
		//I check each tileset one by one.
		//I make multiple attempts to match a tileset, in case of error.
		//I do not expect the tile grid to line up with the edges of the image, so I vary the tile starting position, using offsetx and offsety.
		//I check every tile in the tileset to see if it matches.
		//If the tile from the tileset is the same color, I abandon the attempt. It leads to false positives in black areas.
		for (tilesetID = 0; tilesetID < numTilesetsToCheck; tilesetID++) {
			System.out.println(tilesetID);
			tilesetMatchCount.add(0);//0 matches so far. Will update later if any matches found.
			
			//Load tileset.
			Tileset tileset = tilesets.get(tilesetID);
			System.out.println(tileset.getImagePath());
			BufferedImage tilesetImg = loadImage("/Tilesets" + tileset.getImagePath());
			
			//Read in some vars.
			int tileWidth = tileset.getTileWidth();
			int tileHeight = tileset.getTileHeight();
			
			//Does the tileset use alpha or a pink background?
			boolean tilesetUsesAlpha = false;
			BufferedImage tileImg = tilesetImg.getSubimage(0, 2*tileHeight, tileWidth, tileHeight);

			Color c = new Color(tileImg.getRGB(0, 0), true);
			tilesetUsesAlpha = c.getAlpha() != 255;
			
			boolean tilesetMatches = false;
			
			int x = 0;
			int y = 0;
			
		TestTileset:
			for (double attempts = 0; attempts < 5 && !tilesetMatches; attempts++) {
				if ( toConvert.getWidth() < tileWidth || toConvert.getWidth() < tileHeight ) {
					break TestTileset;//Tiles from this tileset cannot fit inside the image.
				}
				if ( toConvert.getWidth() < 2*tileWidth ) {
					//The image is not two tiles wide. Be conservative.
					x = 0;
				} else {
					x = rng.nextInt(toConvert.getWidth() - 2*tileWidth);
				}
				if ( toConvert.getWidth() < tileHeight*2 ) {
					//The image is not two tiles tall. Be conservative.
					y = 0;
				} else {
					y = rng.nextInt(toConvert.getHeight() - 2*tileHeight);
				}
				
				int spaceAllotedX = Math.min(toConvert.getWidth() - tileWidth, tileWidth);//How much space can we vary 
				
			TestAttempt:
				for (offsetx = 0; offsetx < spaceAllotedX; offsetx++) {
					int spaceAllotedY = Math.min(toConvert.getHeight() - tileHeight, tileHeight);
					for (offsety = 0; offsety < spaceAllotedY; offsety++) {
						//Check each tile in the tileset.
						for (int tile = 0; tile < 256; tile++) {
							int tileCol = tile%16;
							int tileRow = (tile - tileCol)/16;
							tileImg = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);
							
							BufferedImage sampleImg = toConvert.getSubimage(x + offsetx, y + offsety, tileWidth, tileHeight);
							
							//Check if sampleImg is all black. If true, ignore with minor attempt penalty.
							//The reason I do it here and not later is because the checkSimlarity method only returns a boolean
							boolean sameColor = true;
							Color baseColor = null;
						checkTileSameColor:
							for (int col = 0; col < tileWidth; col++) {
								for (int row = 0; row < tileHeight; row++) {
									Color sampleC = new Color(sampleImg.getRGB(col, row), true);
									if (baseColor == null) {
										baseColor = new Color(sampleC.getRed(), sampleC.getGreen(), sampleC.getBlue(), sampleC.getAlpha());
									} else if (!baseColor.equals(sampleC)) {
										sameColor = false;
										break checkTileSameColor;
									}
								}
							}
							if (sameColor) {
								attempts -= 0.6;//0.4 attempt penalty
								//saveImage(sampleImg, "Resources/ImageAA" + Math.floor(attempts) + ".png");
								break TestAttempt;
							} else {
								//saveImage(sampleImg, "Resources/ImageBB" + Math.floor(attempts) + ".png");
							}
							
							//Does it match?
							if (checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha) != null) {
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
				basexList.add(basex);
				baseyList.add(basey);
				
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
				
				tilesetMatchCount.set(tilesetID, numMatches);
				if (numMatches == 0) {
					//Contradictory if no matches found when one match is confirmed. Faulty code above!!
					throw new Error();
				}

			} else {
				//Tileset does not match
				basexList.add(0);
				baseyList.add(0);
			}
		}
		
		//Find tileset with most matched area.
		for (tilesetID = 0; tilesetID < numTilesetsToCheck; tilesetID++) {
			Tileset tileset = tilesets.get(tilesetID);
			Tileset best = tilesets.get(bestTilesetMatch);
			double tileSize = tileset.getTileWidth()*tileset.getTileHeight();
			double tileSize2 = best.getTileWidth()*best.getTileHeight();
			if ( tilesetMatchCount.get(tilesetID)*tileSize > tilesetMatchCount.get(bestTilesetMatch)*tileSize2 ) {
				bestTilesetMatch = tilesetID;
			}
		}
		
		if (tilesetMatchCount.get(bestTilesetMatch) == 0) {
			System.out.println("No suitable match found.");
			return;
		}
		
		//Now that I know the tileset of the image, decode the image into its colors and tile id's
		DecodedImage decoded = readTiles(toConvert, tilesets, basexList.get(bestTilesetMatch), baseyList.get(bestTilesetMatch), bestTilesetMatch);

		//Re-render the image with the new tileset
		exportRenderedImage(decoded, tilesets, bestTilesetMatch, "Resources" + "/Decoded.png");
		exportRenderedImage(decoded, tilesets, tilesetIDConvertTo, "Resources" + IMAGE_EXPORT_PATH);
		//exportRenderedImage(tiles, tilesets, tilesetConvertTo, convertTileWidth, convertTileHeight,  "Resources" + IMAGE_EXPORT_PATH);
	}
	
	private static DecodedImage readTiles(BufferedImage toConvert, ArrayList<Tileset> tilesets, int basex, int basey, int tilesetID) {
		Tileset tileset = tilesets.get(tilesetID);//The tileset you think this image is in.
		BufferedImage tilesetImg = loadImage("/Tilesets" + tileset.getImagePath());//Get its image.
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
			if (col%5 == 0) {
				System.out.println(((double)col/convertTileWidth) + "%");
			}
			for (int row = 0; row < convertTileHeight; row++) {
				BufferedImage sampleImg = toConvert.getSubimage(basex + col*tileWidth, basey + row*tileHeight, tileWidth, tileHeight);
				if (artistic) {
					threshold = 10;
				} else {
					threshold = 5;
				}
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
	
	private static void exportRenderedImage(DecodedImage decoded, ArrayList<Tileset> tilesets, int tilesetConvertTo, String exportPath) {
		//Read in decoded info.
		ArrayList<Tile> tiles = decoded.getTiles();
		int convertTileWidth = decoded.getTilesWide();
		int convertTileHeight = decoded.getTilesTall();
		
		//Data management
		int tilesetID = tilesetConvertTo;//What tileset am I converting to.
		Tileset tileset = tilesets.get(tilesetID);//Get that tileset.
		BufferedImage tilesetImg = loadImage("/Tilesets" + tileset.getImagePath());//And its image.
		int tileWidth = tileset.getTileWidth();//How wide are the tiles? Pixels
		int tileHeight = tileset.getTileHeight();//How tall are the tiles?
		System.out.println("Render to tileset:" + tileset.getAuthor() + ":" + tileset.getImagePath());//Handy.
		
		boolean tilesetUsesAlpha = false;//Does the tileset use alpha? This affects rendering.
		BufferedImage tileImg = tilesetImg.getSubimage(0, 2*tileHeight, tileWidth, tileHeight);
		Color c = new Color(tileImg.getRGB(0, 0), true);
		tilesetUsesAlpha = c.getAlpha() != 255;
		
		//Set up image to write to
		BufferedImage output = new BufferedImage(convertTileWidth*tileWidth, convertTileHeight*tileHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = output.createGraphics();
		g2.setColor(Color.BLACK);//Fill with black. Touche.
		g2.fill(new Rectangle(0, 0, 1000, 1000));
		
		for (int col = 0; col < convertTileWidth; col++) {
			for (int row = 0; row < convertTileHeight; row++) {
				final Color PINK = new Color(255, 0, 255);
				Tile tile = tiles.get(col*convertTileHeight + row);
				
				if (tile != null) {
					//Grab tile
					int tileID = tile.getID();
					//System.out.println(tileID + ":" + tile.getForegroundColor() + ":" + tile.getBackgroundColor());
					int tileCol = tileID%16;
					int tileRow = (tileID - tileCol)/16;
					tileImg = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);
					
					for (int x = 0; x < tileWidth; x++) {
						for (int y = 0; y < tileHeight; y++) {
							
							//Grab tile color
							Color tileC = new Color(tileImg.getRGB(x,  y), true);
							Color toDraw;
							
							if (tilesetUsesAlpha) {
								//The tileset uses alpha.
								toDraw = Color.BLACK;
									double alpha = tileC.getAlpha()/255.0;//1.0 = foreground, 0.0 = background
									
									Color foreground = tile.getForegroundColor();
									Color background = tile.getBackgroundColor();
									
									//The hue and saturation of the tileset tile affect the dimness of the foreground color.
									//The greater the difference in the hue of the foreground and tile colors, the duller the rendered color.
									//The more saturated the tile, the greater the effect.
									double fgDegradation = 0.0;
									float[] hsv = new float[3];//Hue = 0, Saturation = 1, Value = 2
									Color.RGBtoHSB(tileC.getRed(),tileC.getGreen(),tileC.getGreen(),hsv);
									double tileHue = hsv[0];//0...360
									double tileSaturation = hsv[1];//0.0...1.0
									double transparency = hsv[2];//0.0...1.0
									Color.RGBtoHSB(foreground.getRed(),foreground.getGreen(),foreground.getBlue(),hsv);
									double foregroundHue = hsv[0];//0...360
									double hueDifference = Math.abs(tileHue - foregroundHue);
									hueDifference = Math.min(hueDifference, 360-hueDifference);
									fgDegradation = (hueDifference/180.0)*tileSaturation;//1 = full degradation, 0 = no effect

									Color normal = new Color((int)((foreground.getRed()*transparency*(1 - fgDegradation))*alpha + (background.getRed())*(1-alpha)),
											(int)((foreground.getGreen()*transparency*(1 - fgDegradation))*alpha + (background.getGreen())*(1-alpha)),
											(int)((foreground.getBlue()*transparency*(1 - fgDegradation))*alpha + (background.getBlue())*(1-alpha)));//I think this is how colors are rendered.
									toDraw = normal;
							} else {
								//The tileset does not use alpha.
								if (tileC.equals(PINK)) {
									toDraw = tile.getBackgroundColor();
								} else {
									boolean tileCisGrey = Math.abs(tileC.getRed()-tileC.getBlue()) < 2 && Math.abs(tileC.getRed() - tileC.getGreen()) < 2;
									if (tileCisGrey) {
										double transparency = tileC.getRed()/255.0;
										toDraw = new Color((int)(tile.getForegroundColor().getRed()*transparency),
												(int)(tile.getForegroundColor().getGreen()*transparency),
												(int)(tile.getForegroundColor().getBlue()*transparency));
									} else {
										toDraw = new Color(tileC.getRed(), tileC.getGreen(), tileC.getBlue());
									}
								}
							}
							g2.setColor(toDraw);
							g2.fill(new Rectangle(col*tileWidth + x, row*tileHeight + y, 1, 1));
						}
					}
				}
			}
		}
		
		g2.dispose();
		
		saveImage(output, exportPath);
	}
	
	private static Tile checkSimilarity(BufferedImage sampleImg, BufferedImage tileImg, boolean tilesetUsesAlpha) {
		return checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha, false);
	}
	
	private static Tile checkSimilarity(BufferedImage sampleImg, BufferedImage tileImg, boolean tilesetUsesAlpha, boolean meh) {
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
								if (Math.abs(backgroundColor.getRed() - sampleC.getRed()) < threshold && 
										Math.abs(backgroundColor.getGreen() - sampleC.getGreen()) < threshold &&
										Math.abs(backgroundColor.getBlue() - sampleC.getBlue()) < threshold) {
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
								if (Math.abs(transparency*foregroundColor.getRed() - sampleC.getRed()) < threshold && 
										Math.abs(transparency*foregroundColor.getGreen() - sampleC.getGreen()) < threshold &&
										Math.abs(transparency*foregroundColor.getBlue() - sampleC.getBlue()) < threshold) {
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
	
	public static BufferedImage loadImage(String path) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(Main.class.getResourceAsStream(path));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Err");
			e.printStackTrace();
		}

		return image;
	}
	
	private static void saveImage(BufferedImage img, String title) {
		try {
		    // retrieve image
		    BufferedImage bi = img;
		    File outputfile = new File(title);
		    outputfile.mkdirs();
		    ImageIO.write(bi, "png", outputfile);
		} catch (IOException e) {
		    e.printStackTrace();
		}
	}
}
