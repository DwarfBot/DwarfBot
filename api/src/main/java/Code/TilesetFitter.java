package Code;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
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
import javax.imageio.ImageIO;

/**
 * Created by jacob on 2016-05-22.
 */
public class TilesetFitter {
	private ArrayList<Tileset> tilesets;
	private boolean artistic;
	private int similarityThreshold;
	private BufferedImage toConvert;
	private int seedx;
	private int seedy;
	private AtomicInteger numTilesetChecksComplete;
	private static Logger logger = LoggerFactory.getLogger(TilesetFitter.class);

	public TilesetFitter(ArrayList<Tileset> _tilesets, boolean _artistic) {
		tilesets = _tilesets;
		artistic = _artistic;
		similarityThreshold = 10;
	}
	
	public void loadImageForConverting(BufferedImage toConvert_) {
		toConvert = toConvert_;
		
		if (toConvert.getWidth() < 96 && toConvert.getHeight() < 96) {
			//Get a seed.
			seedx = 0;
			seedy = 0;
			
			Color baseColor = null;
			
			checkIfSameColorInside:
			for (int x2 = 0; x2 < toConvert.getWidth(); x2++) {
				for (int y2 = 0; y2 < toConvert.getHeight(); y2++) {
					if (x2 == 0 && y2 == 0) {
						baseColor = new Color(toConvert.getRGB(x2, y2));
					} else if (!baseColor.equals(new Color(toConvert.getRGB(x2, y2)))) {
						seedx = x2;
						seedy = y2;
						
						break checkIfSameColorInside;
					}
				}
			}
		} else {
			Random rng = new Random();
			
			int attempts = 0;
			boolean finished = false;
			do {
				int x;//Where to take a sample from.
				int y;//Where to take a sample from.
				if (toConvert.getWidth() <= 96) {//96 = 32*2, 32 = size of largest tileset tile
					//The image is not two tiles wide. Be conservative.
					x = 0;
				} else {
					x = rng.nextInt(toConvert.getWidth() - 96);
				}
				if (toConvert.getWidth() <= 96) {
					//The image is not two tiles tall. Be conservative.
					y = 0;
				} else {
					y = rng.nextInt(toConvert.getHeight() - 96);
				}
				BufferedImage sampleFromToConvert = toConvert.getSubimage(x, y, Math.min(toConvert.getWidth(),  96), Math.min(toConvert.getHeight(), 96));
				
				//Check that colors vary inside the sample image. If color varies, there is a tile inside of the sample image.
				//If color does not vary, attempt to pick another sample image.
				boolean sameColorInside = true;
				Color baseColor = null;
			
			checkIfSameColorInside:
				for (int x2 = 32; x2 < 64; x2++) {
					for (int y2 = 32; y2 < 64; y2++) {
						if (x2 == 32 && y2 == 32) {
							baseColor = new Color(sampleFromToConvert.getRGB(x2, y2));
						} else if (!baseColor.equals(new Color(sampleFromToConvert.getRGB(x2, y2)))) {
							sameColorInside = false;
							seedx = x + x2 + 1;
							seedy = y + y2 + 1;
							
							break checkIfSameColorInside;
						}
					}
				}
				
				if (!sameColorInside) {
					finished = true;
				}
				
				attempts++;
			} while (attempts < 10 && !finished);
			
			if (!finished) {
				//Brute force. This method lacks variety.
				Color baseColor = null;
			findSeed:
				for (int x2 = 32; x2 < toConvert.getWidth() - 32; x2++) {
					for (int y2 = 32; y2 < toConvert.getHeight() - 32; y2++) {
						if (x2 == 32 && y2 == 32) {
							baseColor = new Color(toConvert.getRGB(x2, y2));
						} else if (!baseColor.equals(new Color(toConvert.getRGB(x2, y2)))) {
							seedx = x2;
							seedy = y2;
							finished = true;
							
							break findSeed;
						}
					}
				}
			}
		}
		logger.info("Seed: {},{}", seedx, seedy);
	}
	
