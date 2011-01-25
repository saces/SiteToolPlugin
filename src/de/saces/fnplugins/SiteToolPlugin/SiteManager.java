package de.saces.fnplugins.SiteToolPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import de.saces.fnplugins.SiteToolPlugin.exception.DuplicateSiteException;

import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.io.Closer;
import freenet.support.io.FileUtil;

public class SiteManager {

	private volatile boolean logMINOR;

	private final Object lock = new Object();
	private final SimpleFieldSet sites;

	final File filename;
	final File tempFilename;

	// if false everything is transient (config not stored to disk)
	boolean persistent = true;

	SiteManager(File config) throws IOException {
		filename = config;
		tempFilename = new File(filename.getPath()+".tmp");
		if (config.exists()) {
			sites = load(config);
		} else {
			sites = initNew();
			store();
		}
	}

	private SimpleFieldSet initNew() {
		SimpleFieldSet sfs = new SimpleFieldSet(false);
		sfs.put("Version", 1);
		return sfs;
	}

	private SimpleFieldSet load(File config) throws IOException {
		SimpleFieldSet sfs;
		FileInputStream fis = null;
		BufferedInputStream bis = null;
		try {
			fis = new FileInputStream(config);
			bis = new BufferedInputStream(fis);
			sfs = SimpleFieldSet.readFrom(bis, false, false);
		} finally {
			Closer.close(bis);
			Closer.close(fis);
		}
		return sfs;
	}

	void store() {
		if (!persistent) return;
		synchronized (lock) {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(tempFilename);
				synchronized(this) {
					sites.writeTo(fos);
				}
				FileUtil.renameTo(tempFilename, filename);
			} catch (IOException e) {
				String err = "Cannot store config: "+e;
				Logger.error(this, err, e);
				System.err.println(err);
				e.printStackTrace();
			} finally {
				Closer.close(fos);
			}
		}
	}

	public void addSite(String sitename, String sitepath, String inserturi, String requesturi) throws DuplicateSiteException {
		synchronized (lock) {
			SimpleFieldSet sub1 = sites.subset("Sites");
			SimpleFieldSet sub2 = null;
			boolean addParent = false;
			if (sub1 != null)
				sub2 = sub1.subset(sitename);
			else {
				sub1 = new SimpleFieldSet(false);
				addParent = true;
			}
			if (sub2 != null) 
				throw new DuplicateSiteException(sitename);
			SimpleFieldSet sfs = new SimpleFieldSet(false);
			sfs.putSingle("SitePath", sitepath);
			sfs.putSingle("InsertURI", inserturi);
			sfs.putSingle("RequestURI", requesturi);
			sub1.put(sitename, sfs);
			if (addParent)
				sites.put("Sites", sfs);
			store();
		}
	}

	public String[] getSites() {
		String[] result;
		synchronized (lock) {
			result = sites.subset("Sites").namesOfDirectSubsets();
		}
		return result;
	}
}
