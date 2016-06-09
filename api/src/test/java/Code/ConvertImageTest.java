package Code;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * Created by shafe on 5/24/2016.
 */
public class ConvertImageTest {
	public static final double CONFIDENCE_INTERVAL = .05;
	private static TilesetFitter fitter;
	private static DecodedImage decoded;
	private static ArrayList<Tileset> tilesets;

	@BeforeClass
	public static void setUp() throws Exception {
		//Load in tilesets
		TilesetManager bot = new TilesetManager();
		tilesets = bot.getTilesets();

		//Read in our image.
		BufferedImage toConvert = ImageReader.loadImageFromResources("/Image_curses640x300b.png");

		fitter = new TilesetFitter(tilesets, false);
		fitter.loadImageForConverting(toConvert);
		decoded = fitter.decodeImage();
	}

	@Test
	public void testDefaultTilesetDetection() {
		assertEquals(decoded.getTileset().getID(), 0);
	}

	@Test
	public void testDefaultTilesetConversionWithoutAlpha() {
		for (Tileset tileset : tilesets) {
			if (!tileset.usesAlpha()) {
				convertImageForTest(tileset, "without_alpha");
			}
		}
	}

	@Test
	public void testDefaultTilesetConversionWithAlpha() {
		for (Tileset tileset : tilesets) {
			if (tileset.usesAlpha()) {
				convertImageForTest(tileset, "with_alpha");
			}
		}
	}

	private void convertImageForTest(Tileset tileset, String extraPathInfo) {
		String convertedImagePath = "TestRunner/" + extraPathInfo + tileset.getImagePath();
		String correctImagePath = "/" + extraPathInfo + tileset.getImagePath();
		System.out.println("Current Tileset: " + tileset.getImagePath());
		fitter.exportRenderedImage(decoded, tileset.getID(), convertedImagePath);
		if (this.getClass().getResource(correctImagePath) != null) {
			assertEquals(diffImage(convertedImagePath,correctImagePath), 0, CONFIDENCE_INTERVAL);
		}
		else {
			System.out.println(convertedImagePath + " was not found");
		}
	}

	/**
	 * http://stackoverflow.com/q/23537710
	 * @return diff percent
	 */
	public double diffImage(String convertedImagePath, String correctImagePath) {
		BufferedImage img1 = null;
		BufferedImage img2 = null;

		try{
			img1 = ImageIO.read(new File(convertedImagePath));
			img2 = ImageIO.read(this.getClass().getResource(correctImagePath));
		} catch (IOException e) {
			e.printStackTrace();
		}
		int width1 = img1.getWidth(null);
		int width2 = img2.getWidth(null);
		int height1 = img1.getHeight(null);
		int height2 = img2.getHeight(null);

		if ((width1 != width2) || (height1 != height2)) {
			System.err.println("Error: Images dimensions mismatch");
			return 1;
		}

		long diff = 0;
		for (int i = 0; i < height1; i++) {
			for (int j = 0; j < width1; j++) {
				int rgb1 = img1.getRGB(j, i);
				int rgb2 = img2.getRGB(j, i);
				int r1 = (rgb1 >> 16) & 0xff;
				int g1 = (rgb1 >>  8) & 0xff;
				int b1 = (rgb1      ) & 0xff;
				int r2 = (rgb2 >> 16) & 0xff;
				int g2 = (rgb2 >>  8) & 0xff;
				int b2 = (rgb2      ) & 0xff;
				diff += Math.abs(r1 - r2);
				diff += Math.abs(g1 - g2);
				diff += Math.abs(b1 - b2);
			}
		}
		double n = width1 * height1 * 3;
		double p =  diff / n / 255.0;

		System.out.println("Diff: " + p);
		return p;
	}
	
}