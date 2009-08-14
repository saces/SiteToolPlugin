package plugins.SiteToolPlugin.toadlets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import SevenZip.Compression.LZMA.Encoder;

import plugins.KeyExplorer.KeyExplorerUtils;
import plugins.SiteToolPlugin.SiteToolPlugin;
import plugins.SiteToolPlugin.toadlets.siteexport.SiteCollector;
import plugins.SiteToolPlugin.toadlets.siteexport.SiteParser;
import plugins.fproxy.lib.PluginContext;
import plugins.fproxy.lib.WebInterfaceToadlet;
import freenet.client.FetchException;
import freenet.clients.http.PageNode;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.Closer;

public class HomeToadlet extends WebInterfaceToadlet {

	private final static String CMD_SITEDOWNLOAD = "sitedownload";
	private final static String CMD_SITEEXPORT = "siteexport";
	private final static String CMD_FILEEXPORT = "fileexport";
	private final static String CMD_BLOBIMPORT = "blobimport";
	private final static String CMD_USKFASTHEAL = "healuskfast";
	private final static String CMD_USKFULLHEAL = "healuskfull";

	private final static String DL_TYPE_TAR7Z = "tar7z";
	private final static String DL_TYPE_TARGZ = "targz";
	private final static String DL_TYPE_TAR = "tar";
	private final static String DL_TYPE_ZIP = "zip";

	private final static String PARAM_URI = "key";
	private final static String PARAM_TYPE = "archivetype";

	public HomeToadlet(PluginContext pluginContext2) {
		super(pluginContext2, SiteToolPlugin.PLUGIN_URI, "");
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		if (!req.getPath().toString().equals(path())) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}
		makePage(ctx);
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, RedirectException, IOException {

		String pass = request.getPartAsString("formPassword", 32);
		if ((pass.length() == 0) || !pass.equals(pluginContext.clientCore.formPassword)) {
			sendErrorPage(ctx, 403, "Forbidden", "Invalid form password.");
			return;
		}
		if (!request.getPath().equals(path())) {
			sendErrorPage(ctx, 404, "Not found", "The path '"+uri+"' was not found");
			return;
		}

		List<String> errors = new LinkedList<String>();
		FreenetURI furi = null;
		String key = request.getPartAsString(PARAM_URI, 1024).trim();

		if (key.length() == 0) {
			errors.add("Are you jokingly? URI is empty!");
			makePage(ctx, errors);
			return;
		}

		if (request.isPartSet(CMD_SITEDOWNLOAD)) {
			String archiveType = request.getPartAsString(PARAM_TYPE, 32);

			try {
				furi = KeyExplorerUtils.sanitizeURI(errors, key);
			} catch (MalformedURLException e) {
				errors.add(e.getLocalizedMessage());
			}

			String mime = null;
			String ext = null;
			if (DL_TYPE_TAR7Z.equals(archiveType)) {
				mime = "application/x-lzma-compressed-tar";
				ext = "tar.lzma";
			} else if (DL_TYPE_TARGZ.equals(archiveType)) {
				mime = "application/x-gtar";
				ext = "tar.gz";	
			} else if (DL_TYPE_TAR.equals(archiveType)) {
				mime = "application/x-tar";
				ext = "tar";
			} else if (DL_TYPE_ZIP.equals(archiveType)) {
				mime = "application/zip";
				ext = "zip";
			} else {
				errors.add("Did not anderstand archive type '"+ archiveType+"'");
				archiveType = null;
			}

			if (!errors.isEmpty()) {
				makePage(ctx, errors, CMD_SITEDOWNLOAD, (furi == null)?null:furi.toString(false, false), archiveType);
				return;
			}

			//furi = furi.addMetaStrings(new String[] { "fake" } );
			Bucket out = ctx.getBucketFactory().makeBucket(-1);
			OutputStream os = new BufferedOutputStream(out.getOutputStream());
			if (DL_TYPE_TARGZ.equals(archiveType)) {
				os = new GZIPOutputStream(os);
			}

			try {
				makeArchive(os, furi, DL_TYPE_ZIP.equals(archiveType), errors);
			} finally {
				Closer.close(os);
				os = null;
			}
			
			if (!errors.isEmpty()) {
				makePage(ctx, errors);
				return;
			}
			
			if (DL_TYPE_TAR7Z.equals(archiveType)) {
				Bucket output = ctx.getBucketFactory().makeBucket(-1);
				BufferedInputStream cis = null;
				BufferedOutputStream cos = null;
				try {
					cis = new BufferedInputStream(out.getInputStream());
					cos = new BufferedOutputStream(output.getOutputStream());
					Encoder encoder = new Encoder();
					encoder.SetEndMarkerMode( true );
					encoder.SetDictionarySize( 1 << 20 );
					// enc.WriteCoderProperties( out );
					// 5d 00 00 10 00
					encoder.Code( cis, cos, -1, -1, null );
					cis.close();
					cos.close();
				} finally {
					Closer.close(cis);
					Closer.close(cos);
				}
				out.free();
				out = output;
			}

			MultiValueTable<String, String> head = new MultiValueTable<String, String>();
			head.put("Content-Disposition", "attachment; filename=\"" + "sitearchive."+ ext + '"');
			ctx.sendReplyHeaders(200, "Found", head, mime, out.size());
			ctx.writeData(out);
			return;
		}

		System.out.println("YY "+uri);
		String path = request.getPath();
		System.out.println("YY "+path);
		String submit = request.getPartAsString("submit", 32);
		System.out.println("YY "+submit);
		request.getMethod();
		errors.add("Whatever you have called, it is not implemented");
		makePage(ctx, errors);
	}

