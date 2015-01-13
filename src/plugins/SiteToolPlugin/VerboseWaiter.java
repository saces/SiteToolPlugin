package plugins.SiteToolPlugin;

import freenet.client.PutWaiter;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;

public class VerboseWaiter extends PutWaiter implements ClientEventListener {
	
	private final PluginReplySender _replysender;
	private final String _identifier;
	private BaseClientPutter _putter;

	public VerboseWaiter(PluginReplySender replysender, String identifier, RequestClient reqestclient) {
		super(reqestclient);
		_replysender = replysender;
		_identifier = identifier;
	}

	public void setPutter(BaseClientPutter putter) {
		_putter = putter;
	}

	@Override
	public void onFetchable(BaseClientPutter state) {
		try {
			FCPHandler.sendProgress(_replysender,  _identifier, "Put fetchable");
		} catch (PluginNotFoundException e) {
			// TODO Auto-generated catch block
			_putter.cancel(null);
		}
		Logger.error(this, "Put fetchable");
		super.onFetchable(state);
	}

	@Override
	public synchronized void onGeneratedURI(FreenetURI uri, BaseClientPutter state) {
		Logger.debug(this, "Got UriGenerated: "+uri.toString(false, false));
		try {
			FCPHandler.sendProgress(_replysender,  _identifier, "Uri generated: "+uri.toString(false, false));
		} catch (PluginNotFoundException e) {
			_putter.cancel(null);
		}
		super.onGeneratedURI(uri, state);
	}

	// segment start/finish, ignore, we don't persist
//	@Override
//	public void onMajorProgress(ObjectContainer container) {
//		sendProgress(_replysender,  _identifier, "major progress");
//		super.onMajorProgress(container);
//	}

	public void onRemoveEventProducer() {
		Logger.error(this, "TODO", new Exception("TODO"));
	}

	public void receive(ClientEvent ce, ClientContext context) {
		Logger.error(this, "Progress: "+ce.getDescription());
		try {
			FCPHandler.sendProgress(_replysender,  _identifier, ce.getDescription());
		} catch (PluginNotFoundException e) {
			Logger.error(this, "Could not send Progress", e);
			_putter.cancel(context);
		}
	}
}

