package Code;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/**
 * Created by jacob on 2016-05-22.
 */
public class TilesetFitter {
	private ArrayList<Tileset> tilesets;
	private boolean artistic;
	private int similarityThreshold;
	private BufferedImage toConvert;
	private BufferedImage sampleFromToConvert;
	private Logger logger;
	private static AtomicInteger numTilesetChecksComplete;

	public TilesetFitter(ArrayList<Tileset> _tilesets, boolean _artistic) {
		tilesets = _tilesets;
		artistic = _artistic;
		similarityThreshold = 10;
		logger = Logger.getLogger(Main.LOGGER_NAME);
	}
	
	public void loadImageForConverting(BufferedImage toConvert_) {
		toConvert = toConvert_;
		
		Random rng = new Random();
		int x;//Where to take a sample from.
		int y;//Where to take a sample from.
		if (toConvert.getWidth() < 96) {
			//The image is not two tiles wide. Be conservative.
			x = 0;
		} else {
			x = rng.nextInt(toConvert.getWidth() - 96);
		}
		if (toConvert.getWidth() < 96) {
			//The image is not two tiles tall. Be conservative.
			y = 0;
		} else {
			y = rng.nextInt(toConvert.getHeight() - 96);
		}
		sampleFromToConvert = toConvert.getSubimage(x, y, Math.min(toConvert.getWidth(),  96), Math.min(toConvert.getHeight(), 96));
		TilesetManager.saveImage(sampleFromToConvert, "Resources/SampleConvert.png");
	}
	
	public DecodedImage decodeImage() {
		long timeBeforeExtraction = System.currentTimeMillis();

		//What tileset is the image using?
		TilesetDetected detected = extractTileset();

		logger.log(Level.INFO, "Extraction time: " + (System.currentTimeMillis() - timeBeforeExtraction));

		//Decode the image into its tile colors and tile id's.
		return readTiles(detected.getBasex(), detected.getBasey(), detected.getTileset());
	}
	
	public TilesetDetected extractTileset() throws Error {
		ArrayList<Tileset> tilesets = getTilesets();
		int numTilesetsToCheck = tilesets.size();//tilesets.size();//How many tilesets are we checking against?

		ArrayList<Integer> tilesetMatchCount = new ArrayList<Integer>();//How closely does a tileset match the image to convert? This counts the number of matching tiles.

		ArrayList<Integer> basexList = new ArrayList<Integer>();//The offsetx where a tile match was detected.
		ArrayList<Integer> baseyList = new ArrayList<Integer>();//The offsety where a tile match was detected.

		// Before we start any other threads, make the AtomicInteger for progress information.
		numTilesetChecksComplete = new AtomicInteger(0);

		//The way I detect tilesets:
		//I check each tileset one by one.

		int numThreads = Math.min(8, numTilesetsToCheck / 2);
		int typicalLength = (int)Math.ceil((double)numTilesetsToCheck / numThreads);
		ExecutorService pool = Executors.newFixedThreadPool(numThreads);
		ArrayList<Future<ArrayList<TilesetDetected>>> futures = new ArrayList<>();
		for (int i = 0; i < numThreads; i++) {
			int index = Math.min(numTilesetsToCheck - 1, i * typicalLength);
			int length = Math.max(0, Math.min(numTilesetsToCheck - index, typicalLength));
			logger.log(Level.FINER, "Creating thread with index " + index + ", length " + length);
			futures.add(pool.submit(threadCallableForTilesetRange(index, length, this)));
		}
		pool.shutdown(); // This line means it will stop accepting new threads; it will not terminate existing ones

		// Give progress information.
		try {
			do {
				logger.log(Level.FINE, "Tileset checks " + 100.0 * numTilesetChecksComplete.get() / numTilesetsToCheck + "% complete.");
			} while (!pool.awaitTermination(1, TimeUnit.SECONDS));
		} catch (InterruptedException ie) {
			// We can safely ignore this and move on to the next code block, which will handle the error.
		}
		logger.log(Level.INFO, "Tileset checks now finished.");

		for (Future<ArrayList<TilesetDetected>> future : futures) {
			ArrayList<TilesetDetected> list;
			try {
				list = future.get();
			} catch (Exception e) {
				// Attempt to clean up remaining threads.
				pool.shutdownNow();
				e.printStackTrace();
				throw new Error("A thread failed. This shouldn't happen under normal usage.");
			}
			for (TilesetDetected tilesetDetected : list) {
				basexList.add(tilesetDetected.getBasex());
				baseyList.add(tilesetDetected.getBasey());
				tilesetMatchCount.add(tilesetDetected.getMatchCount());
			}
		}

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
			throw new Error("No suitable match found.");
		}

