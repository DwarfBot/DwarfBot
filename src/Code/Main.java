package Code;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.*;

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
		IMAGE_IMPORT_PATH = "/Image_Anikki8x8.png";
		IMAGE_EXPORT_PATH = "/Converted.png";
		threshold = 40;
		artistic = false;
	}
	
	public static void main(String [] args) {
		init();
		//printTileset(22);
		//findTileset("Phoebus");
		//refreshTilesets();
		convertImage(0);
		//14 anikki 8x8 Nice solid tileset
		//57 vidume 15x15 Uses alpha
		//112 - Lemunde, uses alpha, good for rendering
		//114 - Phoebus
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
	private static void findTileset(String identifier) {
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();
		
		for (int i = 0; i < tilesets.size(); i++) {
			Tileset tileset = tilesets.get(i);
			if (tileset.getAuthor().contains(identifier) || tileset.getImagePath().contains(identifier)) {
				System.out.println(i);
			}
		}
	}
	
	private static void convertImage(int tilesetIDConvertTo) {
		//Load in tilesets
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();
		
		//Read in our image.
		BufferedImage toConvert = loadImage(IMAGE_IMPORT_PATH);
		
		//Necessary data management

		TilesetDetected detected = extractTileset(tilesets, toConvert);
		
		//Now that I know the tileset of the image, decode the image into its colors and tile id's
		DecodedImage decoded = readTiles(toConvert, tilesets, detected.getBasex(), detected.getBasey(), detected.getTilesetID());

		//Re-render the image with the new tileset
		exportRenderedImage(toConvert, decoded, tilesets, tilesetIDConvertTo, "Resources" + IMAGE_EXPORT_PATH);
	}

	public static MatchObject matchObjectForTileset(Tileset tileset, Random rng, BufferedImage toConvert) {
		//I make multiple attempts to match a tileset, in case of error.
		//I do not expect the tile grid to line up with the edges of the image, so I vary the tile starting position, using offsetx and offsety.
		//I check every tile in the tileset to see if it matches.
		//If the tile from the tileset is the same color, I abandon the attempt. It leads to false positives in black areas.
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

		int offsetx = 0;
		int offsety = 0;

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

			return new MatchObject(numMatches, basex, basey);

		} else {
			//Tileset does not match
			return new MatchObject(0, 0, 0);
		}
	}

	private static Callable<ArrayList<MatchObject>> threadCallableForTilesetRange(int start, int length, ArrayList<Tileset> allTilesets, BufferedImage toConvert) {
		return new ThreadCallable(start, length, allTilesets, toConvert);
	}

	private static TilesetDetected extractTileset( ArrayList<Tileset> tilesets, BufferedImage toConvert ) throws Error {
		int numTilesetsToCheck = tilesets.size();//How many tilesets are we checking against?
		
		ArrayList<Integer> tilesetMatchCount = new ArrayList<Integer>();//How closely does a tileset match the image to convert? This counts the number of matching tiles.
		Random rng = new Random();
		
		ArrayList<Integer> basexList = new ArrayList<Integer>();//The offsetx where a tile match was detected.
		ArrayList<Integer> baseyList = new ArrayList<Integer>();//The offsety where a tile match was detected.
		
		//The way I detect tilesets:
		//I check each tileset one by one.

		int numThreads = Math.min(8, numTilesetsToCheck / 2);
		int typicalLength = (int)Math.ceil((double)numTilesetsToCheck / numThreads);
		ExecutorService pool = Executors.newFixedThreadPool(numThreads);
		ArrayList<Future<ArrayList<MatchObject>>> futures = new ArrayList<>();
		for (int i = 0; i < numThreads; i++) {
			int index = Math.min(numTilesetsToCheck - 1, i * typicalLength);
			int length = Math.max(0, Math.min(numTilesetsToCheck - index, typicalLength));
			System.out.println("Creating thread with index " + index + ", length " + length);
			futures.add(pool.submit(threadCallableForTilesetRange(index, length, tilesets, toConvert)));
		}

		for (Future<ArrayList<MatchObject>> future : futures) {
			ArrayList<MatchObject> list;
			try {
				list = future.get();
			} catch (Exception e) {
				throw new Error("A thread failed. This shouldn't happen under normal usage.");
			}
			for (MatchObject matchObject : list) {
				basexList.add(matchObject.getBasex());
				baseyList.add(matchObject.getBasey());
				tilesetMatchCount.add(matchObject.getMatchCount());
			}
		}
		pool.shutdown();

		//Find tileset with most matched area.
		int bestTilesetMatch = 0;
		for (int tilesetID = 1; tilesetID < numTilesetsToCheck; tilesetID++) {
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
			return null;
		}
		
		return new TilesetDetected(basexList.get(bestTilesetMatch), baseyList.get(bestTilesetMatch), bestTilesetMatch);
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
				System.out.println((100.0*col/convertTileWidth) + "%");
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
	
	private static void exportRenderedImage(BufferedImage toConvert, DecodedImage decoded, ArrayList<Tileset> tilesets, int tilesetConvertTo, String exportPath) {
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
									float transparency;
									
									Color foreground = tile.getForegroundColor();
									Color background = tile.getBackgroundColor();
									
									float[] hsv = new float[3];//Hue = 0, Saturation = 1, Value = 2
									Color.RGBtoHSB(tileC.getRed(), tileC.getGreen(), tileC.getBlue(), hsv);
									float tileHue = hsv[0];//0.0...1.0
									transparency = hsv[2];//0.0...1.0
									float[] hsv2 = new float[3];
									Color.RGBtoHSB(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), hsv2);
									float foregroundHue = hsv2[0];//0.0...1.0
									float hueDifference = Math.abs(tileHue - foregroundHue);
									hueDifference = Math.min(hueDifference, 1-hueDifference);
									
									//Dwarf Fortress a slightly tints the colors of the tiles.
									double average = transparency*255.0;
									int redBoost = (int)(Math.min(tileC.getRed()-average, 25)*(foreground.getRed()/255.0));
									int greenBoost = (int)(Math.min(tileC.getGreen()-average, 25)*(foreground.getGreen()/255.0));
									int blueBoost = (int)(Math.min(tileC.getBlue()-average, 25)*(foreground.getBlue()/255.0));

									int red = (int)(((foreground.getRed() + redBoost)*transparency)*alpha + (background.getRed())*(1.0-alpha));
									int green = (int)(((foreground.getGreen() + greenBoost)*transparency)*alpha + (background.getGreen())*(1.0-alpha));
									int blue = (int)(((foreground.getBlue() + blueBoost)*transparency)*alpha + (background.getBlue())*(1.0-alpha));
									toDraw = new Color(Math.min(Math.max(red, 0), 255),
											Math.min(Math.max(green, 0), 255),
											Math.min(Math.max(blue, 0), 255));//I think this is how colors are rendered.
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
