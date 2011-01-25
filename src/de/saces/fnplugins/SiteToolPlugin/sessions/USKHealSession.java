package de.saces.fnplugins.SiteToolPlugin.sessions;

import java.net.MalformedURLException;

import freenet.client.InsertException;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableUSK;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.PluginContext;

public class USKHealSession extends AbstractSiteToolSession {

	private final FreenetURI _startURI;

	private final StringBuilder status;

	private final PluginContext pCtx;

	public USKHealSession(String identifier, FreenetURI furi, PluginContext pluginContext) {
		super(identifier);
		_startURI = furi;
		pCtx = pluginContext;
		status = new StringBuilder();
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
	public void execute(PluginReplySender replysender) {
		status.append("starting\n");
		InsertableUSK iUSK = null;
		FreenetURI targetURI;
		try {
			iUSK = InsertableUSK.createInsertable(_startURI, false);
			targetURI = iUSK.getUSK().getURI().sskForUSK().setMetaString(new String[] { "" });
			status.append("Target: "+targetURI.toString(false, false));
			status.append('\n');
		} catch (MalformedURLException e) {
			Logger.error(this, "DEBUG", e);
			setError(e);
			return;
		}

		long edition = _startURI.getSuggestedEdition();

		while (edition > 0) {
			long newEdition = getNextEdition(edition);
			FreenetURI testUri = iUSK.getInsertableSSK(newEdition).getInsertURI();
			try {
				status.append("Healing edition: ");
				status.append(newEdition);
				status.append(' ');
				pCtx.hlsc.insertRedirect(testUri, targetURI);
				status.append("- healed\n");
			} catch (InsertException e) {
				if (e.getMode() == InsertException.COLLISION) {
					status.append("- was ok\n");
				} else {
					status.append("- Error: ");
					status.append(e.getLocalizedMessage());
					status.append('\n');
					Logger.error(this, "Pfehler", e);
				}
			}
			edition = newEdition;
		}
	}

	long getNextEdition(long oldEdition) {
		if (oldEdition < 1) throw new IllegalStateException();
		long neu = oldEdition-(2+pCtx.clientCore.random.nextInt(2));
		return Math.max(0, neu);
	}

	@Override
	public void getExtraStatusPanel(HTMLNode node) {
		node.addChild("pre", status.toString());
	}

	@Override
	public void handleFCP(PluginReplySender replysender, String command, SimpleFieldSet params,
			Bucket data, int accesstype) throws PluginNotFoundException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean canRetry() {
		return true;
	}

}