	public DecodedImage decodeImage() {
		long timeBeforeExtraction = System.currentTimeMillis();

		//What tileset is the image using?
		TilesetDetected detected = extractTileset();

		logger.info("Extraction time: {}", (System.currentTimeMillis() - timeBeforeExtraction));

		logger.trace("Seed: {},{}", seedx, seedy);
		
		//Decode the image into its tile colors and tile id's.
		return readTiles(toConvert, detected.getBasex(), detected.getBasey(), detected.getTileset());
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
			logger.trace("Creating thread with index {}, length {}", index, length);
			futures.add(pool.submit(threadCallableForTilesetRange(index, length)));
		}
		pool.shutdown(); // This line means it will stop accepting new threads; it will not terminate existing ones

		// Give progress information.
		try {
			do {
				logger.info("Tileset checks {}% complete.", 100.0 * numTilesetChecksComplete.get() / numTilesetsToCheck);
			} while (!pool.awaitTermination(5, TimeUnit.SECONDS));
		} catch (InterruptedException ie) {
			// We can safely ignore this and move on to the next code block, which will handle the error.
		}
		logger.info("Tileset checks now finished.");

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
		//If two or more tilesets have an equal amount of matched area, we will run more vigorous tests. We call these "collisions".
		int bestTilesetMatch = 1;
		//Some collision handling.
		ArrayList<Integer> collisions = new ArrayList<Integer>();//Do any tilesets convert the same amount of image?
		//Other data.
		double bestTilesMatched = 0;
		double areaConvertedBest = 0.0;
		for (int tilesetID = 0; tilesetID < numTilesetsToCheck; tilesetID++) {
			Tileset tileset = tilesets.get(tilesetID);
			double tileSize = tileset.getTileWidth()*tileset.getTileHeight();
			double tilesMatched = tilesetMatchCount.get(tilesetID);
			double areaConverted = tilesMatched*tileSize;
			boolean betterTileserFound = false;
			
			if ( areaConverted > areaConvertedBest ) {
				bestTilesetMatch = tilesetID;
				areaConvertedBest = areaConverted;
				bestTilesMatched = tilesMatched;
				betterTileserFound = true;
			}
			
			//Collision handling
			if (tilesMatched > 0) {
				if (bestTilesMatched < 4) {
					//The tests were not conclusive enough. Fall back to old methods.
					collisions.add(tilesetID);
				} else {
					if (betterTileserFound) {
						collisions.clear();
						collisions.add(bestTilesetMatch);
					} else if (areaConverted == areaConvertedBest) {
						collisions.add(tilesetID);
					}
				}
			}
		}
		
		//Check for errors.
		if (tilesetMatchCount.get(bestTilesetMatch) == 0) {
			throw new Error("No suitable match found.");
		}
		
		ArrayList<Integer> areasConverted = new ArrayList<Integer>();
		if (collisions.size() > 1) {
			logger.info("Resolving tileset collisions. Found {} collisions.", collisions.size());
			//We will try and fix the collisions.
			//Data handling.
			areaConvertedBest = 0.0;
			for (int i = 0; i < collisions.size(); i++) {
				if (i != 0) {
					logger.info("Resolving collision between tileset id's: {} and {}", bestTilesetMatch ,collisions.get(i));
				}
				
				//Read in a new tileset.
				int index = i;
				Tileset tileset = tilesets.get(collisions.get(i));
				int tileSize = tileset.getTileWidth()*tileset.getTileHeight();
				if (areasConverted.size() <= index) {
					//How much of the original image does it convert?
					DecodedImage di = readTiles(toConvert, basexList.get(collisions.get(index)), baseyList.get(collisions.get(index)), tileset, false);
					int numMatches = 0;
					for (Tile tile : di.getTiles()) {
						if (tile != null) {
							numMatches++;
						}
					}
					areasConverted.add(numMatches);
				}
				double areaConverted = areasConverted.get(index)*tileSize;
				
				//Check which tileset converts more of the original image.
				if (areaConverted > areaConvertedBest) {
					areaConvertedBest = areaConverted;
					bestTilesetMatch = collisions.get(index);
				}
			}
		}

		
		Tileset best = tilesets.get(bestTilesetMatch);//Extract the tiles from the image.
		
