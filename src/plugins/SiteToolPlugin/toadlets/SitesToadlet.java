package plugins.SiteToolPlugin.toadlets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import plugins.KeyExplorer.toadlets.UIUtils;
import plugins.SiteToolPlugin.SiteManager;
import plugins.SiteToolPlugin.SiteToolPlugin;
import plugins.SiteToolPlugin.exception.DuplicateSiteException;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;

public class SitesToadlet extends WebInterfaceToadlet {

	private static final String PATH_CREATESITE	= "/createsite";
	private static final String PATH_DELETESITE	= "/deletesite";
	private static final String PATH_OPENSITE	= "/opensite";

	private static final String SUBMIT_CREATE	= "create";
	private static final String SUBMIT_NEWKEY	= "newkey";

	private static final String PARAM_SITENAME = "sitename";
	private static final String PARAM_SITEPATH = "sitepath";
	private static final String PARAM_LASTEDITION = "lastedition";
	private static final String PARAM_REQUESTURI = "requestkey";
	private static final String PARAM_INSERTURI = "insertkey";

	private final SiteManager siteMgr;

	public SitesToadlet(PluginContext pCtx, SiteManager siteManager) {
		super(pCtx, SiteToolPlugin.PLUGIN_URI, "Sites");
		siteMgr = siteManager;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		String path = normalizePath(req.getPath());

		System.out.println("PATH test: " + path + " -> " + req.getPath());

		List<String> errors = new LinkedList<String>();
		if (PATH_CREATESITE.equals(path)) {
			makeCreatePage(ctx, errors, null, null, null, null);
			return;
		}

		if (path.equals("/")) {
			makeMainPage(ctx);
			return;
		}

		writeTemporaryRedirect(ctx, "Found elsewhere", path());
	}

