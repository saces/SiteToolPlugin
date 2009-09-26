package plugins.SiteToolPlugin;

import java.util.HashMap;
import java.util.Set;

import plugins.SiteToolPlugin.exception.DuplicateSessionIDException;
import plugins.SiteToolPlugin.sessions.AbstractSiteToolSession;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Executor;
import freenet.support.Logger;

public class SessionManager {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(SessionManager.class);
	}

	private final HashMap<String, AbstractSiteToolSession> _sessions;
	private final Executor _executor;
	
	SessionManager(Executor executor) {
		_sessions = new HashMap<String, AbstractSiteToolSession>();
		_executor = executor;
	}

	public AbstractSiteToolSession getSession(String identifier) {
		return _sessions.get(identifier);
	}
	
	public Set<String> getSessionNames() {
		return _sessions.keySet();
	}

	public synchronized void kill() {
		for (AbstractSiteToolSession session:_sessions.values()) {
			session.destroySession();
		}
		_sessions.clear();
	}

	public AbstractSiteToolSession newSession(PluginReplySender replysender, String identifier) {
		AbstractSiteToolSession session = null; //;new SiteToolSession(replysender, identifier);
		synchronized (_sessions) {
			_sessions.put(identifier, session);
		}
		return session;
	}

	public void addSession(AbstractSiteToolSession session) throws DuplicateSessionIDException {
		String key = session.getSessionID();
		if (_sessions.containsKey(key))
			throw new DuplicateSessionIDException(key);
		_sessions.put(key, session);
	}

	public void startSession(String sessionID) {
		AbstractSiteToolSession session = _sessions.get(sessionID);
		session.startSession(_executor);
	}

	public void removeSession(String sessionID) {
		AbstractSiteToolSession session = _sessions.get(sessionID);
		if (!session.checkRemove())
			throw new IllegalStateException();
		_sessions.remove(sessionID);
	}
}
