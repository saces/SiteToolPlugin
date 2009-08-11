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

public class SessionsToadlet extends WebInterfaceToadlet {

	public SessionsToadlet(PluginContext stCtx) {
		super(stCtx, SiteToolPlugin.PLUGIN_URI, "Sessions");
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Site Tool Plugin", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;
		contentNode.addChild("#", "hello -> welt");
		
		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

}