		Tileset best = tilesets.get(bestTilesetMatch);//Extract the tiles from the image.
		
		logger.log(Level.INFO, "Detected tileset: " + best.getImagePath());
		return new TilesetDetected(basexList.get(bestTilesetMatch), baseyList.get(bestTilesetMatch), tilesets.get(bestTilesetMatch), tilesetMatchCount.get(bestTilesetMatch));
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

		//Place to store tiles.
		BufferedImage[] tileImages = new BufferedImage[256];
		
	TestTileset:
		for (double attempts = 0; attempts < 4 && !tilesetMatches; attempts++) {
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
						if (tileImages[tile] == null) {
							//Load in image.
							int tileCol = tile%16;
							int tileRow = (tile - tileCol)/16;
							tileImages[tile] = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);
						}
						
						Tile tileObj = checkSimilarity(sampleImg, tileImages[tile], tilesetUsesAlpha, tile);
						if (tileObj != null) {
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

						if (checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha, tile) != null) {
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
	
	private Tile checkSimilarity(BufferedImage sampleImg, BufferedImage tileImg, boolean tilesetUsesAlpha, int id) {
		return checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha, id, false);
	}

	private Tile checkSimilarity(BufferedImage sampleImg, BufferedImage tileImg, boolean tilesetUsesAlpha, int id, boolean meh) {
		logger.log(Level.FINEST, "---");
		//Check that two tile images are equal
		//TileImg - The tile image pulled from the tileset.
		//SampleImg - A section of image to compare.

		Color backgroundC = null;//The background color of the tile.
		Color foregroundC = null;//The foreground color of the tile.
		//Check all pixels
		Color tileC = null;
		if (tileImg.getWidth() < 4 || tileImg.getHeight() < 4) {
			throw new Error("You should never have a tile this small.");
		}
		
		boolean performedCheck = false;//Require one check to be performed for positive signal to be sent. Only used when considering alpha tilesets.
		for (int x2 = 0; x2 < tileImg.getWidth(); x2++) {
			for (int y2 = 0; y2 < tileImg.getHeight(); y2++) {
				Color sampleC = new Color(sampleImg.getRGB(x2, y2));//From screenshot
				tileC = new Color(tileImg.getRGB(x2, y2), true);//From tileset

				boolean isPink = tileC.getRed() > 250 && tileC.getGreen() < 5 && tileC.getBlue() > 250;
				
				if (isPink && !tilesetUsesAlpha) {
					if (backgroundC != null) {
						//The sample color should be the same as the background color
						performedCheck = true;
						
						if (artistic) {
							if (Math.abs(backgroundC.getRed() - sampleC.getRed()) < similarityThreshold &&
									Math.abs(backgroundC.getGreen() - sampleC.getGreen()) < similarityThreshold &&
									Math.abs(backgroundC.getBlue() - sampleC.getBlue()) < similarityThreshold) {
								//All's good
							} else {
								return null;
							}
						} else {
							if (!sampleC.equals(backgroundC)) {
								return null;
							}
						}
					} else {
						backgroundC = new Color(sampleC.getRed(), sampleC.getGreen(), sampleC.getBlue());
					}
				} else {
					//Get some tileset values.
					double alpha = tileC.getAlpha()/255.0;//1.0 = foreground, 0.0 = background
					float transparency;//How much the foreground color mixes with black. 1.0 = full foreground, 0.0 = black.
	
					float[] hsv = new float[3];//Hue = 0, Saturation = 1, Value = 2
					Color.RGBtoHSB(tileC.getRed(), tileC.getGreen(), tileC.getBlue(), hsv);
					transparency = hsv[2];//Goes from 0.0 to 1.0.
					
					boolean attemptProcess = false;
	
					if (foregroundC == null) {
	
						/*
						* We need to guess the foreground color.
						* If the background color is known, this is easy.
						* If the alpha = 1.0, we can ignore the background color.
						*/
						if (alpha == 1.0 || (backgroundC != null && alpha != 0.0)) {
							double average = transparency*255.0;//The brightness of the tileset pixel. It's dubbed "average" because of how its used.
							Color backgroundC2;
							if (backgroundC == null) {
								backgroundC2 = new Color(2);//Just to avoid the null pointer exceptions.
							} else {
								backgroundC2 = new Color(backgroundC.getRed(), backgroundC.getGreen(), backgroundC.getBlue());//Clone the variable.
							}
		
							//Reverse engineer the render code.
							double redBoost = (1 + (tileC.getRed()-average)/255.0);
							double greenBoost = (1 + (tileC.getGreen()-average)/255.0);
							double blueBoost = (1 + (tileC.getBlue()-average)/255.0);
							int redGuess = (int)((sampleC.getRed() - (backgroundC2.getRed()*(1.0 - alpha)))/alpha/transparency/redBoost);
							int greenGuess = (int)((sampleC.getGreen() - (backgroundC2.getGreen()*(1.0 - alpha)))/alpha/transparency/greenBoost);
							int blueGuess = (int)((sampleC.getBlue() - (backgroundC2.getBlue()*(1.0 - alpha)))/alpha/transparency/blueBoost);
							foregroundC = new Color(Math.min(Math.max(redGuess, 0), 255), Math.min(Math.max(greenGuess, 0), 255), Math.min(Math.max(blueGuess, 0), 255));
							logger.log(Level.FINEST, "f:" + foregroundC.getRed() + ":" + foregroundC.getGreen() + ":" + foregroundC.getBlue());
						} else if ((alpha == 0.0 || transparency == 0.0) && backgroundC != null) {
							//The color only depends on the foreground. We can test that easily.
							attemptProcess = true;
						}
					}
					if (backgroundC == null) {
						
						/*
						 * We need to guess the background color.
						 * It's pretty much the same as guessing the foregroundColor, commented about above ^^.
						 * 
						 * One nice thing is that in this code, we are assured that foregroundC is not null.
						 */
						if (alpha == 0.0 || (foregroundC != null && alpha != 1.0) || (transparency == 0.0)) {
							double average = transparency*255.0;//The brightness of the tileset pixel. It's dubbed "average" because of how its used.
							Color foregroundC2;
							if (foregroundC == null) {
								foregroundC2 = new Color(2);//Just to avoid the null pointer exceptions.
							} else {
								foregroundC2 = new Color(foregroundC.getRed(), foregroundC.getGreen(), foregroundC.getBlue());//Clone the variable.
							}
							
							//Reverse engineer the render code.
							double redBoost = (1 + (tileC.getRed()-average)/255.0);
							double greenBoost = (1 + (tileC.getGreen()-average)/255.0);
							double blueBoost = (1 + (tileC.getBlue()-average)/255.0);
							int redGuess = (int)((sampleC.getRed() - ((foregroundC2.getRed()*(redBoost)*transparency*alpha)))/(1 - alpha));
							int greenGuess = (int)((sampleC.getGreen() - ((foregroundC2.getGreen()*(greenBoost)*transparency*alpha)))/(1 - alpha));
							int blueGuess = (int)((sampleC.getBlue() - ((foregroundC2.getBlue()*(blueBoost)*transparency*alpha)))/(1 - alpha));
							backgroundC = new Color(Math.min(Math.max(redGuess, 0), 255), Math.min(Math.max(greenGuess, 0), 255), Math.min(Math.max(blueGuess, 0), 255));
							logger.log(Level.FINEST, "f:" + backgroundC.getRed() + ":" + backgroundC.getGreen() + ":" + backgroundC.getBlue());
						} else if (alpha == 1.0 && foregroundC != null) {
							//The color only depends on the foreground. We can test that easily.
							attemptProcess = true;
						}
					}
					if ((foregroundC != null && backgroundC != null) || attemptProcess) {
						performedCheck = true;
						//We can make an educated guess if the tile and the image section match. Some leeway is given via the variable "threshold"
						Color toRender;
						if (foregroundC == null) {
							toRender = getRenderColor(new Color(2), backgroundC, tileC, tilesetUsesAlpha);
						} else if (backgroundC == null) {
							toRender = getRenderColor(foregroundC, new Color(2), tileC, tilesetUsesAlpha);
						} else {
							toRender = getRenderColor(foregroundC, backgroundC, tileC, tilesetUsesAlpha);
						}
						logger.log(Level.FINEST, "s:" + sampleC.getRed() + ":" + sampleC.getGreen() + ":" + sampleC.getBlue() + ":" + sampleC.getAlpha());

						if (Math.abs(toRender.getRed() - sampleC.getRed()) < similarityThreshold && 
								Math.abs(toRender.getGreen() - sampleC.getGreen()) < similarityThreshold &&
								Math.abs(toRender.getBlue() - sampleC.getBlue()) < similarityThreshold ) {
							//All's good.
						} else {
							return null;//Match failed.
						}
					}
				}
			}
		}
		
		if (!performedCheck && tilesetUsesAlpha) {
			return null;//Cannot safely say yes.
		}
		
		if (foregroundC == null) {
			foregroundC = Color.BLACK;//To handle some edge cases.
		}
		if (backgroundC == null) {
			backgroundC = Color.BLACK;//To handle some edge cases.
		}

		if (meh) {
			/*TilesetManager.saveImage(sampleImg, "Resources/Sample.png");
			TilesetManager.saveImage(tileImg, "Resources/Tile.png");*/
			
			//System.exit(0);
		}
		
		return new Tile(backgroundC, foregroundC);
	}

