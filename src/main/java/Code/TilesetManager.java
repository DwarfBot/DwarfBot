package Code;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import Code.Main;
import Code.Tileset;
import WikiBot.ContentRep.Template;
import WikiBot.ContentRep.Page;
import WikiBot.ContentRep.PageLocation;
import WikiBot.Core.GenericBot;

/**
 * 
 * @author ErnieParke
 * 
 * This class requires JavaMediawikiBot from choco31415 to run.
 *
 */

public class TilesetManager extends GenericBot {
	
	private String TILESET_INFO_FILE;
	
	public TilesetManager() {
		//What MediaWiki family am I browsing?
		String family = "Random";
		mdm.readFamily(family, 0);
		revisionDepth = 0;

		TILESET_INFO_FILE = "/tileset.txt";
	}
	
	public void refreshTilesets() {
		Page page = getWikiPage( new PageLocation("Tileset repository", "dw"));
		ArrayList<Template> templates = page.getTemplates();
		
		//Process the tilsets found, and then write them to the local files.
		String fileOutput =  "# Author, Date, (Optional)Nickname\n# Image Location, Type\n# Width, Height\n";//What to write to the tileset info file.
		
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
		Date dateObject = new Date();
		fileOutput += "# Last Date Update: " + dateFormat.format(dateObject) + "\n";//timestamp
		
		fileOutput += "Toady, null Default\n/curses_640x300.png\n8 12\n";//Add three default tilesets
		fileOutput += "Toady, null\n/curses_800x600.png\n10 12\n";
		fileOutput += "Toady, null DefaultSquare\n/curses_square_16x16.png\n16 16\n";
		
		for (Template t : templates) {
			//Get template values.
			String author = getParameterValue(t, "author");
			String date = getParameterValue(t, "dated");
			String image = getParameterValue(t, "image");
			String size = getParameterValue(t, "size");
			
			//Parse template further.
			String imageName;
			image = image.replace("%27", "'");
			if (image.contains(":Image:")) {
				imageName = image.substring(image.indexOf("Image:"), image.indexOf("|"));
			} else {
				if (image.contains("Image:")) {
					imageName = image.substring(image.indexOf("Image:"), image.indexOf("]]"));
				} else {
					imageName = image.substring(image.indexOf("File:"), image.indexOf("]]"));
				}
			}
			if (imageName.contains("jolly")) {
				continue;//Bad tileset.
			}
			
			//Switch image type to .png
			String alteredName = imageName.substring(0, imageName.indexOf(".")) + ".png";
			String imagePath = "/" + alteredName.substring(alteredName.indexOf(":")+1);
			
			//Fix author names
			if (author.contains("[[")) {
				if (author.contains("|")) {
					author = author.substring(author.indexOf(":") + 1, author.indexOf("|")-1);
				} else {
					author = author.substring(author.indexOf(":") + 1, author.indexOf("]]")-1);
				}
			}
			
			//Fix date created
			if (date != null) {
				if (date.contains(" ")) {
					date = date.substring(0, date.indexOf(" ")).trim();
				}
			} else {
				date = "null";
			}
			
			//Avoid comments contained in ()
			if (size.contains("(")) {
				size = size.substring(0, size.indexOf("(")).trim();
			}
			if (size.contains(",")) {
				size = size.substring(0, size.indexOf(",")).trim();
			}
			String deliminator = "";//For some reason people use multiple x ASCII characters.
			if (size.contains("×")) {
				deliminator = "×";
			} else {
				deliminator = "x";
			}
				
			int width = Integer.parseInt(size.substring(0, size.indexOf(deliminator)));
			int height = Integer.parseInt(size.substring(size.indexOf(deliminator)+1));
			
			//Add onto file output.
			fileOutput += author + ", " + date + "\n";
			fileOutput += imagePath + "\n";
			fileOutput += width + " " + height + "\n";
			
			//download image
			String directUrl = "";
			try {
				directUrl = getDirectImageURL(new PageLocation(imageName, "dw"));
				System.out.println(directUrl);
				
				BufferedImage png = null;
				try {
					URL url = new URL(directUrl);
					png = ImageIO.read(url);
					saveImage(png, "src/main/resources/Tilesets" + imagePath);
					try {
						BufferedImage tilesetImg = Main.loadImage("/Tilesets" + imagePath);
					} catch (Throwable e) {
						//Corrupted image. Halt code.
						e.printStackTrace();
						throw new Error("Stopping because somehow images are corrupted.", e);
					}
				} catch (IOException e) {
					System.out.println("IOError");
				}
			} catch (NullPointerException e) {
				System.out.println("Error!!");
				System.out.println(new PageLocation(image.substring(2, image.length()-2), "dw"));
			}
			
			//Easier on the wiki.
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// Just continue running. The wait isn't so critical we need to worry about it being interrupted
			}
		}
		
