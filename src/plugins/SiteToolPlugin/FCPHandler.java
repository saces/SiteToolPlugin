package plugins.SiteToolPlugin;

import java.net.MalformedURLException;
import java.util.Set;

import plugins.SiteToolPlugin.exception.DuplicateSessionIDException;
import plugins.SiteToolPlugin.sessions.AbstractSiteToolSession;
import plugins.SiteToolPlugin.sessions.SiteEditSession;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.AbstractFCPHandler;
import freenet.support.plugins.helpers1.PluginContext;

public class FCPHandler extends AbstractFCPHandler {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(FCPHandler.class);
	}

	private final SessionManager sessionMgr;

	FCPHandler(SessionManager sessionManager, PluginContext pluginContext2) {
		super(pluginContext2);
		sessionMgr = sessionManager;
	}

	public void kill() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handle(PluginReplySender replysender, String command, String identifier, SimpleFieldSet params, Bucket data, int accesstype) throws PluginNotFoundException {

		if (logDEBUG) {
			Logger.debug(this, "Got Message: ("+command+") "+ params.toOrderedString());
		}

		// session less commands
		if ("IsInsertUSK".equals(command)) {
			FreenetURI testUri;
			try {
				testUri = new FreenetURI(params.get("URI"));
			} catch (MalformedURLException e) {
				sendError(replysender, STFCPException.INVALID_URI, identifier, "Invalid URI");
				return;
			}
			if (testUri.isSSKForUSK() || testUri.isUSK()) {
				byte[] extra = testUri.getExtra();
				boolean isPriv = (extra[1] == 1);
				SimpleFieldSet sfs = new SimpleFieldSet(true);
				sfs.putOverwrite("Status", "Success");
				sfs.put("Code", 0);
				sfs.putSingle("Identifier", identifier);
				sfs.putSingle("Description", "check successful done");
				sfs.put("IsInsertUSK", isPriv);
				replysender.send(sfs);
				return;
			}
			sendError(replysender, STFCPException.INVALID_URI, identifier, "Invalid URI");
			return;
		}

		if ("HGSanitizeURI".equals(command)) {
			FreenetURI testUri;
			FreenetURI baseUri;
			FreenetURI sUri;
			FreenetURI uUri;
			FreenetURI bsUri;
			FreenetURI buUri;
			long origEdition;
			try {
				testUri = new FreenetURI(params.get("URI"));
			} catch (MalformedURLException e) {
				sendError(replysender, STFCPException.INVALID_URI, identifier, "Invalid URI");
				return;
			}
			if (!(testUri.isSSKForUSK() || testUri.isUSK())) {
				sendError(replysender, STFCPException.INVALID_URI, identifier, "Invalid URI");
				return;
			}
			if (testUri.hasMetaStrings()) {
				testUri = testUri.setMetaString(null);
			}
			origEdition = testUri.getSuggestedEdition();

			baseUri = testUri.setSuggestedEdition(0);

			// turn USK into SSK
			if (testUri.isUSK()) {
				uUri = testUri;
				sUri = testUri.sskForUSK();
				buUri = baseUri;
				bsUri = baseUri.sskForUSK();
			} else {
				uUri = testUri.uskForSSK();
				sUri = testUri;
				buUri = baseUri.uskForSSK();
				bsUri = baseUri;
			}
			testUri = testUri.setSuggestedEdition(origEdition);
			
			byte[] extra = testUri.getExtra();
			boolean isPriv = (extra[1] == 1);
			SimpleFieldSet sfs = new SimpleFieldSet(true);
			sfs.putOverwrite("Status", "Success");
			sfs.put("Code", 0);
			sfs.putSingle("Identifier", identifier);
			sfs.putSingle("Description", "sanitize");
			sfs.put("IsPrivate", isPriv);
			sfs.put("Edition", origEdition);
			sfs.putSingle("OrigURI", testUri.toString(false, false));
			sfs.putSingle("BaseOrigURI", baseUri.toString(false, false));
			sfs.putSingle("USK", uUri.toString(false, false));
			sfs.putSingle("SSK", sUri.toString(false, false));
			sfs.putSingle("BaseUSK", buUri.toString(false, false));
			sfs.putSingle("BaseSSK", bsUri.toString(false, false));
			replysender.send(sfs);
			return;
		}

		// session control commands
		
		if ("ListSessions".equals(command)) {
			Set<String> ids = sessionMgr.getSessionNames();
			for (String id:ids) {
				SimpleFieldSet sfs = new SimpleFieldSet(true);
				sfs.putOverwrite("Status", "SessionListing");
				sfs.putSingle("SessionID", id);
				sfs.putSingle("Identifier", identifier);
				replysender.send(sfs);
			}
			SimpleFieldSet sfs = new SimpleFieldSet(true);
			sfs.putOverwrite("Status", "EndSessionListing");
			sfs.putSingle("Identifier", identifier);
			replysender.send(sfs, data);
			return;
		}

		String sessionID = params.get("SessionID");

		if (sessionID == null || sessionID.trim().length() == 0) {
			sendError(replysender, STFCPException.MISSING_SESSION_IDENTIFIER, identifier, "Missing session identifier 'SessionID'");
			return;
		}

		AbstractSiteToolSession session = sessionMgr.getSession(sessionID);

		// new session commands
		if ("NewHgPushSession".equals(command)) {
			if (session != null) {
				sendError(replysender, STFCPException.DUPLICATE_SESSION, identifier, "Session already exists.");
				return;
			}
			//session = sessionManager.newSession(replysender, identifier);
			FCPHandler.sendSuccess(replysender, identifier, "New Session created");
			return;
		}

		if ("NewSiteSession".equals(command)) {
			if (session != null) {
				sendError(replysender, STFCPException.DUPLICATE_SESSION, identifier, "Session already exists.");
				return;
			}
			session = new SiteEditSession(sessionID, pluginContext);
			try {
				sessionMgr.addSession(session);
			} catch (DuplicateSessionIDException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				sendError(replysender, STFCPException.DUPLICATE_SESSION, identifier, "Session already exists.");
				return;
			}
			//session = sessionManager.newSession(replysender, identifier);
			FCPHandler.sendSuccess(replysender, identifier, "New Session created");
			return;
		}

//		if ("OpenSession".equals(command)) {
//			if (session != null) {
//				sendError(replysender, STFCPException.DUPLICATE_SESSION_RUNNING, identifier, "Session already started.");
//				return;
//			}
//
//			if (logDEBUG) {
//				Logger.debug(this, "Open Session: " + identifier);
//			}
//
//			FreenetURI oldUri;
//			try {
//				oldUri = new FreenetURI(params.get("OldURI"));
//			} catch (MalformedURLException e) {
//				sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' invalid: "+e.getMessage());
//				return;
//			}
//			if (oldUri == null) {
//				sendError(replysender, STFCPException.MISSING_OLDURI, identifier, "Parameter 'OldURI' (previous edition) missing for session.");
//				return;
//			}
//
//			if (logDEBUG) {
//				Logger.debug(this, "Open Session: uri seems valid" + oldUri.toString(false, false));
//			}
//
//			Metadata md;
//			try {
//				md = KeyExplorerUtils.simpleManifestGet(pluginContext.pluginRespirator, oldUri);
//			} catch (MetadataParseException e) {
//				Logger.error(this, "Parameter 'OldURI' failed to fetch.", e);
//				sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' failed to fetch: "+e.getMessage());
//				return;
//			} catch (IOException e) {
//				Logger.error(this, "Parameter 'OldURI' failed to fetch.", e);
//				sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' failed to fetch: "+e.getMessage());
//				return;
//			} catch (FetchException e) {
//				Logger.error(this, "Parameter 'OldURI' failed to fetch.", e);
//				sendError(replysender, STFCPException.INVALID_OLDURI, identifier, "Parameter 'OldURI' failed to fetch: "+e.getMessage());
//				return;
//			}
//			
//			
//			// convert metadata
//			HashMap<String, Object> newMD;
//			try {
//				newMD = KeyExplorerUtils.parseMetadata(md, oldUri);
//			} catch (MalformedURLException e) {
//				sendError(replysender, FCPException.INTERNAL_ERROR, identifier, "Internal Error: "+e.getMessage());
//				return;
//			}
//			
//			// start session
//			//STSession session2 = new STSession(replysender, identifier, oldUri, newMD);
////			synchronized (sessions) {
////				sessions.put(identifier, session2);
////			}
//			FCPHandler.sendSuccess(replysender, identifier, "Session opened");
//			return;
//		}

		if (session == null) {
			sendError(replysender, STFCPException.NO_SUCH_SESSION, identifier, "No such session.");
			return;
		}

		// session control
		if ("CancelSession".equals(command)) {
			boolean kill = params.getBoolean("Kill", false);
			sessionMgr.cancelSession(sessionID);
			sendSuccess(replysender, identifier, "Session endet.");
			return;
		}

		if ("RemoveSession".equals(command)) {
			sessionMgr.removeSession(sessionID);
			return;
		}

		if ("StartSession".equals(command)) {
			sessionMgr.startSession(replysender, sessionID);
			return;
		}

		// anything left forward to session
		session.handleFCP(replysender, command, params, data, accesstype);
		
//		if ("AddItem".equals(command)) {
//			String name = params.get("Name");
//			String mime = params.get("ContentType");
//			session.addItem(replysender, name, mime, data, false, true);
//			return;
//		}
//		
//		if ("SetDefaultItem".equals(command)) {
//			FCPHandler.sendNOP(replysender, identifier);
//			return;
//		}
//		
//		if ("DeleteItem".equals(command)) {
//			FCPHandler.sendNOP(replysender, identifier);
//			return;
//		}
//		
//		if ("ModifyItem".equals(command)) {
//			String name = params.get("Name");
//			String mime = params.get("ContentType");
//			//session.addItem(replysender, name, mime, data, true, false);
//			return;
//		}
//		
//		if ("CommitSession".equals(command)) {
//			FreenetURI insertUri;
//			try {
//				insertUri = new FreenetURI(params.get("InsertURI"));
//			} catch (MalformedURLException e) {
//				sendError(replysender, STFCPException.INVALID_INSERTURI, identifier, "Parameter 'OldURI' invalid: "+e.getMessage());
//				return;
//			}
//			if (insertUri == null) {
//				sendError(replysender, STFCPException.MISSING_INSERTURI, identifier, "Parameter 'OldURI' (previous edition) missing for session.");
//				return;
//			}
//			//session.commit_test(this, replysender, insertUri, pr);
//			return;
//		}
//		
//		if ("HealFile".equals(command)) {
//			FCPHandler.sendNOP(replysender, identifier);
//			return;
//		}
//		
//		if ("HealDir".equals(command)) {
//			FCPHandler.sendNOP(replysender, identifier);
//			return;
//		}
//		
//		if ("ListManifest".equals(command)) {
//			session.listItems(replysender, identifier);
//			return;
//		}
		
		//sendError(replysender, STFCPException.INVALID_COMMAND, identifier, "Unknown command");
	}
}
