package plugins.SiteToolPlugin.toadlets;

import java.io.IOException;
import java.net.URI;

import plugins.SiteToolPlugin.SiteToolPlugin;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class EditSiteToadlet extends WebInterfaceToadlet {
	private final SitesToadlet sitesToadlet;

	public EditSiteToadlet(PluginContext stCtx, SitesToadlet sitesToadlet2) {
		super(stCtx, SiteToolPlugin.PLUGIN_URI, "Edit Site");
		this.sitesToadlet = sitesToadlet2;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Site Tool Plugin", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;
		contentNode.addChild("#", "hello -> welt");
		
		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	@Override
	public Toadlet showAsToadlet() {
		return sitesToadlet;
	}

}