	public DecodedImage readTiles(int basex, int basey, Tileset tileset) {
		BufferedImage tilesetImg = null;
		try {
			tilesetImg = tileset.loadImage();
		} catch (IOException e) {
			throw new Error("Could not load the tileset.", e);
		}

		similarityThreshold = 10;
		
		int tileWidth = tileset.getTileWidth();//How wide is a tile? Pixels.
		int tileHeight = tileset.getTileHeight();//How tall is a tile?
		logger.log(Level.INFO, "Detected:" + tileset.getAuthor() + ":" + tileset.getImagePath());//It's kind of the program to think of us...

		boolean tilesetUsesAlpha = false;//Does the tileset use alpha? This affects how the tileset is rendered.
		BufferedImage tileImg = tilesetImg.getSubimage(0, 2*tileHeight, tileWidth, tileHeight);
		Color c = new Color(tileImg.getRGB(0, 0), true);
		tilesetUsesAlpha = c.getAlpha() != 255;

		ArrayList<Tile> tiles = new ArrayList<Tile>();//Set up some "storage" variables. This stores what tiles are found.
		int convertTileWidth = (toConvert.getWidth()-basex)/tileWidth;//How many tiles wide the image to be converted is.
		int convertTileHeight = (toConvert.getHeight()-basey)/tileHeight;//How many tiles tall the image to be converted is.

		for (int col = 0; col < convertTileWidth; col++) {
			if (col%((int)(Math.ceil((convertTileWidth+1)*0.1))) == 0) {
				logger.log(Level.FINE, (100.0*col/convertTileWidth) + "%");//Nice to see where we are in the algorithm.
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

						Tile tileObj = checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha, tile);//, row == 0 && col == 3);

						if (tileObj != null) {
							//Prefer tile 32 or 219 over tile 0 if they match.
							if (tile == 0) {
								Color bg = tileObj.getBackgroundColor();
								if (bg.getRed() + bg.getGreen() + bg.getBlue() < 100) {
									//Anything dim enough is likely an unexplored tile.
									tileImg = tilesetImg.getSubimage(tileCol*tileWidth, (tileRow+2)*tileHeight, tileWidth, tileHeight);
									Tile temp = checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha, tile);
									if (temp != null) {
										//Tile 32 does match
										tileObj = temp;
										tile = 32;
									}
								} else {
									//Anything bright enough is likely an explored tile.
									tileImg = tilesetImg.getSubimage(11*tileWidth, 13*tileHeight, tileWidth, tileHeight);
									Tile temp = checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha, tile);
									if (temp != null) {
										//Tile 32 does match
										tileObj = temp;
										tile = 219;
									}
								}
							}
							//System.out.println(row + ":" + col + ":" + tile);
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
					if (artistic) {
						similarityThreshold += 10;
					}
				} while (!tileFound);
			}
		}

		return new DecodedImage(tiles, convertTileWidth, convertTileHeight);
	}
	
	public void exportRenderedImage(DecodedImage decoded, int tilesetConvertTo, String exportPath) {
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
		logger.log(Level.INFO, "Render to tileset: " + tileset.getAuthor() + ":" + tileset.getImagePath());//Handy.

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
			if (col%((int)(Math.ceil((convertTileWidth+1)*0.1))) == 0) {
				logger.log(Level.INFO, (100.0*col/convertTileWidth) + "%");//Nice to see where we are in the algorithm.
			}
			for (int row = 0; row < convertTileHeight; row++) {
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
							Color toDraw = getRenderColor(tile.getForegroundColor(), tile.getBackgroundColor(), tileC, tilesetUsesAlpha);
							g2.setColor(toDraw);
							g2.fill(new Rectangle(col*tileWidth + x, row*tileHeight + y, 1, 1));
						}
					}
				}
			}
		}

		g2.dispose();

		TilesetManager.saveImage(output, exportPath);
	}
	
	public static Color getRenderColor(Color foregroundC, Color backgroundC, Color tileC, boolean tilesetUsesAlpha) {		
		Color toReturn;//Return this.
		
		if (tilesetUsesAlpha) {
			//The tileset uses alpha.
			double alpha = tileC.getAlpha()/255.0;//1.0 = foreground, 0.0 = background
			float transparency;//How much the foreground color is showing against black.

			float[] hsv = new float[3];//Hue = 0, Saturation = 1, Value = 2
			Color.RGBtoHSB(tileC.getRed(), tileC.getGreen(), tileC.getBlue(), hsv);
			transparency = hsv[2];//0.0...1.0

			//Dwarf Fortress a slightly tints the colors of the tiles.
			double average = transparency*255.0;
			int redBoost = (int)(Math.min(tileC.getRed()-average, 25)*(foregroundC.getRed()/255.0));
			int greenBoost = (int)(Math.min(tileC.getGreen()-average, 25)*(foregroundC.getGreen()/255.0));
			int blueBoost = (int)(Math.min(tileC.getBlue()-average, 25)*(foregroundC.getBlue()/255.0));

			int red = (int)(((foregroundC.getRed() + redBoost)*transparency)*alpha + (backgroundC.getRed())*(1.0-alpha));
			int green = (int)(((foregroundC.getGreen() + greenBoost)*transparency)*alpha + (backgroundC.getGreen())*(1.0-alpha));
			int blue = (int)(((foregroundC.getBlue() + blueBoost)*transparency)*alpha + (backgroundC.getBlue())*(1.0-alpha));
			toReturn = new Color(Math.min(Math.max(red, 0), 255),
					Math.min(Math.max(green, 0), 255),
					Math.min(Math.max(blue, 0), 255));//I think this is how colors are rendered.
		} else {
			//The tileset does not use alpha.
			boolean isPink = tileC.getRed() > 250 && tileC.getGreen() < 5 && tileC.getBlue() > 250;
			if (isPink) {
				toReturn = backgroundC;
			} else {
				boolean tileCisGrey = Math.abs(tileC.getRed()-tileC.getBlue()) < 2 && Math.abs(tileC.getRed() - tileC.getGreen()) < 2;
				if (tileCisGrey) {
					double transparency = tileC.getRed()/255.0;
					toReturn = new Color((int)(foregroundC.getRed()*transparency),
							(int)(foregroundC.getGreen()*transparency),
							(int)(foregroundC.getBlue()*transparency));
				} else {
					toReturn = new Color(tileC.getRed(), tileC.getGreen(), tileC.getBlue());
				}
			}
		}
		return toReturn;
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

	private static Callable<ArrayList<TilesetDetected>> threadCallableForTilesetRange(int start, int length, TilesetFitter fitter) {
		return new ThreadCallable(start, length, fitter);
	}

	public static void incrementNumTilesetChecksComplete() {
		numTilesetChecksComplete.incrementAndGet();
	}

	public static BufferedImage loadImage(String path) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(Main.class.getResourceAsStream(path));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Logger.getLogger(Main.LOGGER_NAME).log(Level.SEVERE, "Could not load an image at " + path);
			e.printStackTrace();
		}

		return image;
	}
}