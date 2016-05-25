package Code;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.SystemUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

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

	/**
	 * The name of the logger
	 * Logging levels are: - SEVERE (fatal errors)
	 *                     - WARNING
	 *                     - INFO (algorithm progress)
	 *                     - CONFIG
	 *                     - FINE (percentages)
	 *                     - FINER (thread creation)
	 *                     - FINEST
	 */
	public static final String LOGGER_NAME = "Code";

	/** The location of the image to be converted. */
	private static String imageImportPath;

	/** The location of the converted image to be converted. */
	private static String imageExportPath;

	/** Changes parsing slightly for making artistic DF pieces. */
	private static boolean artistic;

	/**
	 * Run all this beautiful code...
	 * @param args Arguments given on the command line.
	 */
	public static void main(final String[] args) {
		//findTileset("Phoebus");
		//refreshTilesets();
		//convertImage(0);
		//14 anikki 8x8, nice solid tileset
		//57 vidume 15x15, uses alpha
		//111 - isenhertz, uses color boosts, good for testing, uses altered RAWS
		//112 - Lemunde, uses alpha, good for rendering, uses altered RAWS
		//114 - Phoebus, uses alpha, uses altered RAWS

		Logger logger = Logger.getLogger(Main.LOGGER_NAME);
		logger.setLevel(Level.FINEST); // The Levels will be limited by the handler, not by logger.
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.FINE);
		logger.addHandler(handler);

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
		options.addOption(Option.builder("L")
				.longOpt("log-level")
				.hasArg(true)
				.argName("level")
				.desc("Use this logging level. (Default: INFO). SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST")
				.type(String.class)
				.build());

		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);
		} catch (ParseException e) {
			logger.log(Level.SEVERE, "Parsing failed.  Reason: " + e.getMessage());
			System.exit(1);
		}
		try {
			Level logLevel = Level.parse(line.getOptionValue("log-level", "FINE"));
			handler.setLevel(logLevel);
		} catch (IllegalArgumentException e) {
			logger.log(Level.SEVERE, "Failed to parse log level. Check the help for allowed values.");
		}

		imageImportPath = line.getOptionValue("i", "/b.png");
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
			try {
				convertImage(Integer.parseInt(line.getOptionValue("t", "0")));
			} catch (NumberFormatException e) {
				logger.log(Level.SEVERE, "ID is not an integer.  Reason: " + e.getMessage());
				System.exit(1);
			}
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
	private static void convertImage(int tilesetIDConvertTo) {
		//Load in tilesets
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();

		//Read in our image.
		BufferedImage toConvert = loadImage(imageImportPath);

		TilesetFitter fitter = new TilesetFitter(tilesets, artistic);
		fitter.loadImageForConverting(toConvert);
		DecodedImage decoded = fitter.decodeImage();
		
		try {
			FileOutputStream fileOut = new FileOutputStream("/tmp/a.ser");
			ObjectOutputStream objOut = new ObjectOutputStream(fileOut);
			objOut.writeObject(decoded);
			objOut.close();
			fileOut.close();
		} catch (IOException e) {
			//TODO
		}

		//Re-render the image with the new tileset
		fitter.exportRenderedImage(decoded, 57/*tilesetIDConvertTo*/, "Resources" + imageExportPath);
	}
	
	public static BufferedImage loadImage(String path) {
		return TilesetFitter.loadImage(path);
	}
}
