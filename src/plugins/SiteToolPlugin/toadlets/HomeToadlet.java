package plugins.SiteToolPlugin.toadlets;

import java.io.IOException;
import java.net.URI;

import plugins.SiteToolPlugin.SiteToolPlugin;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class HomeToadlet extends WebInterfaceToadlet {

	private final static String CMD_SITEDOWNLOAD = "sitedownload";
	private final static String CMD_SITEEXPORT = "siteexport";
	private final static String CMD_FILEEXPORT = "fileexport";
	private final static String CMD_BLOBIMPORT = "blobimport";
	private final static String CMD_USKFASTHEAL = "healuskfast";
	private final static String CMD_USKFULLHEAL = "healuskfull";

	public HomeToadlet(PluginContext pluginContext2) {
		super(pluginContext2, SiteToolPlugin.PLUGIN_URI, "");
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		if (!req.getPath().toString().equals(path())) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}
		makePage(ctx);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {

		String pass = request.getPartAsString("formPassword", 32);
		if ((pass.length() == 0) || !pass.equals(pluginContext.clientCore.formPassword)) {
			sendErrorPage(ctx, 403, "Forbidden", "Invalid form password.");
			return;
		}
		if (!request.getPath().equals(path())) {
			sendErrorPage(ctx, 404, "Not found", "The path '"+uri+"' was not found");
			return;
		}
		if (request.isPartSet("sitedownload"))
			System.out.println("!!!!!!!!YY "+uri);
		System.out.println("YY "+uri);
		String path = request.getPath();
		System.out.println("YY "+path);
		String submit = request.getPartAsString("submit", 32);
		System.out.println("YY "+submit);
		request.getMethod();
		makePage(ctx);
	}

	private void makePage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		makePage(ctx, null);
	}

	private void makePage(ToadletContext ctx, HTMLNode error) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Site Tool Plugin", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		if (error != null) {
			HTMLNode errorBox = pluginContext.pageMaker.getInfobox("infobox-information", "Error", contentNode);
			errorBox.addChild(error);
		}

		HTMLNode box1 = pluginContext.pageMaker.getInfobox("infobox-information", "Uploads / Downloads", contentNode);

		HTMLNode box11 = pluginContext.pageMaker.getInfobox("infobox-information", "Download a whole Freesite as archive", box1);
		HTMLNode box11Form = pluginContext.pluginRespirator.addFormChild(box11, path(), "uriForm");
		box11Form.addChild("#", "Select archive format: \u00a0 ");
		box11Form.addChild("input", new String[] { "type", "name", "value", "checked"}, new String[] { "radio", "archivetype", "tar7z", "checked" }, "tar.lzma");
		box11Form.addChild("#", "\u00a0");
		box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", "archivetype", "targz" }, "tar.gz");
		box11Form.addChild("#", "\u00a0");
		box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", "archivetype", "tar" }, "tar");
		box11Form.addChild("#", "\u00a0");
		box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", "archivetype", "zip" }, "zip");
		box11Form.addChild("%", "<BR />");
		box11Form.addChild("#", "Site URI: \u00a0 ");
		box11Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		box11Form.addChild("#", "\u00a0");
		box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_SITEDOWNLOAD, "Download" });

		HTMLNode box12 = pluginContext.pageMaker.getInfobox("infobox-information", "Export a whole Freesite as fblob", box1);
		HTMLNode box12Form = pluginContext.pluginRespirator.addFormChild(box12, path(), "uriForm");
		box12Form.addChild("#", "Site URI: \u00a0 ");
		box12Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		box12Form.addChild("#", "\u00a0");
		box12Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_SITEEXPORT, "Export" });

		HTMLNode box12a = pluginContext.pageMaker.getInfobox("infobox-information", "Export Freenet URI (a single file) as fblob", box1);
		HTMLNode box12aForm = pluginContext.pluginRespirator.addFormChild(box12a, path(), "uriForm");
		box12aForm.addChild("#", "Freenet URI: \u00a0 ");
		box12aForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		box12aForm.addChild("#", "\u00a0");
		box12aForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_FILEEXPORT, "Export" });

		HTMLNode box13 = pluginContext.pageMaker.getInfobox("infobox-information", "Import a fblob", box1);
		HTMLNode box13Form = pluginContext.pluginRespirator.addFormChild(box13, path(), "uriForm");
		box13Form.addChild("#", "Local filename to import: \u00a0 ");
		box13Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		box13Form.addChild("#", "\u00a0");
		box13Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_BLOBIMPORT, "Import" });

		HTMLNode box2 = pluginContext.pageMaker.getInfobox("infobox-information", "Healing", contentNode);

		HTMLNode box21 = pluginContext.pageMaker.getInfobox("infobox-information", "Heal an USK update chain", box2);
		HTMLNode box21Form = pluginContext.pluginRespirator.addFormChild(box21, path(), "uriForm");
		box21Form.addChild("#", "Ensure you have given the latest known edition in the URI, the auto updater may not work until it is healed. ;)");
		box21Form.addChild("%", "<BR />");
		box21Form.addChild("#", "USK to heal: \u00a0 ");
		box21Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", "key", "70" });
		box21Form.addChild("#", "\u00a0");
		box21Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_USKFULLHEAL, "Full Heal" });
		box21Form.addChild("#", "\u00a0");
		box21Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_USKFASTHEAL, "Fast Heal" });

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}
}