		writeFile(fileOutput, "src/main/resources" + TILESET_INFO_FILE);
	}
	
	public String getParameterValue(Template t, String parameterName) {
		for (int i = 0; i < t.getNumParameters(); i++) {
			String parameter = t.getParameter(i);
			if (parameter.contains(parameterName + "=")) {
				return parameter.substring(parameter.indexOf("=")+1).replace("\n", "").trim();
			}
		}
		return null;
	}
	
	public ArrayList<Tileset> getTilesets() {
		ArrayList<Tileset> tilesets = new ArrayList<Tileset>();
		
		ArrayList<String> rawTilesetData = readFileAsList(TILESET_INFO_FILE, "#", true, true);
		for (int row = 0; row < rawTilesetData.size(); row += 3 ) {
			//# Name, Author, (Optional)Nickname
			//# Image Location
			//# Width, Height
			String identifier = rawTilesetData.get(row);
			String path = rawTilesetData.get(row+1);
			String size = rawTilesetData.get(row+2);
			
			int spaceIndex2 = identifier.indexOf(" ", identifier.indexOf(",")+2);//Index of second space in identifier line.
			boolean hasNickname = spaceIndex2 != -1;//Checks to see if there are two spaces.
			
			String author = identifier.substring(0, identifier.indexOf(","));
			String dateCreated = null;
			String nickname = null;
			if (hasNickname) {
				dateCreated = identifier.substring(identifier.indexOf(",") + 2, spaceIndex2);
				nickname = identifier.substring(spaceIndex2 + 1);
			} else {
				dateCreated = identifier.substring(identifier.indexOf(",") + 2);
			}
			if (dateCreated.equals("null")) {
				dateCreated = null;
			}
			
			String imagePath = path;
			
			int width = Integer.parseInt(size.substring(0, size.indexOf(" ")));
			int height = Integer.parseInt(size.substring(size.indexOf(" ") + 1));
			
			Tileset tileset = new Tileset(imagePath, author, nickname, dateCreated, width, height, tilesets.size());
			
			tilesets.add(tileset);
		}
		
		return tilesets;
	}
	
	public static void writeFile(String text, String location) {
		PrintWriter writer = null;
		try {
			System.out.println(location);
			writer = new PrintWriter(location, "UTF-8");
		} catch (FileNotFoundException e) {
			System.out.println("Err1 File Not Found");
			return;
		} catch (UnsupportedEncodingException e) {
			System.out.println("Err2 Unsupported file format");
			return;
		}
		writer.write(text);
		writer.close();
	}
	
	public ArrayList<String> readFileAsList(String location, String commentHeader, boolean comments, boolean ignoreBlankLines) {
		try {
			// Read in the file!
			InputStream in = getClass().getResourceAsStream(location);
			BufferedReader br = new BufferedReader(
						new InputStreamReader(in, "UTF-8")
					);

			
			// Gather array size
			ArrayList<String> lines = new ArrayList<String>();
			
			// Parse file array into java int array
			String line;
			line = br.readLine();
			do {
				if (comments && (line.length() > commentHeader.length() && line.substring(0,commentHeader.length()).equals(commentHeader))) {
					//We have a comment. Ignore it.
				} else if (ignoreBlankLines && line.length() == 0) {
					//We have an empty line.
				} else {
					lines.add(line);
				}
				line = br.readLine();
			} while (line != null);
			
			in.close();
			br.close();
			
			return lines;
			
		} catch (IOException e) {
			System.out.println("Error reading in list.");
		}
		return null;
	}
	
	public static void saveImage(BufferedImage img, String title) {
		try {
			// retrieve image
			BufferedImage bi = img;
			File outputfile = new File(title);
			boolean parentPathExists = true;
			if (!outputfile.getParentFile().isDirectory()) {
				parentPathExists = outputfile.mkdirs();
			}
			if (parentPathExists) {
				ImageIO.write(bi, "png", outputfile);
			} else {
				throw new Error();
			}
		} catch (IOException|Error e) {
			e.printStackTrace();
			throw new Error("Could not write to a file.", e);
		}
	}

	public void printTilesets() {
		ArrayList<Tileset> tilesets = getTilesets();
		for (int i = 0; i < tilesets.size(); i++) {
			Tileset tile = tilesets.get(i);
			System.out.println("#" + i + ": " + tile.getImagePath()
					+ "\n\tAuthor: " + tile.getAuthor()
					+ "\n\tWidth: " + tile.getTileWidth()
					+ "\n\tHeight: " + tile.getTileHeight()
					+ "\n");

		}
	}
}
