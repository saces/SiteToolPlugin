package plugins.SiteToolPlugin.toadlets.siteexport;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;


import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.FetchWaiter;
import freenet.client.Metadata;
import freenet.client.async.ClientContext;
import freenet.client.async.ClientGetter;
import freenet.client.async.SnoopMetadata;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.api.Bucket;

public class SiteParser {
	
	public class Snooper implements SnoopMetadata {
		private Metadata _meta;

		Snooper() {
		}

		public boolean snoopMetadata(Metadata meta, ObjectContainer container,
				ClientContext context) {
			if (meta.isSimpleManifest()) {
				_meta = meta;
				//System.out.println("found a SimpleManifest");
				return true;
			}
			//System.out.println("not SM: "+meta.dump());
			return false;
		}
	}

	private final boolean _deep;
	private final boolean _multilevel;
	private final ISiteParserCallback _cb;
	private final FreenetURI _uri;
	private final PluginRespirator _pr;

	public SiteParser(ISiteParserCallback callback, FreenetURI uri, boolean multilevel, boolean deep, PluginRespirator pr) {
		_deep = deep;
		_cb = callback;
		_multilevel = multilevel;
		_uri = uri;
		_pr = pr;
	}

	private SiteParser(SiteParser sp, FreenetURI uri) {
		_deep = sp._deep;
		_cb = sp._cb;
		_multilevel = sp._multilevel;
		_uri = uri;
		_pr = sp._pr;
	}

	public void parseSite() throws FetchException, IOException {
		Snooper snooper = new Snooper();
		FetchContext context = _pr.getHLSimpleClient().getFetchContext();
		FetchWaiter fw = new FetchWaiter();
		ClientGetter get = new ClientGetter(fw, _uri, context, RequestStarter.INTERACTIVE_PRIORITY_CLASS, (RequestClient)_pr.getHLSimpleClient(), null, null);
		get.setMetaSnoop(snooper);
		try {
			get.start(null, _pr.getNode().clientCore.clientContext);
			fw.waitForCompletion();
		} catch (FetchException e) {
			if (snooper._meta == null) {
				// really an error
				throw e;
			}
		}

		if (snooper._meta == null) {
			// uri did not point to a SimpleManifest
			throw new FetchException(FetchException.INVALID_METADATA, "URI does not point to a SimpleManifest");
		}
		HashMap<String, Metadata> docs = snooper._meta.getDocuments();
		
		parseMetadata(docs, "/");
		_cb.finish();
		
	}

	private void parseMetadata(HashMap<String, Metadata> docs, String prefix) throws IOException {
		for (String name : docs.keySet()) {
			Metadata md = docs.get(name);
			if (md.isSimpleManifest()) {
				parseMetadata(md.getDocuments(), prefix + name + '/');
				continue;
			}
			final String tempName = prefix + name;
			//_pr.getNode().clientCore.getExecutor().execute(new Runnable() {

			//	public void run() {
					System.out.println("dumpi: "+_uri.toString(false, false)+tempName);
					try {
						_cb.addItem(tempName, fetchItem(_uri.toString(false, false)+tempName));
					} catch (MalformedURLException e) {
						Logger.error(this, "500", e);
						_cb.addReport(tempName, e.getLocalizedMessage());
					} catch (FetchException e) {
						Logger.error(this, "500", e);
						_cb.addReport(tempName, e.getLocalizedMessage());
					}
					// TODO Auto-generated method stub
					
			//	}}, "jobName");
			//_cb.parseMetadataItem(_uri, name, md, prefix);
			
		}
	}

	private Bucket fetchItem(String uri) throws MalformedURLException, FetchException {
		FetchResult result = _pr.getHLSimpleClient().fetch(new FreenetURI(uri));
		return result.asBucket();
	}
}
