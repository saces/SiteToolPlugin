/*
 * Copyright 1999,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package plugins.SiteToolPlugin.fproxy.dav.methods;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Hashtable;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import plugins.SiteToolPlugin.fproxy.dav.api.ILockedObject;
import plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;
import plugins.SiteToolPlugin.fproxy.dav.util.XMLWriter;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;

public class DoLock extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoLock.class);
	}

    private IWebDAVStore _store;
    private IResourceLocks _resourceLocks;
    private boolean _readOnly;

    private boolean _macLockRequest = false;

    private boolean _exclusive = false;
    private String _type = null;
    private String _lockOwner = null;

    private String _path = null;
    private String _parentPath = null;

    private String _userAgent = null;

    public DoLock(Toadlet parent, IWebDAVStore store, IResourceLocks resourceLocks,boolean readOnly) {
    	super(parent);
    	_store = store;
        _resourceLocks = resourceLocks;
        _readOnly = readOnly;
    }

	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, WebDAVException {
        if (logDEBUG)
        	Logger.debug(this, "-- " + this.getClass().getName());

        if (_readOnly) {
            sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
            return;
        } else {
            _path = getRelativePath(req);
            _parentPath = getParentPath(getCleanPath(_path));

            Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

            if (!checkLocks(transaction, req, ctx, _resourceLocks, _path)) {
                errorList.put(_path, WebDAVStatus.SC_LOCKED);
                sendReport(req, errorList, null, ctx);
                return; // resource is locked
            }

            if (!checkLocks(transaction, req, ctx, _resourceLocks, _parentPath)) {
                errorList.put(_parentPath, WebDAVStatus.SC_LOCKED);
                sendReport(req, errorList, null, ctx);
                return; // parent is locked
            }

            // Mac OS Finder (whether 10.4.x or 10.5) can't store files
            // because executing a LOCK without lock information causes a
            // SC_BAD_REQUEST
            _userAgent = req.getHeader("User-Agent");
            if (_userAgent != null && _userAgent.indexOf("Darwin") != -1) {
                _macLockRequest = true;

                String timeString = new Long(System.currentTimeMillis())
                        .toString();
                _lockOwner = _userAgent.concat(timeString);
            }

            String tempLockOwner = "doLock" + System.currentTimeMillis()
                    + req.toString();
            if (_resourceLocks.lock(transaction, _path, tempLockOwner, false,
                    0, TEMP_TIMEOUT, TEMPORARY)) {
                try {
                    if (req.getHeader("If") != null) {
                        doRefreshLock(transaction, req, ctx);
                    } else {
                        doLock(transaction, req, ctx);
                    }
                } catch (LockFailedException e) {
                    sendError(WebDAVStatus.SC_LOCKED, ctx);
                    e.printStackTrace();
                } finally {
                    _resourceLocks.unlockTemporaryLockedObjects(transaction,
                            _path, tempLockOwner);
                }
            }
        }
    }

    private void doLock(ITransaction transaction, HTTPRequest req,
            ToadletContext ctx) throws IOException, ToadletContextClosedException, WebDAVException {

        IStoredObject so = _store.getStoredObject(transaction, _path);

        if (so != null) {
            doLocking(transaction, req, ctx);
        } else {
            // resource doesn't exist, null-resource lock
            doNullResourceLock(transaction, req, ctx);
        }

        so = null;
        _exclusive = false;
        _type = null;
        _lockOwner = null;

    }

    private void doLocking(ITransaction transaction, HTTPRequest req,
            ToadletContext ctx) throws IOException, ToadletContextClosedException, WebDAVException {

        // Tests if LockObject on requested path exists, and if so, tests
        // exclusivity
        ILockedObject lo = _resourceLocks.getLockedObjectByPath(transaction,
                _path);
        if (lo != null) {
            if (lo.isExclusive()) {
                sendLockFailError(transaction, req, ctx);
                return;
            }
        }
        try {
            // Thats the locking itself
            executeLock(transaction, req, ctx);

        } catch (LockFailedException e) {
            sendLockFailError(transaction, req, ctx);
        } finally {
            lo = null;
        }

    }

    private void doNullResourceLock(ITransaction transaction, HTTPRequest req, ToadletContext ctx) throws IOException, ToadletContextClosedException {

        IStoredObject parentSo, nullSo = null;

        try {
            parentSo = _store.getStoredObject(transaction, _parentPath);
            if (_parentPath != null && parentSo == null) {
                _store.createFolder(transaction, _parentPath);
            } else if (_parentPath != null && parentSo != null && parentSo.isResource()) {
                sendError(WebDAVStatus.SC_PRECONDITION_FAILED, ctx);
                return;
            }

            nullSo = _store.getStoredObject(transaction, _path);
            if (nullSo == null) {
                // resource doesn't exist
                _store.createResource(transaction, _path);

                // FIXME
                // Transmit expects 204 response-code, not 201
//                if (_userAgent != null && _userAgent.indexOf("Transmit") != -1) {
//                	if (logDEBUG)
//                		Logger.debug(this, "DoLock.execute() : do workaround for user agent '"
//                                    + _userAgent + "'");
//                    ctx.setStatus(WebDAVStatus.SC_NO_CONTENT);
//                } else {
//                    ctx.setStatus(WebDAVStatus.SC_CREATED);
//                }

            } else {
                // resource already exists, could not execute null-resource lock
                sendLockFailError(transaction, req, ctx);
                return;
            }
            nullSo = _store.getStoredObject(transaction, _path);
            // define the newly created resource as null-resource
            nullSo.setNullResource(true);

            // Thats the locking itself
            executeLock(transaction, req, ctx);

        } catch (LockFailedException e) {
            sendLockFailError(transaction, req, ctx);
        } catch (WebDAVException e) {
            sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            e.printStackTrace();
        } finally {
            parentSo = null;
            nullSo = null;
        }
    }

    private void doRefreshLock(ITransaction transaction,
            HTTPRequest req, ToadletContext ctx)
            throws IOException, LockFailedException, ToadletContextClosedException {

        String[] lockTokens = getLockIdFromIfHeader(req);
        String lockToken = null;
        if (lockTokens != null)
            lockToken = lockTokens[0];

        if (lockToken != null) {
            // Getting LockObject of specified lockToken in If header
            ILockedObject refreshLo = _resourceLocks.getLockedObjectByID(
                    transaction, lockToken);
            if (refreshLo != null) {
                int timeout = getTimeout(transaction, req);

                refreshLo.refreshTimeout(timeout);
                // sending success response
                generateXMLReport(transaction, ctx, refreshLo);

                refreshLo = null;
            } else {
                // no LockObject to given lockToken
                sendError(WebDAVStatus.SC_PRECONDITION_FAILED, ctx);
            }

        } else {
            sendError(WebDAVStatus.SC_PRECONDITION_FAILED, ctx);
        }
    }

    // ------------------------------------------------- helper methods

    /**
     * Executes the LOCK
     * @throws ToadletContextClosedException 
     * @throws WebDAVException 
     */
    private void executeLock(ITransaction transaction, HTTPRequest req, ToadletContext ctx) throws IOException, ToadletContextClosedException, WebDAVException {

        // Mac OS lock request workaround
        if (_macLockRequest) {
        	if (logDEBUG)
        		Logger.debug(this, "DoLock.execute() : do workaround for user agent '"
                    + _userAgent + "'");

            doMacLockRequestWorkaround(transaction, req, ctx);
        } else {
            // Getting LockInformation from request
            if (getLockInformation(transaction, req, ctx)) {
                int depth = getDepth(req);
                int lockDuration = getTimeout(transaction, req);

                boolean lockSuccess = false;
                if (_exclusive) {
                    lockSuccess = _resourceLocks.exclusiveLock(transaction,
                            _path, _lockOwner, depth, lockDuration);
                } else {
                    lockSuccess = _resourceLocks.sharedLock(transaction, _path,
                            _lockOwner, depth, lockDuration);
                }

                if (lockSuccess) {
                    // Locks successfully placed - return information about
                    ILockedObject lo = _resourceLocks.getLockedObjectByPath(
                            transaction, _path);
                    if (lo != null) {
                        generateXMLReport(transaction, ctx, lo);
                    } else {
                        sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                    }
                } else {
                    sendLockFailError(transaction, req, ctx);

                    throw new LockFailedException();
                }
            } else {
                // information for LOCK could not be read successfully
                //ctx.setContentType("text/xml; charset=UTF-8");
                sendError(WebDAVStatus.SC_BAD_REQUEST, ctx);
            }
        }
    }

    /**
     * Tries to get the LockInformation from LOCK request
     * @throws ToadletContextClosedException 
     * @throws WebDAVException 
     */
    private boolean getLockInformation(ITransaction transaction,
            HTTPRequest req, ToadletContext ctx)
            throws IOException, ToadletContextClosedException, WebDAVException {

        Node lockInfoNode = null;
        DocumentBuilder documentBuilder = null;

        documentBuilder = getDocumentBuilder();
        try {
            Document document = documentBuilder.parse(new InputSource(req.getRawData().getInputStream()));

            // Get the root element of the document
            Element rootElement = document.getDocumentElement();

            lockInfoNode = rootElement;

            if (lockInfoNode != null) {
                NodeList childList = lockInfoNode.getChildNodes();
                Node lockScopeNode = null;
                Node lockTypeNode = null;
                Node lockOwnerNode = null;

                Node currentNode = null;
                String nodeName = null;

                for (int i = 0; i < childList.getLength(); i++) {
                    currentNode = childList.item(i);

                    if (currentNode.getNodeType() == Node.ELEMENT_NODE || currentNode.getNodeType() == Node.TEXT_NODE) {

                        nodeName = currentNode.getNodeName();

                        if (nodeName.endsWith("locktype")) {
                            lockTypeNode = currentNode;
                        }
                        if (nodeName.endsWith("lockscope")) {
                            lockScopeNode = currentNode;
                        }
                        if (nodeName.endsWith("owner")) {
                            lockOwnerNode = currentNode;
                        }
                    } else {
                        return false;
                    }
                }

                if (lockScopeNode != null) {
                    String scope = null;
                    childList = lockScopeNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++) {
                        currentNode = childList.item(i);

                        if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                            scope = currentNode.getNodeName();

                            if (scope.endsWith("exclusive")) {
                                _exclusive = true;
                            } else if (scope.equals("shared")) {
                                _exclusive = false;
                            }
                        }
                    }
                    if (scope == null) {
                        return false;
                    }

                } else {
                    return false;
                }

                if (lockTypeNode != null) {
                    childList = lockTypeNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++) {
                        currentNode = childList.item(i);

                        if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                            _type = currentNode.getNodeName();

                            if (_type.endsWith("write")) {
                                _type = "write";
                            } else if (_type.equals("read")) {
                                _type = "read";
                            }
                        }
                    }
                    if (_type == null) {
                        return false;
                    }
                } else {
                    return false;
                }

                if (lockOwnerNode != null) {
                    childList = lockOwnerNode.getChildNodes();
                    for (int i = 0; i < childList.getLength(); i++) {
                        currentNode = childList.item(i);

                        if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                            _lockOwner = currentNode.getTextContent();
                        }
                    }
                }
                if (_lockOwner == null) {
                    return false;
                }
            } else {
                return false;
            }

        } catch (DOMException e) {
            sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            e.printStackTrace();
            return false;
        } catch (SAXException e) {
            sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Ties to read the timeout from request
     */
    private int getTimeout(ITransaction transaction, HTTPRequest req) {

        int lockDuration = DEFAULT_TIMEOUT;
        String lockDurationStr = req.getHeader("Timeout");

        if (lockDurationStr == null) {
            lockDuration = DEFAULT_TIMEOUT;
        } else {
            int commaPos = lockDurationStr.indexOf(',');
            // if multiple timeouts, just use the first one
            if (commaPos != -1) {
                lockDurationStr = lockDurationStr.substring(0, commaPos);
            }
            if (lockDurationStr.startsWith("Second-")) {
                lockDuration = new Integer(lockDurationStr.substring(7))
                        .intValue();
            } else {
                if (lockDurationStr.equalsIgnoreCase("infinity")) {
                    lockDuration = MAX_TIMEOUT;
                } else {
                    try {
                        lockDuration = new Integer(lockDurationStr).intValue();
                    } catch (NumberFormatException e) {
                        lockDuration = MAX_TIMEOUT;
                    }
                }
            }
            if (lockDuration <= 0) {
                lockDuration = DEFAULT_TIMEOUT;
            }
            if (lockDuration > MAX_TIMEOUT) {
                lockDuration = MAX_TIMEOUT;
            }
        }
        return lockDuration;
    }

    /**
     * Generates the response XML with all lock information
     * @throws ToadletContextClosedException 
     */
    private void generateXMLReport(ITransaction transaction, ToadletContext ctx, ILockedObject lo) throws IOException, ToadletContextClosedException {

        HashMap<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("DAV:", "D");

        Bucket xmlBucket = ctx.getBucketFactory().makeBucket(-1);
		OutputStream os = xmlBucket.getOutputStream();
		XMLWriter xmlWriter = new XMLWriter(os, namespaces);

        xmlWriter.writeXMLHeader();
        xmlWriter.writeElement("DAV::prop", XMLWriter.OPENING);
        xmlWriter.writeElement("DAV::lockdiscovery", XMLWriter.OPENING);
        xmlWriter.writeElement("DAV::activelock", XMLWriter.OPENING);

        xmlWriter.writeElement("DAV::locktype", XMLWriter.OPENING);
        xmlWriter.writeProperty("DAV::" + _type);
        xmlWriter.writeElement("DAV::locktype", XMLWriter.CLOSING);

        xmlWriter.writeElement("DAV::lockscope", XMLWriter.OPENING);
        if (_exclusive) {
            xmlWriter.writeProperty("DAV::exclusive");
        } else {
            xmlWriter.writeProperty("DAV::shared");
        }
        xmlWriter.writeElement("DAV::lockscope", XMLWriter.CLOSING);

        int depth = lo.getLockDepth();

        xmlWriter.writeElement("DAV::depth", XMLWriter.OPENING);
        if (depth == INFINITY) {
            xmlWriter.writeText("Infinity");
        } else {
            xmlWriter.writeText(String.valueOf(depth));
        }
        xmlWriter.writeElement("DAV::depth", XMLWriter.CLOSING);

        xmlWriter.writeElement("DAV::owner", XMLWriter.OPENING);
        xmlWriter.writeElement("DAV::href", XMLWriter.OPENING);
        xmlWriter.writeText(_lockOwner);
        xmlWriter.writeElement("DAV::href", XMLWriter.CLOSING);
        xmlWriter.writeElement("DAV::owner", XMLWriter.CLOSING);

        long timeout = lo.getTimeoutMillis();
        xmlWriter.writeElement("DAV::timeout", XMLWriter.OPENING);
        xmlWriter.writeText("Second-" + timeout / 1000);
        xmlWriter.writeElement("DAV::timeout", XMLWriter.CLOSING);

        String lockToken = lo.getID();
        xmlWriter.writeElement("DAV::locktoken", XMLWriter.OPENING);
        xmlWriter.writeElement("DAV::href", XMLWriter.OPENING);
        xmlWriter.writeText("opaquelocktoken:" + lockToken);
        xmlWriter.writeElement("DAV::href", XMLWriter.CLOSING);
        xmlWriter.writeElement("DAV::locktoken", XMLWriter.CLOSING);

        xmlWriter.writeElement("DAV::activelock", XMLWriter.CLOSING);
        xmlWriter.writeElement("DAV::lockdiscovery", XMLWriter.CLOSING);
        xmlWriter.writeElement("DAV::prop", XMLWriter.CLOSING);

        xmlWriter.close();
        os.close();

        MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
        mvt.put("Lock-Token", "<opaquelocktoken:" + lockToken + ">");

        sendXMLStatus(WebDAVStatus.SC_OK, xmlBucket, mvt, ctx);

    }

    /**
     * Executes the lock for a Mac OS Finder client
     * @throws ToadletContextClosedException 
     */
    private void doMacLockRequestWorkaround(ITransaction transaction,
            HTTPRequest req, ToadletContext ctx)
            throws LockFailedException, IOException, ToadletContextClosedException {
        ILockedObject lo;
        int depth = getDepth(req);
        int lockDuration = getTimeout(transaction, req);
        if (lockDuration < 0 || lockDuration > MAX_TIMEOUT)
            lockDuration = DEFAULT_TIMEOUT;

        boolean lockSuccess = false;
        lockSuccess = _resourceLocks.exclusiveLock(transaction, _path,
                _lockOwner, depth, lockDuration);

        if (lockSuccess) {
            // Locks successfully placed - return information about
            lo = _resourceLocks.getLockedObjectByPath(transaction, _path);
            if (lo != null) {
                generateXMLReport(transaction, ctx, lo);
            } else {
                sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            }
        } else {
            // Locking was not successful
            sendLockFailError(transaction, req, ctx);
        }
    }

    /**
     * Sends an error report to the client
     * @throws ToadletContextClosedException 
     */
    private void sendLockFailError(ITransaction transaction,
            HTTPRequest req, ToadletContext ctx)
            throws IOException, ToadletContextClosedException {
        Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
        errorList.put(_path, WebDAVStatus.SC_LOCKED);
        sendReport(req, errorList, null, ctx);
    }

}
