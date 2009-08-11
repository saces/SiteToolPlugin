package plugins.SiteToolPlugin;

import com.db4o.ObjectContainer;

import freenet.client.PutWaiter;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;


class VerboseWaiter extends PutWaiter implements ClientEventListener {
	
	private final PluginReplySender _replysender;
	private final String _identifier;
	private final BaseClientPutter _putter;

	VerboseWaiter(PluginReplySender replysender, String identifier, BaseClientPutter putter) {
		super();
		_replysender = replysender;
		_identifier = identifier;
		_putter = putter;
	}

	@Override
	public void onFetchable(BaseClientPutter state, ObjectContainer container) {
		try {
			FCPHandler.sendProgress(_replysender,  _identifier, "Put fetchable");
		} catch (PluginNotFoundException e) {
			// TODO Auto-generated catch block
			_putter.cancel(container, null);
		}
		Logger.error(this, "Put fetchable");
		super.onFetchable(state, container);
	}

	@Override
	public synchronized void onGeneratedURI(FreenetURI uri, BaseClientPutter state, ObjectContainer container) {
		Logger.debug(this, "Got UriGenerated: "+uri.toString(false, false));
		try {
			FCPHandler.sendProgress(_replysender,  _identifier, "Uri generated: "+uri.toString(false, false));
		} catch (PluginNotFoundException e) {
			_putter.cancel(container, null);
		}
		super.onGeneratedURI(uri, state, container);
	}

	// segment start/finish, ignore, we don't persist
//	@Override
//	public void onMajorProgress(ObjectContainer container) {
//		sendProgress(_replysender,  _identifier, "major progress");
//		super.onMajorProgress(container);
//	}

	public void onRemoveEventProducer(ObjectContainer container) {
		Logger.error(this, "TODO", new Exception("TODO"));
	}

	public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context) {
		Logger.error(this, "Progress: "+ce.getDescription());
		try {
			FCPHandler.sendProgress(_replysender,  _identifier, ce.getDescription());
		} catch (PluginNotFoundException e) {
			Logger.error(this, "Could not send Progress", e);
			_putter.cancel(maybeContainer, context);
		}
	}
}

