package dwarfbothttp;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.naming.ConfigurationException;

import Code.Tileset;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.template.velocity.VelocityTemplateEngine;

public class Main {
	private static SessionManager sessionManager;
	private static SlackPosterBot slackPosterBot;
	private static boolean failureReportsEnabled;
	public static final String CONFIGFILE_FILENAME = "config.json";

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");

		addInterruptHandler();

		sessionManager = new SessionManager();
		loadPreviouslyArchivedSessions();

		slackPosterBot = null;
		try {
			slackPosterBot = new SlackPosterBot();
			failureReportsEnabled = true;
		} catch (ConfigurationException e) {
			e.printStackTrace();
			System.out.println("Warning: Could not initialize SlackPosterBot. This is normal if you haven't configured it.");
			System.out.println("Continuing without Slack functionality.");
			failureReportsEnabled = false;
		}

		Code.Main.setupLogger();
		Code.Main.logger.getHandlers()[0].setLevel(Level.WARNING);

		Spark.staticFiles.location("/static");
		VelocityTemplateEngine velocityTemplateEngine = new VelocityTemplateEngine();

		Spark.get("/", (request, response) -> {
			HashMap<String, Object> model = new HashMap<String, Object>();
			return new ModelAndView(model, "index.vm");
		}, velocityTemplateEngine);
		Spark.post("/upload", (request, response) -> {
			String sessionId = sessionManager.addNewSession();
			Session s = sessionManager.get(sessionId);

			try {
				s.setImageFromUpload(request);
			} catch (UploadFailedException e) {
				errorOutResponse(400, "Uploading the image failed.");
			}

			response.redirect("/" + sessionId + "/convertpage");
			return response;
		});
		Spark.get("/:session/uploadedimage.png", (request, response) -> {
			String sessionId = getSessionIdForRequest(request, response);
			Session s = sessionManager.get(sessionId);

			response.type("image/png");
			OutputStream outputStream = response.raw().getOutputStream();
			ImageIO.write(s.getToConvert(), "png", outputStream);

			return response;
		});
		Spark.get("/:session/convertpage", (request, response) -> {
			String sessionId = getSessionIdForRequest(request, response);
			Session s = sessionManager.get(sessionId);
			s.startDecoding();

			HashMap<String, Object> model = new HashMap<String, Object>();
			model.put("session", sessionId);
			return velocityTemplateEngine.render(new ModelAndView(model, "convertpage.vm"));
		});
		Spark.get("/:session/encodeimage", (request, response) -> {
			String sessionId = getSessionIdForRequest(request, response);
			Session s = sessionManager.get(sessionId);
			if (!s.isDecodingFinished()) {
				errorOutResponse(400, "Your image is not decoded yet! Be patient.");
			}

			HashMap<String, Object> model = new HashMap<String, Object>();
			model.put("session", sessionId);
			model.put("tilesets", Session.getSupportedTilesets());
			model.put("failureReportsEnabled", failureReportsEnabled);
			return velocityTemplateEngine.render(new ModelAndView(model, "encodeimage.vm"));
		});
		Spark.get("/:session/encodedimage.png", (request, response) -> {
			String sessionId = getSessionIdForRequest(request, response);
			Session s = sessionManager.get(sessionId);
			Tileset tileset = Session.tilesetWithPath(request.queryParams("tileset"));

			BufferedImage renderedImage = null;
			try {
				renderedImage = s.renderToTileset(tileset);
			} catch (IllegalArgumentException e) {
				errorOutResponse(404, "Tileset not found!");
			} catch (IllegalStateException e) {
				errorOutResponse(400, "Your image is not decoded yet! Be patient.");
			}

			if (renderedImage == null) {
				errorOutResponse(500, "Internal server error");
			}

			response.type("image/png");
			OutputStream outputStream = response.raw().getOutputStream();
			ImageIO.write(renderedImage, "png", outputStream);

			return response;
		});
		Spark.get("/:session/decodingstatus.json", (request, response) -> {
			String sessionId = getSessionIdForRequest(request, response);
			Session s = sessionManager.get(sessionId);
			response.type("application/json");
			return s.statusJson();
		});
		Spark.post("/:session/submitfailurereport", (request, response) -> {
			String sessionId = getSessionIdForRequest(request, response);
			Session s = sessionManager.get(sessionId);
			try {
				slackPosterBot.submitFailureReport(s.createFailureReport());
			} catch (IOException e) {
				return "Failed to report failure :(";
			}
			return "Submitted.";
		});
		Spark.get("/gettilesetimage.png", (request, response) -> {
			String param = request.queryParams("tilesetpath");
			Tileset tileset = Session.tilesetWithPath(param);
			if (tileset == null) {
				errorOutResponse(404, "Tileset not found");
			}

			response.type("image/png");
			OutputStream outputStream = response.raw().getOutputStream();
			ImageIO.write(tileset.loadImage(), "png", outputStream);
			return response;
		});
	}

	private static void loadPreviouslyArchivedSessions() {
		for (ArchivedSession archivedSession : ArchivedSession.retrieveAllFromConfigDir()) {
			sessionManager.addExistingSession(archivedSession.getId(), new Session(archivedSession));
		}
	}

	private static void addInterruptHandler() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			Spark.stop();
			sessionManager.archiveAll();
		}));
	}

	private static void errorOutResponse(int status, String message) {
		//TODO: Make this more friendly.
		String responseBody = message;

		// Throws an exception that stops execution of the current route.
		Spark.halt(status, responseBody);
	}

	private static String getSessionIdForRequest(Request request, Response response) {
		String id = request.params(":session");
		if (id == null || sessionManager.get(id) == null) {
			response.redirect("/");
		}
		return id;
	}

	public static File getConfigDir() {
		String path = System.getProperty("user.home") + File.separator + ".config" + File.separator + "dwarfbothttp";
		File file = new File(path);
		if (!file.isDirectory()) {
			if (!file.mkdirs()) {
				throw new Error("Could not create config directory");
			}
		}
		return file;
	}
}
