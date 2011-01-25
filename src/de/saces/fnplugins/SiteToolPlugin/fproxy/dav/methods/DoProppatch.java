package de.saces.fnplugins.SiteToolPlugin.fproxy.dav.methods;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.ILockedObject;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.util.XMLHelper;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.util.XMLWriter;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.util.XMLWriter.TAG;

import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;

public class DoProppatch extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoProppatch.class);
	}

    private boolean _readOnly;
    private IWebDAVStore _store;
    private IResourceLocks _resourceLocks;

    public DoProppatch(Toadlet parent, IWebDAVStore store, IResourceLocks resLocks,
            boolean readOnly) {
    	super(parent);
        _readOnly = readOnly;
        _store = store;
        _resourceLocks = resLocks;
    }

	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {
        if (logDEBUG)
        	Logger.debug(this, "-- " + this.getClass().getName());

        if (_readOnly) {
            sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
            return;
        }

        String path = getRelativePath(req);
        String parentPath = getParentPath(getCleanPath(path));

        Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

        if (!checkLocks(transaction, req, ctx, _resourceLocks, parentPath)) {
            errorList.put(parentPath, WebDAVStatus.SC_LOCKED);
            sendReport(req, errorList, null, ctx);
            return; // parent is locked
        }

        if (!checkLocks(transaction, req, ctx, _resourceLocks, path)) {
            errorList.put(path, WebDAVStatus.SC_LOCKED);
            sendReport(req, errorList, null, ctx);
            return; // resource is locked
        }

        // TODO for now, PROPPATCH just sends a valid response, stating that
        // everything is fine, but doesn't do anything.

        // Retrieve the resources
        String tempLockOwner = "doProppatch" + System.currentTimeMillis()
                + req.toString();

        if (_resourceLocks.lock(transaction, path, tempLockOwner, false, 0,
                TEMP_TIMEOUT, TEMPORARY)) {
            IStoredObject so = null;
            ILockedObject lo = null;
            try {
                so = _store.getStoredObject(transaction, path);
                lo = _resourceLocks.getLockedObjectByPath(transaction,
                        getCleanPath(path));

                if (so == null) {
                    sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                    return;
                    // we do not to continue since there is no root
                    // resource
                }

                if (so.isNullResource()) {
                    String methodsAllowed = DeterminableMethod.determineMethodsAllowed(so);
                    MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                    mvt.put("Allow", methodsAllowed);
                    sendError(WebDAVStatus.SC_METHOD_NOT_ALLOWED, mvt, ctx);
                    return;
                }

                if (lo != null && lo.isExclusive()) {
                    // Object on specified path is LOCKED
                    errorList = new Hashtable<String, Integer>();
                    errorList.put(path, new Integer(WebDAVStatus.SC_LOCKED));
                    sendReport(req, errorList, null, ctx);
                    return;
                }

                List<String> toset = null;
                List<String> toremove = null;
                List<String> tochange = new Vector<String>();
                // contains all properties from
                // toset and toremove

                path = getCleanPath(getRelativePath(req));

                Node tosetNode = null;
                Node toremoveNode = null;

                if (req.getContentLength() != 0) {
                    try {
        				DocumentBuilder documentBuilder = getDocumentBuilder();
        				Document document = documentBuilder.parse(new InputSource(req.getRawData().getInputStream()));
        				// Get the root element of the document
        				Element rootElement = document.getDocumentElement();

                        tosetNode = XMLHelper.findSubElement(XMLHelper.findSubElement(rootElement, "set"), "prop");
                        toremoveNode = XMLHelper.findSubElement(XMLHelper.findSubElement(rootElement, "remove"), "prop");
                    } catch (Exception e) {
                        sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                        return;
                    }
                } else {
                    // no content: error
                    sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                    return;
                }

                HashMap<String, String> namespaces = new HashMap<String, String>();
                namespaces.put("DAV:", "D");

                if (tosetNode != null) {
                    toset = XMLHelper.getPropertiesFromXML(tosetNode);
                    tochange.addAll(toset);
                }

                if (toremoveNode != null) {
                    toremove = XMLHelper.getPropertiesFromXML(toremoveNode);
                    tochange.addAll(toremove);
                }

                Bucket xmlBucket = ctx.getBucketFactory().makeBucket(-1);
        		OutputStream os = xmlBucket.getOutputStream();
        		XMLWriter xmlWriter = new XMLWriter(os, namespaces);
        		
                xmlWriter.writeXMLHeader();
                xmlWriter
                        .writeElement("DAV::multistatus", TAG.OPENING);

                xmlWriter.writeElement("DAV::response", TAG.OPENING);
                String status = "HTTP/1.1 " + WebDAVStatus.SC_OK
                        + " " + WebDAVStatus.getStatusText(WebDAVStatus.SC_OK);

                // Generating href element
                xmlWriter.writeElement("DAV::href", TAG.OPENING);

                String href = req.getPath();
                if ((href.endsWith("/")) && (path.startsWith("/")))
                    href += path.substring(1);
                else
                    href += path;
                if ((so.isFolder()) && (!href.endsWith("/")))
                    href += "/";

                xmlWriter.writeText(rewriteUrl(href));

                xmlWriter.writeElement("DAV::href", TAG.CLOSING);

                for (String property: tochange) {
                    xmlWriter.writeElement("DAV::propstat", TAG.OPENING);

                    xmlWriter.writeElement("DAV::prop", TAG.OPENING);
                    xmlWriter.writeElement(property, TAG.NO_CONTENT);
                    xmlWriter.writeElement("DAV::prop", TAG.CLOSING);

                    xmlWriter.writeElement("DAV::status", TAG.OPENING);
                    xmlWriter.writeText(status);
                    xmlWriter.writeElement("DAV::status", TAG.CLOSING);

                    xmlWriter.writeElement("DAV::propstat", TAG.CLOSING);
                }
                xmlWriter.writeElement("DAV::response", TAG.CLOSING);
                xmlWriter.writeElement("DAV::multistatus", TAG.CLOSING);
                xmlWriter.close();
                os.close();

                sendMultiStatus(xmlBucket, null, ctx);
            } catch (AccessDeniedException e) {
                sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
            } catch (WebDAVException e) {
                sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            } finally {
                _resourceLocks.unlockTemporaryLockedObjects(transaction, path,
                        tempLockOwner);
            }
        } else {
            sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
        }
    }
}
