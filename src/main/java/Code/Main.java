package Code;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

	/** The location of the image to be converted. */
	private static String imageImportPath;

	/** The location of the converted image to be converted. */
	private static String imageExportPath;

	/** Threshold at which two colors are considered similar. */
	private static int threshold;

	/** Changes parsing slightly for making artistic DF pieces. */
	private static boolean artistic;

	/** For giving progress information during extractTileset. */
	private static AtomicInteger numTilesetChecksComplete;

	/**
	 * Run all this beautiful code...
	 * @param args Arguments given on the command line.
	 */
	public static void main(final String[] args) {
		//printTileset(22);
		//findTileset("Phoebus");
		//refreshTilesets();
		//convertImage(0); //All ready added below
		//14 anikki 8x8 Nice solid tileset
		//57 vidume 15x15 Uses alpha
		//112 - Lemunde, uses alpha, good for rendering
		//114 - Phoebus

		threshold = 40;
		artistic = false;

		Options options = new Options();
		options.addOption(Option.builder("l")
				.longOpt("list-tilesets")
				.desc("list all tileset ids and exit")
				.hasArg(false)
				.build());
		options.addOption(Option.builder("h")
				.longOpt("help")
				.desc("show help message and exit")
				.hasArg(false)
				.build());
		options.addOption(Option.builder("i")
				.longOpt("import-path")
				.hasArg(true)
				.argName("path")
				.desc("Image import path (Default: Image_Anikki8x8.png)")
				.type(String.class)
				.build());
		options.addOption(Option.builder("o")
				.longOpt("export-path")
				.hasArg(true)
				.argName("path")
				.desc("Image export path (Default: Converted.png)")
				.type(String.class)
				.build());
		options.addOption(Option.builder("t")
				.longOpt("tileset")
				.hasArg(true)
				.argName("tileset-id")
				.desc("Use tileset by id")
				.type(Integer.class)
				.build());

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine line = parser.parse(options, args);

			imageImportPath = line.getOptionValue("i", "/Image_Anikki8x8.png");
			imageExportPath = line.getOptionValue("o", "/Converted.png");

			if (line.hasOption("h")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("dwarfbot", options);
				System.exit(0);
			}

			if (line.hasOption("l")) {
				new TilesetManager().printTilesets();
				System.exit(0);
			} else {
				convertImage(Integer.parseInt(line.getOptionValue("t", "0")));
			}
		} catch (ParseException e) {
			System.err.println("Parsing failed.  Reason: " + e.getMessage());
		} catch (NumberFormatException e) {
			System.err.println("Not a ID integer.  Reason: " + e.getMessage());
		}
	}

	/**
	 * Redownload the tilesets from the wiki.
	 */
	@SuppressWarnings("unused")
	private static void refreshTilesets() {
		TilesetManager bot = new TilesetManager();
		bot.refreshTilesets();
	}

	/**
	 * Print the tileset with the given id.
	 * @param id The id of the tileset.
	 */
	@SuppressWarnings("unused")
	private static void printTileset(final int id) {
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();

		for (int i = 0; i < tilesets.size(); i++) {
			if (i == id) {
				System.out.println(tilesets.get(id));
			}
		}
	}

	/**
	 * Print the index of the first tileset with an author or path containing identifier.
	 * @param identifier The string to check.
	 */
	@SuppressWarnings("unused")
	private static void findTileset(final String identifier) {
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();

		for (int i = 0; i < tilesets.size(); i++) {
			Tileset tileset = tilesets.get(i);
			if (tileset.getAuthor().contains(identifier) || tileset.getImagePath().contains(identifier)) {
				System.out.println(i);
			}
		}
	}

	/**
	 * Convert the image given in the file `imageImportPath` to another tileset.
	 * @param tilesetIDConvertTo The index of the tileset to convert the image to.
	 */
	private static void convertImage(final int tilesetIDConvertTo) {
		//Load in tilesets
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();

		//Read in our image.
		BufferedImage toConvert = loadImage(imageImportPath);

		TileFitter fitter = new TileFitter(tilesets, artistic, toConvert, threshold);



		long timeBeforeExtraction = System.currentTimeMillis();

		TilesetDetected detected = extractTileset(fitter);

		System.out.println("Extraction time: " + (System.currentTimeMillis() - timeBeforeExtraction));

		//Now that I know the tileset of the image, decode the image into its colors and tile id's
		DecodedImage decoded = fitter.readTiles(detected.getBasex(), detected.getBasey(), detected.getTileset());

		//Re-render the image with the new tileset
		exportRenderedImage(toConvert, decoded, tilesets, tilesetIDConvertTo, "Resources" + imageExportPath);
	}


	private static Callable<ArrayList<TilesetDetected>> threadCallableForTilesetRange(int start, int length, TileFitter fitter) {
		return new ThreadCallable(start, length, fitter);
	}

	public static void incrementNumTilesetChecksComplete() {
		numTilesetChecksComplete.incrementAndGet();
	}

	private static TilesetDetected extractTileset(TileFitter fitter) throws Error {
		ArrayList<Tileset> tilesets = fitter.getTilesets();
		int numTilesetsToCheck = tilesets.size();//How many tilesets are we checking against?

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
			System.out.println("Creating thread with index " + index + ", length " + length);
			futures.add(pool.submit(threadCallableForTilesetRange(index, length, fitter)));
		}
		pool.shutdown(); // This line means it will stop accepting new threads; it will not terminate existing ones

		// Give progress information.
		try {
			do {
				System.out.println("Tileset checks " + 100.0 * numTilesetChecksComplete.get() / numTilesetsToCheck + "% complete.");
			} while (!pool.awaitTermination(1, TimeUnit.SECONDS));
		} catch (InterruptedException ie) {
			// We can safely ignore this and move on to the next code block, which will handle the error.
		}
		System.out.println("Tileset checks now finished.");

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

		Tileset best = tilesets.get(bestTilesetMatch);
		System.out.println("Detected tileset: " + best.getImagePath());
		return new TilesetDetected(basexList.get(bestTilesetMatch), baseyList.get(bestTilesetMatch), tilesets.get(bestTilesetMatch), tilesetMatchCount.get(bestTilesetMatch));
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
		System.out.println("Render to tileset: " + tileset.getAuthor() + ":" + tileset.getImagePath());//Handy.

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
			if (col%((int)((convertTileWidth+1)*0.1)) == 0) {
				System.out.println((100.0*col/convertTileWidth) + "%");//Nice to see where we are in the algorithm.
			}
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

		TilesetManager.saveImage(output, exportPath);
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
}
