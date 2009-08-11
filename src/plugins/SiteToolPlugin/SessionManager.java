package plugins.SiteToolPlugin;

import java.util.HashMap;

import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;

public class SessionManager {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(SessionManager.class);
	}


	private final HashMap<String, SiteToolSession> sessions;

	SessionManager() {
		sessions = new HashMap<String, SiteToolSession>();
	}

	SiteToolSession getSession(String identifier) {
		synchronized (sessions) {
			return sessions.get(identifier);
		}
	}

	public void kill() {
		for (SiteToolSession session:sessions.values()) {
			session.killSession();
		}
		sessions.clear();
	}

	public SiteToolSession newSession(PluginReplySender replysender, String identifier) {
		SiteToolSession session = new SiteToolSession(replysender, identifier);
		synchronized (sessions) {
			sessions.put(identifier, session);
		}
		return session;
	}

}
