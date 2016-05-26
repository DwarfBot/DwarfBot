package Code;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.awt.image.BufferedImage;
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
		options.addOption(Option.builder("a")
				.longOpt("artistic")
				.hasArg(true)
				.argName("true/false")
				.desc("Enable artistic rendering options.")
				.type(Integer.class)
				.build());

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine line = parser.parse(options, args);

			imageImportPath = line.getOptionValue("i", "b.png");
			imageExportPath = line.getOptionValue("o", "Converted.png");
			artistic = Boolean.valueOf(line.getOptionValue("a", "false"));

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
	private static void convertImage(int tilesetIDConvertTo) {
		//Load in tilesets
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();

		//Read in our image.
		BufferedImage toConvert = ImageReader.loadImageWithBackup(imageImportPath, "/b.png");

		TilesetFitter fitter = new TilesetFitter(tilesets, artistic);
		fitter.loadImageForConverting(toConvert);
		DecodedImage decoded = fitter.decodeImage();

		//Re-render the image with the new tileset
		fitter.exportRenderedImage(decoded, tilesetIDConvertTo, "Export/" + imageExportPath);
	}
}
