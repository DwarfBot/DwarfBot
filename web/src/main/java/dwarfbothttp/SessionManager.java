package dwarfbothttp;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author Jacob Sims
 */
public class SessionManager {
	private static Random random;
	private HashMap<String, Session> sessionMap;

	public SessionManager() {
		this(new HashMap<String, Session>());
	}

	public SessionManager(HashMap<String, Session> _sessionMap) {
		sessionMap = _sessionMap;
	}

	public HashMap<String, Session> getSessionMap() {
		return sessionMap;
	}

	public String addNewSession() {
		String id = createSessionId();
		sessionMap.put(id, new Session(id));
		return id;
	}

	public void addExistingSession(String id, Session session) {
		sessionMap.put(id, session);
	}

	public boolean archiveAll() {
		boolean allSucceeded = true;
		for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
			boolean succeeded = entry.getValue().archive();
			allSucceeded = allSucceeded && succeeded;
		}
		return allSucceeded;
	}

	public Session get(String k) {
		return sessionMap.get(k);
	}

	private String createSessionId() {
		MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			//This should never happen! http://docs.oracle.com/javase/7/docs/api/java/security/MessageDigest.html
			throw new Error("Java is messed up", e);
		}
		String beforeHash = Long.toString(System.nanoTime()) + ':' + random.nextInt();
		messageDigest.update(beforeHash.getBytes(Charset.defaultCharset()));
		String sessionId = DatatypeConverter.printHexBinary(messageDigest.digest()).substring(0, 16).toLowerCase();
		if (sessionMap.containsKey(sessionId)) {
			return createSessionId();
		} else {
			return sessionId;
		}
	}

	static {
		random = new Random();
	}
}
