package Code;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Callable;

public class ThreadCallable implements Callable<ArrayList<TilesetDetected>> {
	private int start, length;
	private TilesetFitter fitter;
	
	public ThreadCallable(int _start, int _length, TilesetFitter _fitter) {
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
			TilesetFitter.incrementNumTilesetChecksComplete();
		}
		return mObjs;
	}
}
