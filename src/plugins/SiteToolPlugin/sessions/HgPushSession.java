package plugins.SiteToolPlugin.sessions;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.HTMLNode;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class HgPushSession extends AbstractSiteToolSession {

	public HgPushSession(String identifier) {
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public void getExtraStatusPanel(HTMLNode node) {
		node.addChild("#", "<Empty>");
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
