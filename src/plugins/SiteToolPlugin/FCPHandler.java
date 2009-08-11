package plugins.SiteToolPlugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

import plugins.KeyExplorer.KeyExplorerUtils;
import freenet.client.Metadata;
import freenet.client.MetadataParseException;
import freenet.keys.FreenetURI;
import freenet.node.LowLevelGetException;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class FCPHandler {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(FCPHandler.class);
	}


	private final SessionManager sessionManager;
	private final PluginRespirator pluginRespirator;

	FCPHandler(SessionManager sessionManager, PluginRespirator pluginRespirator) {
		this.sessionManager = sessionManager;
		this.pluginRespirator= pluginRespirator; 
	}

	public void kill() {
		// TODO Auto-generated method stub
		
	}

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException {

		if (logDEBUG) {
			Logger.debug(this, "Got Message: " + params.toOrderedString());
		}

		final String identifier = params.get("Identifier");
		if (identifier == null || identifier.trim().length() == 0) {
			FCPHandler.sendError(replysender, STFCPException.MISSING_IDENTIFIER, "<invalid>", "Empty identifier!");
			return;
		}

		final String command = params.get("Command");
		if (command == null || command.trim().length() == 0) {
			FCPHandler.sendError(replysender, STFCPException.MISSING_COMMAND, identifier, "Empty Command name");
			return;
		}

		if ("Ping".equals(command)) {
			SimpleFieldSet sfs = new SimpleFieldSet(true);
			sfs.put("Pong", System.currentTimeMillis());
			sfs.putSingle("Identifier", identifier);
			replysender.send(sfs);
			return;
		}

		if ("UpdateSite".equals(command)) {
			FCPHandler.sendNOP(replysender, identifier);
			return;
		}

		SiteToolSession session = sessionManager.getSession(identifier);

		if ("NewSession".equals(command)) {
			if (session != null) {
				FCPHandler.sendError(replysender, STFCPException.DUPLICATE_SESSION, identifier, "Session already exists.");
				return;
			}
			session = sessionManager.newSession(replysender, identifier);
			FCPHandler.sendSuccess(replysender, identifier, "New Session created");
			return;
		}

		if ("OpenSession".equals(command)) {
			if (session != null) {
				FCPHandler.sendError(replysender, STFCPException.DUPLICATE_SESSION_RUNNING, identifier, "Session already started.");
				return;
			}

			if (logDEBUG) {
				Logger.debug(this, "Open Session: " + identifier);
			}

			FreenetURI oldUri;
			try {
				oldUri = new FreenetURI(params.get("OldURI"));
			} catch (MalformedURLException e) {
				FCPHandler.sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' invalid: "+e.getMessage());
				return;
			}
			if (oldUri == null) {
				FCPHandler.sendError(replysender, STFCPException.MISSING_OLDURI, identifier, "Parameter 'OldURI' (previous edition) missing for session.");
				return;
			}

			if (logDEBUG) {
				Logger.debug(this, "Open Session: uri seems valid" + oldUri.toString(false, false));
			}

			Metadata md;
			try {
				md = KeyExplorerUtils.simpleManifestGet(pluginRespirator, oldUri);
			} catch (MetadataParseException e) {
				Logger.error(this, "Parameter 'OldURI' failed to fetch.", e);
				FCPHandler.sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' failed to fetch: "+e.getMessage());
				return;
			} catch (IOException e) {
				Logger.error(this, "Parameter 'OldURI' failed to fetch.", e);
				FCPHandler.sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' failed to fetch: "+e.getMessage());
				return;
			} catch (LowLevelGetException e) {
				Logger.error(this, "Parameter 'OldURI' failed to fetch.", e);
				FCPHandler.sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' failed to fetch: "+e.getMessage());
				return;
			}
			
			
			// convert metadata
			HashMap<String, Object> newMD;
			try {
				newMD = KeyExplorerUtils.parseMetadata(md, oldUri);
			} catch (MalformedURLException e) {
				FCPHandler.sendError(replysender, STFCPException.INTERNAL, identifier, "Internal Error: "+e.getMessage());
				return;
			}
			
			// start session
			//STSession session2 = new STSession(replysender, identifier, oldUri, newMD);
//			synchronized (sessions) {
//				sessions.put(identifier, session2);
//			}
			FCPHandler.sendSuccess(replysender, identifier, "Session opened");
			return;
		}
		
		if (session == null) {
			FCPHandler.sendError(replysender, STFCPException.INVALID_SESSION, identifier, "No such session.");
			return;
		}
		
		if ("EndSession".equals(command)) {
			boolean kill = params.getBoolean("Kill", false);
			if (session.endSession(kill)) {
//				synchronized (sessions) {
//					sessions.remove(identifier);
//				}
				FCPHandler.sendSuccess(replysender, identifier, "Session endet.");
			} else {
				FCPHandler.sendError(replysender, STFCPException.SESSION_CANTSTOP, identifier, "Failed stopping session.");
			}
			return;
		}
		
		if ("AttachSession".equals(command)) {
			session.attachSession(replysender, identifier);
			return;
		}
		
		if ("AddItem".equals(command)) {
			String name = params.get("Name");
			String mime = params.get("ContentType");
			session.addItem(replysender, name, mime, data, false, true);
			return;
		}
		
		if ("SetDefaultItem".equals(command)) {
			FCPHandler.sendNOP(replysender, identifier);
			return;
		}
		
		if ("DeleteItem".equals(command)) {
			FCPHandler.sendNOP(replysender, identifier);
			return;
		}
		
		if ("ModifyItem".equals(command)) {
			String name = params.get("Name");
			String mime = params.get("ContentType");
			//session.addItem(replysender, name, mime, data, true, false);
			return;
		}
		
		if ("CommitSession".equals(command)) {
			FreenetURI insertUri;
			try {
				insertUri = new FreenetURI(params.get("InsertURI"));
			} catch (MalformedURLException e) {
				FCPHandler.sendError(replysender, STFCPException.INVALID_INSERTURI, identifier, "Parameter 'OldURI' invalid: "+e.getMessage());
				return;
			}
			if (insertUri == null) {
				FCPHandler.sendError(replysender, STFCPException.MISSING_INSERTURI, identifier, "Parameter 'OldURI' (previous edition) missing for session.");
				return;
			}
			//session.commit_test(this, replysender, insertUri, pr);
			return;
		}
		
		if ("HealFile".equals(command)) {
			FCPHandler.sendNOP(replysender, identifier);
			return;
		}
		
		if ("HealDir".equals(command)) {
			FCPHandler.sendNOP(replysender, identifier);
			return;
		}
		
		if ("ListManifest".equals(command)) {
			session.listItems(replysender, identifier);
			return;
		}
		
		FCPHandler.sendError(replysender, STFCPException.INVALID_COMMAND, identifier, "Unknown command");
	}

	public static void sendNOP(PluginReplySender replysender, String identifier) throws PluginNotFoundException {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putSingle("Status", "Error");
		sfs.put("Code", -1);
		sfs.putSingle("Identifier", identifier);
		sfs.putSingle("Description", "Not implemented yet");
		replysender.send(sfs);
	}

	static void sendSuccess(PluginReplySender replysender, String identifier, String description) throws PluginNotFoundException {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Status", "Success");
		sfs.put("Code", 0);
		sfs.putSingle("Identifier", identifier);
		sfs.putSingle("Description", description);
		replysender.send(sfs);
	}

	static void sendError(PluginReplySender replysender, int code, String identifier,
			String description) throws PluginNotFoundException {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putSingle("Status", "Error");
		sfs.put("Code", code);
		sfs.putSingle("Identifier", identifier);
		sfs.putSingle("Description", description);
		replysender.send(sfs);
	}

	public static void sendProgress(PluginReplySender replysender,  String identifier, String description) throws PluginNotFoundException {
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putSingle("Status", "Progress");
		sfs.putSingle("Identifier", identifier);
		sfs.putSingle("Description", description);
		replysender.send(sfs);
	}
}