	public void handleMethodPOST(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		if (!isFormPassword(req)) {
			sendErrorPage(ctx, 403, "Forbidden", "Invalid form password.");
			return;
		}

		String path = normalizePath(req.getPath());

		List<String> errors = new LinkedList<String>();

		if (PATH_CREATESITE.equals(path)) {
			String sitename = req.getPartAsString(PARAM_SITENAME, 128).trim();
			String sitepath = req.getPartAsString(PARAM_SITEPATH, 128).trim();
			String requesturi = req.getPartAsString(PARAM_REQUESTURI, 256).trim();
			String inserturi = req.getPartAsString(PARAM_INSERTURI, 256).trim();
			if (req.isPartSet(SUBMIT_NEWKEY)) {
				FreenetURI[] kp = pluginContext.hlsc.generateKeyPair("");
				inserturi = kp[0].setDocName(null).toString(false, false);
				requesturi = kp[1].setDocName(null).toString(false, false);
				makeCreatePage(ctx, errors, sitename, sitepath, inserturi, requesturi);
				return;
			} else if (req.isPartSet(SUBMIT_CREATE)) {
				tryCreate(ctx, errors, sitename, sitepath, requesturi, inserturi);
				return;
			} else {
				new Exception("hu?").printStackTrace();
			}
		}
		System.out.println("Path-Test: " + normalizePath(req.getPath()) + " -> " + uri);
		
		String s = new String(BucketTools.toByteArray(req.getRawData()));
		System.out.println("Data-Test: "+s);
		
		PageNode pageNode = getPageNode(ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;
		contentNode.addChild(createNewSiteBox(pluginContext, null, null, null, null));

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void tryCreate(ToadletContext ctx, List<String> errors, String sitename, String sitepath, String requesturi, String inserturi) throws ToadletContextClosedException, IOException {
		// sanitize
		if (sitename.length() == 0)
			errors.add("Sitename is empty");
		if (sitepath.length() == 0)
			errors.add("Sitepath is empty");

		if ((inserturi.length() == 0) && (requesturi.length() == 0)) {
			// booth uris are empty. generate one
			FreenetURI[] kp = pluginContext.hlsc.generateKeyPair("");
			inserturi = kp[0].setDocName(null).toString(false, false);
			requesturi = kp[1].setDocName(null).toString(false, false);
			errors.add("URIs was empty, I generated one for you.");
		} else if (inserturi.length() == 0) {
			errors.add("Insert URI is missing");
		} else if (requesturi.length() == 0) {
			// request uri is empty. generate from insert uri
			try {
				FreenetURI iUri = new FreenetURI(inserturi);
				InsertableClientSSK iSSK = InsertableClientSSK.create(iUri);
				requesturi = iSSK.getURI().setDocName(null).toString(false, false);
				errors.add("Request URI was empty, I generated it from Insert URI for you.");
			} catch (MalformedURLException e) {
				errors.add("Not a valid insert URI");
				e.printStackTrace();
			}
		}

		if (errors.size() > 0) {
			makeCreatePage(ctx, errors, sitename, sitepath, inserturi, requesturi);
			return;
		}

		try {
			siteMgr.addSite(sitename, sitepath, inserturi, requesturi);
		} catch (DuplicateSiteException e) {
			errors.add(e.getLocalizedMessage());
			e.printStackTrace();
		}

		if (errors.size() > 0) {
			makeCreatePage(ctx, errors, sitename, sitepath, inserturi, requesturi);
			return;
		}
		writeTemporaryRedirect(ctx, "Created", path());
		//makeMainPage(ctx);
	}

	private void makeMainPage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode pageNode = getPageNode(ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;
		contentNode.addChild("a", "href", path() + PATH_CREATESITE, "Create new site project");
		contentNode.addChild("BR");
		contentNode.addChild(createListSiteBox(pluginContext));
		
		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private void makeCreatePage(ToadletContext ctx, List<String> errors, String sitename, String sitepath, String inserturi, String requesturi) throws ToadletContextClosedException, IOException {
		PageNode pageNode = getPageNode(ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		if (errors.size() > 0) {
			contentNode.addChild(UIUtils.createErrorBox(pluginContext, errors));
			errors.clear();
		}
		contentNode.addChild(createNewSiteBox(pluginContext, sitename, sitepath, inserturi, requesturi));

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private PageNode getPageNode(ToadletContext ctx) {
		return pluginContext.pageMaker.getPageNode(SiteToolPlugin.PLUGIN_TITLE, ctx);
	}

	private HTMLNode createNewSiteBox(PluginContext pCtx, String sitename, String sitepath, String inserturi, String requesturi) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Create a new site project");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		HTMLNode browseForm = pCtx.pluginRespirator.addFormChild(browseContent, path() + PATH_CREATESITE, "uriForm");
		browseForm.addChild("#", "Give the project a meaningful, unique name:");
		browseForm.addChild("BR");
		if (sitename != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_SITENAME, "30", sitename });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_SITENAME, "30" });
		browseForm.addChild("#", "\u00a0This name is not used for inserts, it is for identify or find the project in your huge list ;)");
		browseForm.addChild("BR");
		browseForm.addChild("BR");

		browseForm.addChild("#", "Site name:");
		browseForm.addChild("BR");
		if (sitename != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_SITEPATH, "30", sitepath });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_SITEPATH, "30" });
		browseForm.addChild("#", "\u00a0This name becomes part of the uri. Choose it wisely.");
		browseForm.addChild("BR");
		browseForm.addChild("BR");

		browseForm.addChild("#", "Insert URI:");
		browseForm.addChild("BR");
		if (inserturi != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_INSERTURI, "70", inserturi });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_INSERTURI, "70" });
		browseForm.addChild("BR");
		browseForm.addChild("BR");

		browseForm.addChild("#", "Request URI:");
		browseForm.addChild("BR");
		if (requesturi != null)
			browseForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_REQUESTURI, "70", requesturi });
		else
			browseForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_REQUESTURI, "70" });
		browseForm.addChild("BR");
		browseForm.addChild("BR");

		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", SUBMIT_NEWKEY, "Make new key" });
		browseForm.addChild("#", "\u00a0");
		browseForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", SUBMIT_CREATE, "Create site project" });
		return browseBox;
	}

	private HTMLNode createListSiteBox(PluginContext pCtx) {
		InfoboxNode box = pCtx.pageMaker.getInfobox("Site projects");
		HTMLNode browseBox = box.outer;
		HTMLNode browseContent = box.content;

		String[] sites = siteMgr.getSites();

		if (sites.length == 0) {
			browseContent.addChild("#", "<Empty>");
		} else {
			for (String sitename: sites) {
				browseContent.addChild("#", sitename);
				browseContent.addChild("BR");
			}
		}

		return browseBox;
	}

}
