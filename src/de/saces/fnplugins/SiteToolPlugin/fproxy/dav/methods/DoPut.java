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
package de.saces.fnplugins.SiteToolPlugin.fproxy.dav.methods;

import java.io.IOException;
import java.net.URI;
import java.util.Hashtable;

import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.ILockedObject;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;

import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class DoPut extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoPut.class);
	}

    private IWebDAVStore _store;
    private IResourceLocks _resourceLocks;
    private boolean _readOnly;
    private boolean _lazyFolderCreationOnPut;

    private String _userAgent;

    public DoPut(Toadlet parent, IWebDAVStore store, IResourceLocks resLocks, boolean readOnly,
            boolean lazyFolderCreationOnPut) {
    	super(parent);
        _store = store;
        _resourceLocks = resLocks;
        _readOnly = readOnly;
        _lazyFolderCreationOnPut = lazyFolderCreationOnPut;
    }

	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {
		if (logDEBUG)
			Logger.debug(this, "-- " + this.getClass().getName());

        if (!_readOnly) {
            String path = getRelativePath(req);
            String parentPath = getParentPath(path);

            _userAgent = req.getHeader("User-Agent");

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

            String tempLockOwner = "doPut" + System.currentTimeMillis()
                    + req.toString();
            if (_resourceLocks.lock(transaction, path, tempLockOwner, false, 0,
                    TEMP_TIMEOUT, TEMPORARY)) {
                IStoredObject parentSo, so = null;
                try {
                    parentSo = _store.getStoredObject(transaction, parentPath);
                    if (parentPath != null && parentSo != null
                            && parentSo.isResource()) {
                        sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                        return;

                    } else if (parentPath != null && parentSo == null
                            && _lazyFolderCreationOnPut) {
                        _store.createFolder(transaction, parentPath);

                    } else if (parentPath != null && parentSo == null
                            && !_lazyFolderCreationOnPut) {
                        errorList.put(parentPath, WebDAVStatus.SC_NOT_FOUND);
                        sendReport(req, errorList, null, ctx);
                        return;
                    }

                    so = _store.getStoredObject(transaction, path);

                    int returnCode;
                    if (so == null) {
                        _store.createResource(transaction, path);
                        returnCode = WebDAVStatus.SC_CREATED;
                    } else {
                        // This has already been created, just update the data
                        if (so.isNullResource()) {

                            ILockedObject nullResourceLo = _resourceLocks
                                    .getLockedObjectByPath(transaction, path);
                            if (nullResourceLo == null) {
                                sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                                return;
                            }
                            String nullResourceLockToken = nullResourceLo
                                    .getID();
                            String[] lockTokens = getLockIdFromIfHeader(req);
                            String lockToken = null;
                            if (lockTokens != null) {
                                lockToken = lockTokens[0];
                            } else {
                                sendError(WebDAVStatus.SC_BAD_REQUEST, ctx);
                                return;
                            }
                            if (lockToken.equals(nullResourceLockToken)) {
                                so.setNullResource(false);
                                so.setFolder(false);

                                String[] nullResourceLockOwners = nullResourceLo
                                        .getOwner();
                                String owner = null;
                                if (nullResourceLockOwners != null)
                                    owner = nullResourceLockOwners[0];

                                if (!_resourceLocks.unlock(transaction,
                                        lockToken, owner)) {
                                    sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                                }
                            } else {
                                errorList.put(path, WebDAVStatus.SC_LOCKED);
                                sendReport(req, errorList, null, ctx);
                            }
                        }
                    }
                    // User-Agent workarounds
                    returnCode = doUserAgentWorkaround(ctx);

                    // setting resourceContent
                    long resourceLength = _store.setResourceContent(transaction, path, req.getRawData(), null, null);

                    so = _store.getStoredObject(transaction, path);
                    if (resourceLength != -1)
                        so.setResourceLength(resourceLength);
                    // Now lets report back what was actually saved

                    sendError(returnCode, ctx);
                } catch (AccessDeniedException e) {
                    sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                } catch (WebDAVException e) {
                    sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                } finally {
                    _resourceLocks.unlockTemporaryLockedObjects(transaction,
                            path, tempLockOwner);
                }
            } else {
                sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            }
        } else {
            sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
        }

    }

	/**
	 * @param resp
	 */
	private int doUserAgentWorkaround(ToadletContext ctx) {
		if (_userAgent != null && _userAgent.indexOf("WebDAVFS") != -1 && _userAgent.indexOf("Transmit") == -1) {
			if (logDEBUG)
				Logger.debug(this, "DoPut.execute() : do workaround for user agent '" + _userAgent + "'");
			return WebDAVStatus.SC_CREATED;
		} else if (_userAgent != null && _userAgent.indexOf("Transmit") != -1) {
			// Transmit also uses WEBDAVFS 1.x.x but crashes
			// with SC_CREATED response
			if (logDEBUG)
				Logger.debug(this, "DoPut.execute() : do workaround for user agent '" + _userAgent + "'");
            return WebDAVStatus.SC_NO_CONTENT;
        } else {
            return WebDAVStatus.SC_CREATED;
        }
    }
}
