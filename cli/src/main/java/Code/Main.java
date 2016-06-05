package Code;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

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
	
	/** The logger for everything to use. */
	public static Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Main.class);

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
		//56 talryth 15x15, very similar to vidume! source of false positives
		//57 vidume 15x15, uses alpha
		//76 DF-Nordic_v1, looks awful
		//111 - isenhertz, uses color boosts, good for testing, uses altered RAWS
		//112 - Lemunde, uses alpha, good for rendering, uses altered RAWS
		//114 - Phoebus, uses alpha, uses altered RAWS
		//118 - Synergy, uses alpha, uses altered RAWS

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
				.desc("Image import path (Default: [JarResources]/b.png - A Demo File)")
				.type(String.class)
				.build());
		options.addOption(Option.builder("o")
				.longOpt("export-path")
				.hasArg(true)
				.argName("path")
				.desc("Image export path (Default: Export/Converted.png)")
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
		options.addOption(Option.builder("E")
				.longOpt("export-decoded")
				.hasArg(true)
				.argName("decoded-export-path")
				.desc("Export a .ser file (DecodedImage) that can be used with -I to transform into any tileset. " +
					"Does not convert the image after.")
				.type(String.class)
				.build());
		options.addOption(Option.builder("I")
				.longOpt("import-decoded")
				.hasArg(true)
				.argName("decoded-import-path")
				.desc("Import a DecodedImage; faster than decoding an image every time. Extension: .ser")
				.type(String.class)
				.build());
		options.addOption(Option.builder("a")
				.longOpt("artistic")
				.hasArg(true)
				.argName("true/false")
				.desc("Enable artistic rendering options.")
				.type(Integer.class)
				.build());

		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
			line = parser.parse(options, args);
		} catch (ParseException e) {
			logger.error("Parsing failed.  Reason: ", e.getMessage());
			System.exit(1);
		}
		try {
			Level logLevel = Level.valueOf(line.getOptionValue("log-level", "INFO"));
			logger.setLevel(logLevel);
		} catch (IllegalArgumentException e) {
			logger.error("Failed to parse log level. Check the help for allowed values.");
		}
		artistic = Boolean.valueOf(line.getOptionValue("a", "false"));

		imageImportPath = line.getOptionValue("i", "gibberish");
		imageExportPath = line.getOptionValue("o", "Exported/Converted.png");
		String importPath = imageImportPath;
		boolean alreadyDecoded = false;
		if (line.hasOption("import-decoded")) {
			alreadyDecoded = true;
			importPath = line.getOptionValue("import-decoded");
			if (importPath == null) {
				throw new Error("Import decoded option requires a path.");
			}
		}

		if (line.hasOption("h")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("dwarfbot", options);
			System.exit(0);
		}

		if (line.hasOption("l")) {
			new TilesetManager().printTilesets();
			System.exit(0);
		} else if (line.hasOption("export-decoded")) {
			String decodedExportPath = line.getOptionValue("export-decoded");
			if (decodedExportPath == null) {
				throw new Error("Export decoded option requires a path.");
			}
			exportDecodedImage(importPath, alreadyDecoded, decodedExportPath);
		} else {
			try {
				convertImage(importPath, alreadyDecoded, Integer.parseInt(line.getOptionValue("t", "0")));
			} catch (NumberFormatException e) {
				logger.error("ID is not an integer.  Reason: ", e.getMessage());
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
	 * @param importPath The path of the image file or .ser file.
	 * @param alreadyDecoded Whether `importPath` refers to a serialized DecodedImage or not.
	 * @param tilesetIDConvertTo The index of the tileset to convert the image to.
	 */
	private static void convertImage(String importPath, boolean alreadyDecoded, int tilesetIDConvertTo) {
		TilesetFitter fitter = createFitter();

		DecodedImage decodedImage = getDecodedImageUsingFitter(fitter, importPath, alreadyDecoded);

		//Re-render the image with the new tileset
		fitter.exportRenderedImage(decodedImage, tilesetIDConvertTo, imageExportPath);
	}

	/**
	 * Export a serialized DecodedImage.
	 * @param importPath The path of the image file or .ser file.
	 * @param alreadyDecoded Whether `importPath` refers to a serialized DecodedImage or not.
	 * @param exportPath Where to put the serialized DecodedImage.
	 */
	private static void exportDecodedImage(String importPath, boolean alreadyDecoded, String exportPath) {
		TilesetFitter fitter = createFitter();

		DecodedImage decodedImage = getDecodedImageUsingFitter(fitter, importPath, alreadyDecoded);

		try (
				FileOutputStream fileOutputStream = new FileOutputStream(exportPath);
				ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)
		) {
			objectOutputStream.writeObject(decodedImage);
		} catch (IOException e) {
			logger.error("Could not write the DecodedImage.");
			throw new Error(e);
		}
	}

	private static DecodedImage getDecodedImageUsingFitter(TilesetFitter fitter, String importPath, boolean alreadyDecoded) {
		if (alreadyDecoded) {
			DecodedImage decodedImage;
			try (
					FileInputStream fileInputStream = new FileInputStream(importPath);
					ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
			) {
				decodedImage = (DecodedImage)objectInputStream.readObject();
			} catch (IOException e) {
				logger.error("Could not read import file.");
				throw new Error(e);
			} catch (ClassNotFoundException e) {
				logger.error("Could not find DecodedImage class. " +
						"This should not happen unless you are messing with the code...");
				throw new Error(e);
			}
			return  decodedImage;
		} else {
			//Read in our image.
			BufferedImage toConvert = ImageReader.loadImageWithFallback(importPath, "Image_Anikki8x8.png");
			fitter.loadImageForConverting(toConvert);
			return fitter.decodeImage();
		}
	}

	private static TilesetFitter createFitter() {
		//Load in tilesets
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();

		return new TilesetFitter(tilesets, artistic);
	}

}