		logger.info("Detected tileset: {}", best.getImagePath());
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
		/*
		 * This is how I detect a tileset:
		 * I find a place where colors vary in the image. This is a spot guaranteed to have a tile.
		 * For each tileset, I then chop a small section of the image around this point. It is, again, guaranteed to have a tile.
		 * I then brute force every tile in the tileset against a point in sample image.
		 * I vary this point a tiny bit through offsetx and offesty.
		 * I keep a list of all offsetx and offsety where points are found.
		 * If I get a positive at any offsetx and offsety pair:
		 * I decode the entire sample against the offsetx and offsety listed, and find which pair yields the best conversion rate.
		 * I return the pair that gives the best conversion rate, if there is one.
		 */
		
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
		boolean tilesetUsesAlpha = tileset.usesAlpha();

		boolean tilesetMatches = false;

		int x = 0;
		int y = 0;

		int offsetx = 0;
		int offsety = 0;

		//Place to store tiles.
		BufferedImage[] tileImages = new BufferedImage[256];
		
		//Crop image for checking
		int imagex = (int)(Math.max(Math.min(seedx - tileWidth*2, toConvert.getWidth() - 4*tileWidth), 0));
		int imagey = (int)(Math.max(Math.min(seedy - tileHeight*2, toConvert.getHeight() - 4*tileHeight), 0));
		BufferedImage localToConvert = toConvert.getSubimage(imagex, imagey, Math.min(tileWidth*4, toConvert.getWidth()), Math.min(tileHeight*4,  toConvert.getHeight()));
		
		//Set up x and y.
		if (localToConvert.getWidth() - 3*tileWidth < 0) {
			x = 0;
		} else {
			x = tileWidth;
		}
		if (localToConvert.getHeight() - 3*tileHeight < 0) {
			y = 0;
		} else {
			y = tileHeight;
		}

		int spaceAllotedX = Math.min(localToConvert.getWidth() - tileWidth, tileWidth);//How much space can we vary
		int spaceAllotedY = Math.min(localToConvert.getHeight() - tileHeight, tileHeight);
		
		boolean attempt = false;
		
