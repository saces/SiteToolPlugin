package plugins.SiteToolPlugin;

import java.util.HashMap;
import java.util.Set;

import freenet.client.async.ManifestElement;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class SiteToolSession {
	
	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(SiteToolSession.class);
	}

	//private HashMap<String, Object> containers;
	private PluginReplySender replySender;
	private String id;
	private HashMap<String, Object> data;

	public SiteToolSession(PluginReplySender replysender, String identifier) {
		replySender = replysender;
		id = identifier;
		data = new HashMap<String, Object>();
	}

	public SiteToolSession(PluginReplySender replysender, String identifier, FreenetURI oldUri, HashMap<String, Object> metadata) {
		replySender = replysender;
		id = identifier;
		data = metadata;
	}

	public boolean endSession(boolean kill) {
		//FIXME
		//SiteToolPlugin.sendSuccess(replySender, id, "FAKE end");
		//SiteToolPlugin.sendNOP(replySender, id);
		return true;
	}

	public void attachSession(PluginReplySender replysender, String identifier) throws PluginNotFoundException {
		//FIXME
		FCPHandler.sendNOP(replysender, identifier);
	}

	@SuppressWarnings("unchecked")
	private HashMap<String, Object> findDir(HashMap<String, Object> parent, String name, boolean create) {
		String[] s = name.split("/", 2); 
		Object o = parent.get(s[0]);
		if (o == null) { // not found
			if (create) {
				o = new HashMap<String, Object>();
				parent.put(s[0], o);
			} else return null;
		}
		if (o instanceof HashMap) {
			if (s.length == 2) {
				return findDir((HashMap<String, Object>)o, s[1], create);
			}
			return (HashMap<String, Object>)o;
		}
		// not a dir FIXME
		return null;
	}

	public void addItem(PluginReplySender replysender, String name, String mimeOverride, Bucket item, boolean overwrite, boolean createpath) throws PluginNotFoundException {
		int i = name.lastIndexOf("/");
		String dirName;
		String itemName;
		HashMap<String, Object> parent;
		if (i > -1) {
			dirName = name.substring(0, i);
			itemName = name.substring(i+1);
			parent = findDir(data, dirName, createpath);
		} else {
			dirName = null;
			itemName = name;
			parent = data;
		}

		if ((!overwrite) && (parent.containsKey(name))) {
			FCPHandler.sendError(replysender, 200, name, "Duplicate item");
			return;
		}

		parent.put(itemName, new ManifestElement(itemName, item, mimeOverride, item.size()));
		FCPHandler.sendSuccess(replysender, id, "Item added: "+ name);
	}

	// TODO / FIXME
	public void commit_test(SiteToolPlugin stp, PluginReplySender replysender2, FreenetURI insertUri, PluginRespirator pr ) throws PluginNotFoundException {
		FreenetURI result = FreenetURI.EMPTY_CHK_URI;
//		try {
//			MyUglyManifestPutter myPutter = new MyUglyManifestPutter(pr);
//			//result = myPutter.insertManifest_test(insertUri, data, "index.html", replysender2, id);
//		} catch (InsertException e) {
//			Logger.error(this, "STP insert Error for "+id, e);
//			FCPHandler.sendError(replysender2, 300, id, "commit failed: "+e.getMessage());
//			return;
//		} catch (DatabaseDisabledException e) {
//			Logger.error(this, "STP insert Error for "+id, e);
//			FCPHandler.sendError(replysender2, 300, id, "commit failed: "+e.getMessage());
//			return;
//		}
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Status", "Success");
		sfs.put("Code", 0);
		sfs.putSingle("Identifier", id);
		sfs.putSingle("Description", "Insert sucessful");
		sfs.putSingle("URI", result.toString(false, false));
		replysender2.send(sfs);
	}

//	public void commit(SiteToolPlugin stp, PluginReplySender replysender2, FreenetURI insertUri, PluginRespirator pr ) throws PluginNotFoundException {
//		FreenetURI result;
//		try {
//			MyUglyManifestPutter myPutter = new MyUglyManifestPutter(pr);
//			// first make everything => 1MB a external
//			//HashMap<String, Object> externalizedMd = myPutter.externalize(data, replysender2, id);
//			// make small containers, any subdir =< 2 MB goes into a container
//			// will see what it becomes while writing code...
//			HashMap<String, Object> containerizedMd = myPutter.containerize(data, replysender2, id);
//			result = myPutter.insertManifest(insertUri, containerizedMd, "index.html", replysender2, id);
//		} catch (InsertException e) {
//			Logger.error(this, "STP insert Error for "+id, e);
//			SiteToolPlugin.sendError(replysender2, 300, id, "commit failed: "+e.getMessage());
//			return;
//		}
//		SimpleFieldSet sfs = new SimpleFieldSet(true);
//		sfs.putOverwrite("Status", "Success");
//		sfs.put("Code", 0);
//		sfs.putSingle("Identifier", id);
//		sfs.putSingle("Description", "Insert sucessful");
//		sfs.putSingle("URI", result.toString(false, false));
//		replysender2.send(sfs);
//	}

	public void listItems(PluginReplySender replysender2, String identifer) throws PluginNotFoundException {
		listItems(replysender2, identifer, "/", data);
		FCPHandler.sendSuccess(replysender2, identifer, "List End");
	}

	@SuppressWarnings("unchecked")
	private void listItems(PluginReplySender replysender2, String identifer, String prefix, HashMap<String, Object> md) throws PluginNotFoundException {
		Set<String> set = md.keySet();
		for(String name:set) {
			Object o = md.get(name);
			if (o instanceof HashMap) {
				listItems(replysender2, identifer, prefix+name+'/', (HashMap<String, Object>)o);	
				continue;
			}
			if (o instanceof ManifestElement) {
				ManifestElement me = (ManifestElement)o;
				SimpleFieldSet sfs = new SimpleFieldSet(true);
				sfs.putSingle("Status", "ManifestItem");
				sfs.putSingle("Identifer", identifer);
				sfs.putSingle("Name", me.getName());
				sfs.putSingle("Path", prefix);
				replysender2.send(sfs);
				continue;
			}
			// FIXME
			System.out.println(o);
			throw new Error("Huhu");
		}	
	}

	public void killSession() {
		// TODO self destroy
		
	}
}
