package plugins.SiteToolPlugin.sessions;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Executor;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public abstract class AbstractSiteToolSession {
	
	public enum SessionStatus { IDLE, WAITING, RUNNING, STOPPING, DONE, ERROR };
	
	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(AbstractSiteToolSession.class);
	}

	private final Object lock = new Object();

	private final String sessionID;
	private SessionStatus sessionStatus;
	private Throwable lastError;

	protected AbstractSiteToolSession(String identifier) {
		sessionID = identifier;
		sessionStatus = SessionStatus.IDLE;
	}

	public abstract boolean canRetry();

	protected abstract void execute();

	public abstract void cancel();

	public abstract HTMLNode getExtraStatusPanel();

	public abstract void destroySession();

	public abstract void handleFCP(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException;

	public SessionStatus getStatus() {
		return sessionStatus ;
	}

	public String getSessionID() {
		return sessionID;
	}

	private void setError(Throwable t) {
		sessionStatus = SessionStatus.ERROR;
		lastError =t;
	}

	public final void startSession(Executor executor) {
		if (!checkStart())
			throw new IllegalStateException();
		sessionStatus = SessionStatus.WAITING;
		executor.execute(new Runnable() {
			public void run() {
				sessionStatus = SessionStatus.RUNNING;
				try {
					execute();
				} catch (Exception e) {
					Logger.error(this, "debug", e);
					setError(e);
				}
			}
		});
	}

	public final void cancelSession(Executor executor) {
		if (!checkCancel())
			throw new IllegalStateException();
		sessionStatus = SessionStatus.STOPPING;
		executor.execute(new Runnable() {
			public void run() {
				try {
					cancel();
					sessionStatus = SessionStatus.DONE;
				} catch (Exception e) {
					Logger.error(this, "debug", e);
					setError(e);
				}
			}
		});
	}

	private boolean checkStart() {
		if (sessionStatus == SessionStatus.IDLE) return true;
		if ((sessionStatus == SessionStatus.DONE) || (sessionStatus == SessionStatus.ERROR)) return canRetry();
		return false;
	}

	private boolean checkCancel() {
		if ((sessionStatus == SessionStatus.RUNNING) || (sessionStatus == SessionStatus.WAITING)) return true;
		return false;
	}

	public boolean checkRemove() {
		if ((sessionStatus == SessionStatus.DONE) || (sessionStatus == SessionStatus.ERROR) || (sessionStatus == SessionStatus.IDLE)) return true;
		return false;
	}
}
