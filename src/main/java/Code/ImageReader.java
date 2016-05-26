package Code;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Created by shafe on 5/26/2016.
 */
public class ImageReader {
	public static BufferedImage loadImageFromDisk(String path) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new File(path));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Err");
			e.printStackTrace();
		}

		return image;
	}

	public static BufferedImage loadImageFromResources(String path) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(Main.class.getResource(path));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Err");
			e.printStackTrace();
		}

		return image;
	}

	public static BufferedImage loadImageWithBackup(String path, String fallbackFromResources) {
		BufferedImage image = null;
		try {
			File file = new File(path);
			if (file.exists()) {
				image = ImageIO.read(file);
			}
			else {
				System.out.println("Input file not found.  Falling back to \"Jar/" + fallbackFromResources);
				image = ImageIO.read(Main.class.getResource(fallbackFromResources));
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Err");
			e.printStackTrace();
		}

		return image;
	}
}