		ArrayList<TilesetDetected> detected = new ArrayList<TilesetDetected>();
		for (offsetx = 0; offsetx < spaceAllotedX; offsetx++) {
			for (offsety = 0; offsety < spaceAllotedY; offsety++) {
				BufferedImage sampleImg = localToConvert.getSubimage(x + offsetx, y + offsety, tileWidth, tileHeight);

				//Make sure we are not checking against swatches of black.
				Color color = null;
				boolean sameColor = true;
			checkColors:
				for (int x2 = 0; x2 < tileWidth; x2++) {
					for (int y2 = 0; y2 < tileHeight; y2++) {
						if (x2 == 0 && y2 == 0) {
							color = new Color(sampleImg.getRGB(x2, y2));
						} else {
							Color color2 = new Color(sampleImg.getRGB(x2, y2));
							if (!color2.equals(color)) {
								sameColor = false;
								break checkColors;
							}
						}
					}
				}
				if (sameColor) {
					continue;//Try the next tile.
				}
				
				//Check each tile in the tileset.
			tileSearch:
				for (int tile = 0; tile < 256; tile++) {					
					if (tileImages[tile] == null) {
						//Load in image.
						int tileCol = tile%16;
						int tileRow = (tile - tileCol)/16;
						tileImages[tile] = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);
					}
					
					//Does the tile match?
					Tile tileObj = checkSimilarity(sampleImg, tileImages[tile], tilesetUsesAlpha, tile, true);
					if (tileObj != null) {
						if (tileset.getID() == 105 && !attempt) {
							TilesetManager.saveImage(sampleImg, "Resources/Sample.png");
							TilesetManager.saveImage(tileImages[tile], "Resources/Tile.png");
							attempt = true;
						}
						tilesetMatches = true;
						
						int basex = (x + offsetx)%tileWidth;
						int basey = (y + offsety)%tileHeight;
						detected.add(new TilesetDetected(basex, basey, tileset, -1));

						break tileSearch;
					}
				}
			}
		}

		if (tilesetMatches) {
			//Check each case of the tileset matching, and find which one matches the best.
			int bestMatches = -1;
			TilesetDetected bestTilesetDetected = detected.get(0);
			
			for (TilesetDetected d : detected) {
				int numMatches = 0;
	
				int basex = d.getBasex();
				int basey = d.getBasey();
	
				for (int col = 0; col < (localToConvert.getWidth()-basex)/tileWidth; col++) {
					for (int row = 0; row < (localToConvert.getHeight()-basey)/tileHeight; row++) {
						BufferedImage sampleImg = localToConvert.getSubimage(basex + col*tileWidth, basey + row*tileHeight, tileWidth, tileHeight);
					
						//Make sure we are not checking against swatches of black.
						Color color = null;
						boolean sameColor = true;
					checkColors:
						for (int x2 = 0; x2 < tileWidth; x2++) {
							for (int y2 = 0; y2 < tileHeight; y2++) {
								if (x2 == 0 && y2 == 0) {
									color = new Color(sampleImg.getRGB(x2, y2));
								} else {
									Color color2 = new Color(sampleImg.getRGB(x2, y2));
									if (!color2.equals(color)) {
										sameColor = false;
										break checkColors;
									}
								}
							}
						}
						if (sameColor) {
							continue;//Try the next tile.
						}
						
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
							
							if (tileImages[tile] == null) {
								//Load in image.
								int tileCol = tile%16;
								int tileRow = (tile - tileCol)/16;
								tileImages[tile] = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);
							}
	
							Tile tileObj = checkSimilarity(sampleImg, tileImages[tile], tilesetUsesAlpha, tile, true);
							if (tileObj != null) {
								numMatches++;
								break tileSearch;
							}
						}
					}
				}
				
				d.setMatchCount(numMatches);
	
				if (numMatches == 0) {
					//Contradictory if no matches found when one match is confirmed. Faulty code above!!
					throw new Error("Contradictory code caught.");
				}
				
				if (numMatches > bestMatches) {
					bestMatches = numMatches;
					bestTilesetDetected = d;
				}
			}
			
			int basex = (bestTilesetDetected.getBasex()+imagex)%tileWidth;
			int basey = (bestTilesetDetected.getBasey()+imagey)%tileHeight;
			return new TilesetDetected(basex, basey, tileset, bestTilesetDetected.getMatchCount());

		} else {
			//Tileset does not match
			return new TilesetDetected(0, 0, tileset, 0);
		}
	}
	
	private Tile checkSimilarity(BufferedImage sampleImg, BufferedImage tileImg, boolean tilesetUsesAlpha, int tilesetID) {
		return checkSimilarity(sampleImg, tileImg, tilesetUsesAlpha, tilesetID, false);
	}
	
	private Tile checkSimilarity(BufferedImage sampleImg, BufferedImage tileImg, boolean tilesetUsesAlpha, int tilesetID, boolean stringent) {
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
		
		if (tilesetUsesAlpha &&!performedCheck) {
			return null;//Cannot safely say yes.
		}
		if (tilesetUsesAlpha && stringent && (foregroundC == null || backgroundC == null)) {
			return null;
		}
		if (tilesetUsesAlpha && foregroundC != null && backgroundC!= null && backgroundC.equals(foregroundC)) {
			return null;//Removes source of false positives with ~99% confidence.
		}
		
		if (foregroundC == null) {
			foregroundC = Color.GRAY;//To handle some edge cases.
		}
		if (backgroundC == null) {
			backgroundC = Color.BLACK;//To handle some edge cases.
		}
		
		return new Tile(backgroundC, foregroundC);
	}
	
	public DecodedImage readTiles(BufferedImage localToConvert, int basex, int basey, Tileset tileset) {
		return readTiles(localToConvert, basex, basey, tileset, true);
	}

	public DecodedImage readTiles(BufferedImage localToConvert, int basex, int basey, Tileset tileset, boolean printInfo) {
		BufferedImage tilesetImg = null;
		try {
			tilesetImg = tileset.loadImage();
		} catch (IOException e) {
			throw new Error("Could not load the tileset.", e);
		}

		similarityThreshold = 10;
		
		int tileWidth = tileset.getTileWidth();//How wide is a tile? Pixels.
		int tileHeight = tileset.getTileHeight();//How tall is a tile? Pixels.

		boolean tilesetUsesAlpha = tileset.usesAlpha();//Does the tileset use alpha? This affects how the tileset is rendered.

		ArrayList<Tile> tiles = new ArrayList<Tile>();//Set up some "storage" variables. This stores what tiles are found.
		int convertTileWidth = (localToConvert.getWidth()-basex)/tileWidth;//How many tiles wide the image to be converted is.
		int convertTileHeight = (localToConvert.getHeight()-basey)/tileHeight;//How many tiles tall the image to be converted is.

		for (int col = 0; col < convertTileWidth; col++) {
			if (printInfo) {
				if (col%10 == 0) {
					logger.info("{}%", (100.0*col/convertTileWidth));//Nice to see where we are in the algorithm.
				}
			}
			for (int row = 0; row < convertTileHeight; row++) {
				BufferedImage sampleImg = localToConvert.getSubimage(basex + col*tileWidth, basey + row*tileHeight, tileWidth, tileHeight);
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
						BufferedImage tileImg = tilesetImg.getSubimage(tileCol*tileWidth, tileRow*tileHeight, tileWidth, tileHeight);

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

		return new DecodedImage(tiles, convertTileWidth, convertTileHeight, tileset);
	}
	
	public void exportRenderedImage(DecodedImage decoded, int tilesetConvertTo, String exportPath) {
		BufferedImage output = renderImage(decoded, tilesetConvertTo);
		TilesetManager.saveImage(output, exportPath);
	}

	public BufferedImage renderImage(DecodedImage decoded, int tilesetConvertTo) {
		//Read in decoded info.
		ArrayList<Tile> tiles = decoded.getTiles();
		int convertTileWidth = decoded.getTilesWide();
		int convertTileHeight = decoded.getTilesTall();

		//Data management
		int tilesetID = tilesetConvertTo;//What tileset am I converting to.
		Tileset tileset = tilesets.get(tilesetID);//Get that tileset.
		BufferedImage tilesetImg = ImageReader.loadImageFromResources("/Tilesets" + tileset.getImagePath());//And its image.
		int tileWidth = tileset.getTileWidth();//How wide are the tiles? Pixels
		int tileHeight = tileset.getTileHeight();//How tall are the tiles?
		logger.info("Render to tileset: {}:{}", tileset.getAuthor(), tileset.getImagePath());//Handy.

		boolean tilesetUsesAlpha = tileset.usesAlpha();//Does the tileset use alpha? This affects rendering.
		BufferedImage tileImg;

		//Set up image to write to
		BufferedImage output = new BufferedImage(convertTileWidth*tileWidth, convertTileHeight*tileHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = output.createGraphics();
		g2.setColor(Color.BLACK);//Fill with black. Touche.
		g2.fill(new Rectangle(0, 0, 1000, 1000));

		for (int col = 0; col < convertTileWidth; col++) {
			if (col%10 == 0) {
				logger.info("{}%",(100.0*col/convertTileWidth));//Nice to see where we are in the algorithm.
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
		return output;
	}
	
	public static Color getRenderColor(Color foregroundC, Color backgroundC, Color tileC, boolean tilesetUsesAlpha) {		
		Color toReturn;//Return this.
		
		boolean isPink = tileC.getRed() > 250 && tileC.getGreen() < 5 && tileC.getBlue() > 250;
		if (isPink && !tilesetUsesAlpha) {
			return backgroundC;
		} else {
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

	public int getNumTilesetChecksComplete() {
		if (numTilesetChecksComplete == null) {
			return 0;
		}
		return numTilesetChecksComplete.get();
	}

	private Callable<ArrayList<TilesetDetected>> threadCallableForTilesetRange(int start, int length) {
		return new ThreadCallable(start, length, this);
	}

	public void incrementNumTilesetChecksComplete() {
		numTilesetChecksComplete.incrementAndGet();
	}
}
