package Code;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.Random;

public class ThreadCallable implements Callable<ArrayList<TilesetDetected>> {
	private int start, length;
	private TileFitter fitter;
	
	public ThreadCallable(int _start, int _length, TileFitter _fitter) {
		start = _start;
		length = _length;
		fitter = _fitter;
	}
	
	@Override
	public ArrayList<TilesetDetected> call() throws Exception {
		ArrayList<TilesetDetected> mObjs = new ArrayList<>();
		Random threadRandom = new Random();
		ArrayList<Tileset> tilesets = fitter.getTilesets();
		for (int i = start; i < start + length; i++) {
			mObjs.add(fitter.matchForTileset(tilesets.get(i), threadRandom));
			Main.incrementNumTilesetChecksComplete();
		}
		return mObjs;
	}
}
