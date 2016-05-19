package Code;

/**
 * Created by jacob on 2016-05-18.
 */
public class MatchObject {
	private int matchCount;
	private int basex;
	private int basey;

	public MatchObject(int _matchCount, int _basex, int _basey) {
		matchCount = _matchCount;
		basex = _basex;
		basey = _basey;
	}

	public int getMatchCount() {
		return matchCount;
	}

	public int getBasex() {
		return basex;
	}

	public int getBasey() {
		return basey;
	}
}