	private void makeArchive(OutputStream out, FreenetURI furi, boolean zip, List<String> errors) {
		
		SiteCollector collector = new SiteCollector(out, zip, pluginContext.clientCore.tempBucketFactory);
		SiteParser parser = new SiteParser(collector, furi, true, true, pluginContext.pluginRespirator);
		try {
			parser.parseSite();
		} catch (FetchException e) {
			Logger.error(this, "500", e);
			errors.add(e.getLocalizedMessage());
			return;
		} catch (IOException e) {
			Logger.error(this, "500", e);
			errors.add(e.getLocalizedMessage());
		} finally {
			
		}
	}

	private void makePage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		makePage(ctx, null);
	}

	private void makePage(ToadletContext ctx, List<String> errors) throws ToadletContextClosedException, IOException {
		makePage(ctx, errors, null, null, null);
	}

	private void makePage(ToadletContext ctx, List<String> errors, String what, String uri, String type) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("Site Tool Plugin", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		if (errors != null) {
			HTMLNode errorBox = pluginContext.pageMaker.getInfobox("infobox-alert", "Error", contentNode);
			for (String error : errors) {
				errorBox.addChild("#", error);
				errorBox.addChild("%", "<BR />");
			}
		}

		HTMLNode box1 = pluginContext.pageMaker.getInfobox("infobox-information", "Uploads / Downloads", contentNode);

		HTMLNode box11 = pluginContext.pageMaker.getInfobox("infobox-information", "Download a whole Freesite as archive", box1);
		HTMLNode box11Form = pluginContext.pluginRespirator.addFormChild(box11, path(), "uriForm");
		String defaulttype;
		if (type==null)
			defaulttype = DL_TYPE_TAR7Z;
		else
			defaulttype = type;

		box11Form.addChild("#", "Select archive format: \u00a0 ");
		if (defaulttype.equals(DL_TYPE_TAR7Z))
			box11Form.addChild("input", new String[] { "type", "name", "value", "checked"}, new String[] { "radio", PARAM_TYPE, DL_TYPE_TAR7Z, "checked" }, "tar.lzma");
		else
			box11Form.addChild("input", new String[] { "type", "name", "value"}, new String[] { "radio", PARAM_TYPE, DL_TYPE_TAR7Z }, "tar.lzma");
		box11Form.addChild("#", "\u00a0");
		if (isCommand(what, CMD_SITEDOWNLOAD) && defaulttype.equals(DL_TYPE_TARGZ))
			box11Form.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "radio", PARAM_TYPE, DL_TYPE_TARGZ, "checked" }, "tar.gz");
		else	
			box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", PARAM_TYPE, DL_TYPE_TARGZ }, "tar.gz");
		box11Form.addChild("#", "\u00a0");
		if (isCommand(what, CMD_SITEDOWNLOAD) && defaulttype.equals(DL_TYPE_TAR))
			box11Form.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "radio", PARAM_TYPE, DL_TYPE_TAR, "checked" }, "tar");
		else
			box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", PARAM_TYPE, DL_TYPE_TAR }, "tar");
		box11Form.addChild("#", "\u00a0");
		if (isCommand(what, CMD_SITEDOWNLOAD) && defaulttype.equals(DL_TYPE_ZIP))
			box11Form.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "radio", PARAM_TYPE, DL_TYPE_ZIP, "checked" }, "zip");
		else
			box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", PARAM_TYPE, DL_TYPE_ZIP }, "zip");
		box11Form.addChild("%", "<BR />");
		box11Form.addChild("#", "Site URI: \u00a0 ");
		if (isCommand(what, CMD_SITEDOWNLOAD) && uri != null)
			box11Form.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", PARAM_URI, "70", uri });
		else
			box11Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_URI, "70" });
		box11Form.addChild("#", "\u00a0");
		box11Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_SITEDOWNLOAD, "Download" });

		HTMLNode box12 = pluginContext.pageMaker.getInfobox("infobox-information", "Export a whole Freesite as fblob", box1);
		HTMLNode box12Form = pluginContext.pluginRespirator.addFormChild(box12, path(), "uriForm");
		box12Form.addChild("#", "Site URI: \u00a0 ");
		box12Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_URI, "70" });
		box12Form.addChild("#", "\u00a0");
		box12Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_SITEEXPORT, "Export" });

		HTMLNode box12a = pluginContext.pageMaker.getInfobox("infobox-information", "Export Freenet URI (a single file) as fblob", box1);
		HTMLNode box12aForm = pluginContext.pluginRespirator.addFormChild(box12a, path(), "uriForm");
		box12aForm.addChild("#", "Freenet URI: \u00a0 ");
		box12aForm.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_URI, "70" });
		box12aForm.addChild("#", "\u00a0");
		box12aForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_FILEEXPORT, "Export" });

		HTMLNode box13 = pluginContext.pageMaker.getInfobox("infobox-information", "Import a fblob", box1);
		HTMLNode box13Form = pluginContext.pluginRespirator.addFormChild(box13, path(), "uriForm");
		box13Form.addChild("#", "Local filename to import: \u00a0 ");
		box13Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_URI, "70" });
		box13Form.addChild("#", "\u00a0");
		box13Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_BLOBIMPORT, "Import" });

		HTMLNode box2 = pluginContext.pageMaker.getInfobox("infobox-information", "Healing", contentNode);

		HTMLNode box21 = pluginContext.pageMaker.getInfobox("infobox-information", "Heal an USK update chain", box2);
		HTMLNode box21Form = pluginContext.pluginRespirator.addFormChild(box21, path(), "uriForm");
		box21Form.addChild("#", "Ensure you have given the latest known edition in the URI, the auto updater may not work until it is healed. ;)");
		box21Form.addChild("%", "<BR />");
		box21Form.addChild("#", "USK to heal: \u00a0 ");
		box21Form.addChild("input", new String[] { "type", "name", "size" }, new String[] { "text", PARAM_URI, "70" });
		box21Form.addChild("#", "\u00a0");
		box21Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_USKFULLHEAL, "Full Heal" });
		box21Form.addChild("#", "\u00a0");
		box21Form.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", CMD_USKFASTHEAL, "Fast Heal" });

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private boolean isCommand(String what, String command) {
		if (command == null) throw new NullPointerException();
		if (what == null) return false;
		return what.equals(command);
	}
}
