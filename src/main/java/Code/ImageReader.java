package Code;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Created by shafe on 5/26/2016.
 */
public class ImageReader {
	public static BufferedImage loadImageFromDisk(String path) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new File(path));
		} catch (IOException e) {
			Main.logger.log(Level.SEVERE, "Error reading image from disk");
			e.printStackTrace();
		}

		return image;
	}

	public static BufferedImage loadImageFromResources(String path) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(Main.class.getResource(path));
		} catch (IOException e) {
			Main.logger.log(Level.SEVERE, "Error reading image from resources");
			e.printStackTrace();
		}

		return image;
	}

	public static BufferedImage loadImageWithFallback(String path, String fallbackFromResources) {
		BufferedImage image = null;
		try {
			File file = new File(path);
			if (file.exists()) {
				image = ImageIO.read(file);
			}
			else {
				Main.logger.log(Level.WARNING, "Input image not found.  Falling back to \"Jar/" + fallbackFromResources);
				image = ImageIO.read(Main.class.getResource("/" + fallbackFromResources));
			}
		} catch (IOException e) {
			Main.logger.log(Level.SEVERE, "Error reading image with fallback");
			e.printStackTrace();
		}

		return image;
	}
}
