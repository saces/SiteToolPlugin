package plugins.SiteToolPlugin.sessions;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class SiteDownloadSession extends AbstractSiteToolSession {

	public SiteDownloadSession(String identifier) {
		super(identifier);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void cancel() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void destroySession() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execute() {
		Logger.error(this, "exuctione");
	}

	@Override
	public HTMLNode getExtraStatusPanel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void handleFCP(PluginReplySender replysender, SimpleFieldSet params,
			Bucket data, int accesstype) throws PluginNotFoundException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean canRetry() {
		// TODO Auto-generated method stub
		return false;
	}

}
