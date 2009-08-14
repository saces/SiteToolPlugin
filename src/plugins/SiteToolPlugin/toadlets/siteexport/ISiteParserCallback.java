package plugins.SiteToolPlugin.toadlets.siteexport;

import java.io.IOException;

import freenet.support.api.Bucket;

public interface ISiteParserCallback {

	public void addItem(String name, Bucket data) throws IOException;
	public void addReport(String name, String report);
	public void finish() throws IOException;

}
