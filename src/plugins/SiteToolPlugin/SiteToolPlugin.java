package plugins.SiteToolPlugin;

import java.io.File;
import java.util.HashMap;

import plugins.SiteToolPlugin.fproxy.dav.sampleimpl.LocalFileSystemStore;
import plugins.SiteToolPlugin.fproxy.dav.sampleimpl.DAVToadlet;
import plugins.SiteToolPlugin.fproxy.dav.sampleimpl.SimpleResourceLocks;
import plugins.SiteToolPlugin.toadlets.EditSiteToadlet;
import plugins.SiteToolPlugin.toadlets.HomeToadlet;
import plugins.SiteToolPlugin.toadlets.SessionsToadlet;
import plugins.SiteToolPlugin.toadlets.SitesToadlet;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterface;
import freenet.l10n.L10n.LANGUAGE;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
/**
 * @author saces
 * 
 */
public class SiteToolPlugin implements FredPlugin, FredPluginFCP,
		FredPluginVersioned, FredPluginRealVersioned, FredPluginThreadless, FredPluginL10n {

	private static final long revision = 6;
	public static final String PLUGIN_URI = "/SiteTool";
	private static final String PLUGIN_CATEGORY = "Site Tools";

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(SiteToolPlugin.class);
	}

	PluginRespirator pr;

	private final HashMap<String, SiteToolSession> sessions = new HashMap<String, SiteToolSession>();

	private PluginContext pluginContext;
	private WebInterface webInterface;

	private SessionManager sessionManager;
	private FCPHandler fcpHandler;

	public void runPlugin(PluginRespirator pluginRespirator) {
		this.pr = pluginRespirator;

		sessionManager = new SessionManager();
		fcpHandler = new FCPHandler(sessionManager, pluginRespirator);

		pluginContext = new PluginContext(pluginRespirator);
		webInterface = new WebInterface(pluginContext);

		webInterface.addNavigationCategory(PLUGIN_URI+"/", PLUGIN_CATEGORY, "Toolbox for maintaining sites and more...", this);

		// Visible pages
		HomeToadlet homeToadlet = new HomeToadlet(pluginContext);
		webInterface.registerVisible(homeToadlet, PLUGIN_CATEGORY, PLUGIN_URI+ "/", "Tools", "Tool page");
		SitesToadlet sitesToadlet = new SitesToadlet(pluginContext);
		webInterface.registerVisible(sitesToadlet, PLUGIN_CATEGORY, PLUGIN_URI + "/Sites", "Sites Manager", "Upload and manage your sites");
		SessionsToadlet sessionsToadlet = new SessionsToadlet(pluginContext);
		webInterface.registerVisible(sessionsToadlet, PLUGIN_CATEGORY, PLUGIN_URI + "/Sessions", "Session Monitor", "View and manage sessions");

		// Invisible pages
		EditSiteToadlet editSiteToadlet = new EditSiteToadlet(pluginContext, sitesToadlet);
		webInterface.registerInvisible(editSiteToadlet, PLUGIN_URI + "/EditSite");

		// set up DAV
		// download dir
		// TODO grab the downloads dir from config
		LocalFileSystemStore store1 = new LocalFileSystemStore(new File("downloads"));
		SimpleResourceLocks resLocks1 = new SimpleResourceLocks();

		// temp downloads
		// TODO implement a filesystem for temp downloads
		LocalFileSystemStore store2 = new LocalFileSystemStore(new File("davdemo"));
		SimpleResourceLocks resLocks2 = new SimpleResourceLocks();

		// download dir
		// TODO implement a filesystem for site editing/creating
		LocalFileSystemStore store3 = new LocalFileSystemStore(new File("demo3"));
		SimpleResourceLocks resLocks3 = new SimpleResourceLocks();

		// Invisible pages
		DAVToadlet davToadlet1 = new DAVToadlet(pluginContext, SiteToolPlugin.PLUGIN_URI, "DAV/downloads", sitesToadlet, store1, resLocks1);
		webInterface.registerInvisible(davToadlet1, PLUGIN_URI + "/DAV/downloads");
		DAVToadlet davToadlet2 = new DAVToadlet(pluginContext, SiteToolPlugin.PLUGIN_URI, "DAV/tempdl", sitesToadlet, store2, resLocks2);
		webInterface.registerInvisible(davToadlet2, PLUGIN_URI + "/DAV/tempdl");
		DAVToadlet davToadlet3 = new DAVToadlet(pluginContext, SiteToolPlugin.PLUGIN_URI, "DAV/siteedit", sitesToadlet, store3, resLocks3);
		webInterface.registerInvisible(davToadlet3, PLUGIN_URI + "/DAV/SiteEdit");
	}

	public void terminate() {
		webInterface.removeNavigationCategory(PLUGIN_CATEGORY);
		webInterface.kill();
		fcpHandler.kill();
		fcpHandler = null;
		sessionManager.kill();
		sessionManager = null;
	}

	public String getVersion() {
		return "STP 0.0.0 r" + revision;
	}

	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
		try {
			try {
				fcpHandler.handle(replysender, params, data, accesstype);
			} catch (UnsupportedOperationException uoe) {
				Logger.error(this, "TODO ERROR? "+uoe, uoe);
				FCPHandler.sendError(replysender, STFCPException.INTERNAL, "<unknown>", uoe.toString());
			}
		} catch (PluginNotFoundException pnfe) {
			Logger.error(this, "Connction to request sender Lost.", pnfe);
		}
	}

	public long getRealVersion() {
		return revision;
	}

	public String getString(String key) {
		//Logger.error(this, "TODO", new Exception("TODO"));
		return key;
	}

	public void setLanguage(LANGUAGE newLanguage) {
		//Logger.error(this, "TODO", new Exception("TODO"));		
	}
}
