package Code;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

public class ThreadCallable implements Callable<ArrayList<TilesetDetected>> {
	private int start, length;
	private ArrayList<Tileset> allTilesets;
	private BufferedImage toConvert;
	
	public ThreadCallable(int _start, int _length, ArrayList<Tileset> _allTilesets, BufferedImage _toConvert) {
		start = _start;
		length = _length;
		allTilesets = _allTilesets;
		toConvert = _toConvert;
	}
	
	@Override
	public ArrayList<TilesetDetected> call() throws Exception {
		ArrayList<TilesetDetected> mObjs = new ArrayList<>();
		Random threadRandom = new Random();
		for (int i = start; i < start + length; i++) {
			mObjs.add(Main.matchForTileset(allTilesets.get(i), threadRandom, toConvert));
			Main.incrementNumTilesetChecksComplete();
		}
		return mObjs;
	}
}
