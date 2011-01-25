package de.saces.fnplugins.SiteToolPlugin.toadlets.siteexport;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;


import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.HighLevelSimpleClient;
import freenet.client.Metadata;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.async.SnoopMetadata;
import freenet.client.events.ClientEvent;
import freenet.client.events.ClientEventListener;
import freenet.client.events.SplitfileProgressEvent;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.support.Logger;

public class SiteParser implements ClientGetCallback {
	
	public class ProgressMonitor implements ClientEventListener {
		
		private String lastProgress = "Starting";

		public void onRemoveEventProducer(ObjectContainer container) {
		}

		public void receive(ClientEvent ce, ObjectContainer maybeContainer,
				ClientContext context) {
			if (ce instanceof SplitfileProgressEvent) {
				SplitfileProgressEvent spe = (SplitfileProgressEvent) ce;
				lastProgress = spe.getDescription();
			}
		}
	}

	public static class Snooper implements SnoopMetadata {
		private Metadata _meta;
		//private boolean metaSeen = false;

		Snooper() {
		}

		public boolean snoopMetadata(Metadata meta, ObjectContainer container,
				ClientContext context) {
			//metaSeen = true;
			if (meta.isSimpleManifest()) {
				_meta = meta;
				return true;
			}
			return false;
		}
	}

	private final boolean _deep;
	private final boolean _multilevel;
	private final ISiteParserCallback _cb;
	private final FreenetURI _uri;
	private final HighLevelSimpleClient _hlsc;
	private final ClientContext _clientContext;
	
	private int itemsLeft=0;
	private int itemsTotal=0;
	private int itemsError=0;
	private int itemsDone=0;

	private final HashMap<ClientGetter, String> _getter2nameMap;
	private final HashMap<String, ProgressMonitor> _statusByName;
	
	private boolean parsingDone;

	public SiteParser(ISiteParserCallback callback, FreenetURI uri, boolean multilevel, boolean deep, HighLevelSimpleClient hlsc, ClientContext clientContext) {
		_deep = deep;
		_cb = callback;
		_multilevel = multilevel;
		_uri = uri;
		_hlsc = hlsc;
		_getter2nameMap = new HashMap<ClientGetter, String>();
		_clientContext = clientContext;
		_statusByName = new HashMap<String, ProgressMonitor>();
	}

	public void parseSite() throws FetchException, IOException {
		parsingDone = false;
		if (!_getter2nameMap.isEmpty())
			throw new IllegalStateException("getter2nameMap not empty on start!");
		Snooper snooper = new Snooper();
		FetchContext context = _hlsc.getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = new ClientGetter(fw, _uri.setMetaString(new String[]{"fake"}), context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)_hlsc, null, null);
		get.setMetaSnoop(snooper);
		try {
			get.start(null, _clientContext);
			fw.waitForCompletion();
		} catch (FetchException e) {
			if (!e.isFatal()) {
				throw e;
			}
		}

		if (snooper._meta == null) {
			// uri did not point to a SimpleManifest
			throw new FetchException(FetchException.INVALID_METADATA, "URI does not point to a SimpleManifest");
		}
		HashMap<String, Metadata> docs = snooper._meta.getDocuments();
		parseMetadata(docs, "/", _uri);
		parsingDone = true;
	}

	private void parseMetadata(HashMap<String, Metadata> docs, String prefix, FreenetURI uri) throws IOException {
		for (Entry<String, Metadata> entry : docs.entrySet()) {
			String name = entry.getKey();
			Metadata md = entry.getValue();
			if (md.isSimpleManifest()) {
				parseMetadata(md.getDocuments(), prefix + name + '/', uri.pushMetaString(name));
				continue;
			}
			final String tempName = prefix + name;

			FetchContext context = _hlsc.getFetchContext();
			ProgressMonitor pm = new ProgressMonitor();
			context.eventProducer.addEventListener(pm);
			_statusByName.put(tempName, pm);
			ClientGetter get = new ClientGetter(this, uri.pushMetaString(name), context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)_hlsc, null, null);
			_getter2nameMap.put(get, tempName);
			itemsTotal++;
			itemsLeft++;
			try {
				get.start(null, _clientContext);
			} catch (FetchException e) {
				onFailure(e, get, null);
			}
		}
	}

	public void onFailure(FetchException e, ClientGetter state, ObjectContainer container) {
		itemsError++;
		String name = _getter2nameMap.get(state);
		Logger.error(this, "500", e);
		_cb.addReport(name, e.getLocalizedMessage());
		removeGetter(state);
	}

	public void onSuccess(FetchResult result, ClientGetter state, ObjectContainer container) {
		itemsDone++;
		String name = _getter2nameMap.get(state);
		try {
			_cb.addItem(name, result.asBucket());
		} catch (IOException e) {
			itemsDone--;
			itemsError++;
			Logger.error(this, "500", e);
			_cb.addReport(name, e.getLocalizedMessage());
		}
		removeGetter(state);
	}

	public void onMajorProgress(ObjectContainer container) {
		// ignore
	}

	private synchronized void removeGetter(ClientGetter getter) {
		String name = _getter2nameMap.remove(getter);
		_statusByName.remove(name);
		itemsLeft--;
		if (_getter2nameMap.isEmpty()) {
			try {
				_cb.finish();
			} catch (IOException e) {
				Logger.error(this, "FATAL", e);
				e.printStackTrace();
			}
		}
		notifyAll();
	}

	/** Wait for the parsing and fetching to complete. */
	public synchronized void waitForCompletion() {
		while(!(parsingDone && _getter2nameMap.isEmpty())) {
			try {
				wait();
			} catch (InterruptedException e) {
				// Ignore
			}
		}
	}

	public void cancel(boolean wait) {
		for (ClientGetter getter:_getter2nameMap.keySet()) {
			getter.cancel(null, _clientContext);
		}
	}

	public int getItemsTotal() {
		return itemsTotal;
	}

	public int getItemsDone() {
		return itemsDone;
	}

	public int getItemsError() {
		return itemsError;
	}
	public int getItemsLeft() {
		return itemsLeft;
	}

	public HashMap<String, String> getProgressStats() {
		HashMap<String, String> result = new HashMap<String, String>();
		for (String name:_statusByName.keySet()) {
			result.put(name, _statusByName.get(name).lastProgress);
		}
		return result;
	}
}
