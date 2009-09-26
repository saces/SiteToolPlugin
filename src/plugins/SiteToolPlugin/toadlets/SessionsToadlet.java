package plugins.SiteToolPlugin.toadlets;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import plugins.SiteToolPlugin.SessionManager;
import plugins.SiteToolPlugin.SiteToolPlugin;
import plugins.SiteToolPlugin.sessions.AbstractSiteToolSession;
import plugins.SiteToolPlugin.sessions.AbstractSiteToolSession.SessionStatus;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class SessionsToadlet extends WebInterfaceToadlet {

	private static final String PARAM_SESSIONID = "sessionid";

	private static final String CMD_KILL9 = "killneun";
	private static final String CMD_START = "start";
	private static final String CMD_CANCEL = "cancel";
	private static final String CMD_REMOVE = "remove";

	private final SessionManager sessionMgr;

	public SessionsToadlet(PluginContext stCtx, SessionManager sessionManager) {
		super(stCtx, SiteToolPlugin.PLUGIN_URI, "Sessions");
		sessionMgr = sessionManager;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		String path = normalizePath(req.getPath());

		if (path.equals("/")) {
			makeSessionsPage(ctx);
			return;
		}

		writeTemporaryRedirect(ctx, "Found elsewhere", path());
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		if (!isFormPassword(request)) {
			sendErrorPage(ctx, 403, "Forbidden", "Invalid form password.");
			return;
		}

		String path = normalizePath(request.getPath());

		if (!path.equals("/")) {
			sendErrorPage(ctx, 404, "Not found", "The path '"+uri+"' was not found");
			return;
		}
		String sessionid = request.getPartAsString(PARAM_SESSIONID, 1024);
		AbstractSiteToolSession session = sessionMgr.getSession(sessionid);
		if (request.isPartSet(CMD_START)) {
			session.startSession(pluginContext.clientCore.getExecutor());
		} else if (request.isPartSet(CMD_CANCEL)) {
			session.cancelSession(pluginContext.clientCore.getExecutor());
		} else if (request.isPartSet(CMD_REMOVE)) {
			sessionMgr.removeSession(sessionid);
		} else {
			sendErrorPage(ctx, 400, "Bad request", "Malformed Request, no proper command");
			return;
		}

		writeTemporaryRedirect(ctx, "Found elsewhere", path() + "/konquitrick");
	}

	private void makeSessionsPage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Site Tool Plugin", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;
		contentNode.addChild(createSessionsBox());
		
		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createSessionsBox() {
		InfoboxNode box = pluginContext.pageMaker.getInfobox("Sessions");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		Set<String> names = sessionMgr.getSessionNames();

		if (names.isEmpty()) {
			browseContent.addChild("#", "<Empty>");
		} else {
			for (String name:names) {
				browseContent.addChild(createSessionBox(name));
			}
		}
		return browseBox;
	}

	private HTMLNode createSessionBox(String name) {
		InfoboxNode box = pluginContext.pageMaker.getInfobox(name);
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		AbstractSiteToolSession session = sessionMgr.getSession(name);

		if (session == null) {
			browseContent.addChild("#", "<Null>");
			return browseBox;
		}

		HTMLNode kill9Form = pluginContext.pluginRespirator.addFormChild(browseContent, path(), "uriForm");
		kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", PARAM_SESSIONID, name });
		// allways a 'Kill -9' button. at least helpful while development
		kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_KILL9, "kill -9" });
		kill9Form.addChild("#", "\u00a0Try to remove session the hard way. Be carefully, this may hurt the node!");
		kill9Form.addChild("br");

		// the regular start/stop buttons depends on status
		SessionStatus status = session.getStatus();

		String statusstring;
		if (status == SessionStatus.IDLE) {
			statusstring = "Iddle";
			kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_START, "Start" });
			kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_REMOVE, "Remove" });
		} else if (status == SessionStatus.WAITING) {
			statusstring = "Started/Waiting";
			kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_CANCEL, "Cancel" });
		} else if (status == SessionStatus.RUNNING) {
			statusstring = "Running";
			kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_CANCEL, "Cancel" });
		} else if (status == SessionStatus.DONE) {
			statusstring = "Done";
			if (session.canRetry()) {
				kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_START, "Start" });
			}
			kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_REMOVE, "Remove" });
		} else if (status == SessionStatus.ERROR) {
			statusstring = "Error";
			if (session.canRetry()) {
				kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_START, "Start" });
			}
			kill9Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_REMOVE, "Remove" });
		} else {
			statusstring = "Invalid Session status";
		}

		InfoboxNode box2 = pluginContext.pageMaker.getInfobox(statusstring);
		HTMLNode extraBox = box2.outer;
		HTMLNode extraContent = box2.content;

		HTMLNode extra = session.getExtraStatusPanel();

		if (extra == null) {
			extraContent.addChild("#", "<Empty>");
		} else {
			extraContent.addChild(extra);
		}
		kill9Form.addChild(extraBox);

		return browseBox;
	}
}
