package Code;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Created by shafe on 5/26/2016.
 */
public class ImageReader {
	private static String MAGIC_WORD = "@resources/";
	private static Logger logger = LoggerFactory.getLogger(ImageReader.class);

	public static BufferedImage loadImageFromDisk(String path) {
		BufferedImage image = null;

		if (path.startsWith(MAGIC_WORD)) {
			path = path.substring(MAGIC_WORD.length());
			loadImageFromResources(path);
		}
		else {
			try {
				image = ImageIO.read(new File(path));
			} catch (IOException e) {
				logger.error("Error reading image from disk");
				e.printStackTrace();
			}
		}

		return image;
	}

	public static BufferedImage loadImageFromResources(String path) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(ImageReader.class.getResource(path));
		} catch (IOException e) {
			logger.error("Error reading image from resources");
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
				logger.warn("Input image not found.  Falling back to \"Jar/" + fallbackFromResources);
				image = ImageIO.read(ImageReader.class.getResource("/" + fallbackFromResources));
			}
		} catch (IOException e) {
			logger.error("Error reading image with fallback");
			e.printStackTrace();
		}

		return image;
	}
}
