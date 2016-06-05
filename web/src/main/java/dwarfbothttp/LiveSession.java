package dwarfbothttp;

import Code.DecodedImage;
import Code.Tileset;
import Code.TilesetDetected;
import Code.TilesetFitter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import com.google.gson.Gson;
import spark.Request;

/**
 * @author Jacob Sims
 */
public class LiveSession {
	private static Gson gson; // Says it is thread safe.

	private BufferedImage toConvert;
	private Thread conversionMainThread;
	private TilesetFitter fitter;
	private AtomicInteger stage;
	private TilesetDetected tilesetDetected;
	private DecodedImage decodedImage;
	private boolean decodingHasStarted;
	private AtomicBoolean decodingFinished;
	private String id;
	private Integer seedx;
	private Integer seedy;

	public LiveSession() {
		decodingFinished = new AtomicBoolean(false);
		seedx = null; // These should be null by default, but who knows what Java's magic will do?
		seedy = null; // We can't have these initialized to 0.
	}

	public LiveSession(BufferedImage _toConvert, TilesetDetected _tilesetDetected, DecodedImage _decodedImage, int _seedx, int _seedy, boolean _decodingFinished) {
		this();
		decodingHasStarted = true;
		toConvert = _toConvert;
		tilesetDetected = _tilesetDetected;
		decodedImage = _decodedImage;
		seedx = _seedx;
		seedy = _seedy;
		decodingFinished = new AtomicBoolean(_decodingFinished);
		fitter = new TilesetFitter(Session.getSupportedTilesets(), false);
		fitter.setToConvert(toConvert);
	}

	public void setImageFromUpload(Request request) throws UploadFailedException {
		request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement((String)null));
		try (InputStream inputStream = request.raw().getPart("to_convert").getInputStream()) {
			toConvert = ImageIO.read(inputStream);
			toConvert.getData();
		} catch (IOException|ServletException|NullPointerException e) {
			throw new UploadFailedException("Could not set image for the session from your upload.", e);
		}
	}

	public void startDecoding() {
		if (decodingHasStarted) {
			// No need to start it over.
			return;
		}
		decodingHasStarted = true;
		stage = new AtomicInteger(0);
		fitter = new TilesetFitter(Session.getSupportedTilesets(), false);
		conversionMainThread = new Thread(() -> {
			//TODO: Allow `artistic` mode (checkbox in the first form)
			fitter.loadImageForConverting(toConvert);
			stage.incrementAndGet();
			tilesetDetected = fitter.extractTileset();
			stage.incrementAndGet();
			decodedImage = fitter.readTiles(toConvert, tilesetDetected.getBasex(), tilesetDetected.getBasey(), tilesetDetected.getTileset());
			stage.incrementAndGet();
			decodingFinished.set(true);
		});
		conversionMainThread.start();
	}

	public String statusJson() {
		HashMap<String, Integer> statusMap = new HashMap<>();
		if (decodingFinished != null && decodingFinished.get()) {
			statusMap.put("loadImageForConverting", 100);
			statusMap.put("extractTileset", 100);
			statusMap.put("readTiles", 100);
		} else {
			statusMap.put("loadImageForConverting", ((stage != null && stage.get() > 0) ? 100 : 0));
			statusMap.put("extractTileset", 100 * fitter.getNumTilesetChecksComplete() / Session.getSupportedTilesets().size());
			statusMap.put("readTiles", ((stage != null && stage.get() > 2) ? 100 : 0));
		}
		return gson.toJson(statusMap);
	}

	public BufferedImage getToConvert() {
		return toConvert;
	}

	public boolean isDecodingFinished() {
		return decodingFinished.get();
	}

	public BufferedImage renderToTileset(Tileset tileset) {
		if (!isDecodingFinished()) {
			throw new IllegalStateException("Cannot render an image if it is not decoded yet!");
		}
		int tilesetId = -1;
		for (int i = 0; i < Session.getSupportedTilesets().size(); i++) {
			if (tileset == Session.getSupportedTilesets().get(i)) {
				tilesetId = i;
				break;
			}
		}
		if (tilesetId == -1) {
			throw new IllegalArgumentException("Tileset not on the list of supported tilesets");
		}
		return fitter.renderImage(decodedImage, tilesetId);
	}

	public TilesetDetected getTilesetDetected() {
		return tilesetDetected;
	}

	public DecodedImage getDecodedImage() {
		return decodedImage;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public int getSeedx() {
		if (seedx != null) {
			return seedx.intValue();
		} else {
			return fitter.getSeedx();
		}
	}

	public int getSeedy() {
		if (seedy != null) {
			return seedy.intValue();
		} else {
			return fitter.getSeedy();
		}
	}

	static {
		gson = new Gson();
	}
}
