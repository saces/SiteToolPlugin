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
import java.net.URI;
import java.util.Hashtable;

import plugins.SiteToolPlugin.fproxy.dav.api.ILockedObject;
import plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

public class DoMkcol extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoMkcol.class);
	}

    private IWebDAVStore _store;
    private IResourceLocks _resourceLocks;
    private boolean _readOnly;

    public DoMkcol(Toadlet parent, IWebDAVStore store, IResourceLocks resourceLocks,
            boolean readOnly) {
    	super(parent);
        _store = store;
        _resourceLocks = resourceLocks;
        _readOnly = readOnly;
    }

    public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {
        if (logDEBUG)
        	Logger.debug(this, "-- " + this.getClass().getName());

        if (!_readOnly) {
            String path = getRelativePath(req);
            String parentPath = getParentPath(getCleanPath(path));

            Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

            if (!checkLocks(transaction, req, ctx, _resourceLocks, parentPath)) {
                // TODO remove
            	if (logDEBUG)
            		Logger.debug(this, "MkCol on locked resource (parentPath) not executable!"
                                + "\n Sending SC_FORBIDDEN (403) error response!");

                sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                return;
            }

            String tempLockOwner = "doMkcol" + System.currentTimeMillis()
                    + req.toString();

            if (_resourceLocks.lock(transaction, path, tempLockOwner, false, 0,
                    TEMP_TIMEOUT, TEMPORARY)) {
                IStoredObject parentSo, so = null;
                try {
                    parentSo = _store.getStoredObject(transaction, parentPath);
                    if (parentPath != null && parentSo != null
                            && parentSo.isFolder()) {
                        so = _store.getStoredObject(transaction, path);
                        if (so == null) {
                            _store.createFolder(transaction, path);
                            // FIXME ctx.setStatus(WebDAVStatus.SC_CREATED);
                        } else {
                            // object already exists
                            if (so.isNullResource()) {

                                ILockedObject nullResourceLo = _resourceLocks
                                        .getLockedObjectByPath(transaction,
                                                path);
                                if (nullResourceLo == null) {
                                    sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                                    return;
                                }
                                String nullResourceLockToken = nullResourceLo
                                        .getID();
                                String[] lockTokens = getLockIdFromIfHeader(req);
                                String lockToken = null;
                                if (lockTokens != null)
                                    lockToken = lockTokens[0];
                                else {
                                    sendError(WebDAVStatus.SC_BAD_REQUEST, ctx);
                                    return;
                                }
                                if (lockToken.equals(nullResourceLockToken)) {
                                    so.setNullResource(false);
                                    so.setFolder(true);

                                    String[] nullResourceLockOwners = nullResourceLo
                                            .getOwner();
                                    String owner = null;
                                    if (nullResourceLockOwners != null)
                                        owner = nullResourceLockOwners[0];

                                    if (_resourceLocks.unlock(transaction,
                                            lockToken, owner)) {
                                        //FIXME ctx.setStatus(WebDAVStatus.SC_CREATED);
                                    } else {
                                        sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                                    }

                                } else {
                                    // TODO remove
                                	if (logDEBUG)
                                		Logger.debug(this, "MkCol on lock-null-resource with wrong lock-token!"
                                                    + "\n Sending multistatus error report!");

                                    errorList.put(path, WebDAVStatus.SC_LOCKED);
                                    sendReport(req, errorList, null, ctx);
                                }

                            } else {
                                String methodsAllowed = DeterminableMethod.determineMethodsAllowed(so);
                                MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                                mvt.put("Allow", methodsAllowed);
                                sendError(WebDAVStatus.SC_METHOD_NOT_ALLOWED, mvt, ctx);
                            }
                        }

                    } else if (parentPath != null && parentSo != null
                            && parentSo.isResource()) {
                        // TODO remove
                    	if (logDEBUG)
                    		Logger.debug(this, "MkCol on resource is not executable"
                                        + "\n Sending SC_METHOD_NOT_ALLOWED (405) error response!");

                        String methodsAllowed = DeterminableMethod.determineMethodsAllowed(parentSo);
                        MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                        mvt.put("Allow", methodsAllowed);
                        sendError(WebDAVStatus.SC_METHOD_NOT_ALLOWED, mvt, ctx);

                    } else if (parentPath != null && parentSo == null) {
                        // TODO remove
                    	if (logDEBUG)
                    		Logger.debug(this, "MkCol on non-existing resource is not executable"
                                        + "\n Sending SC_NOT_FOUND (404) error response!");

                        errorList.put(parentPath, WebDAVStatus.SC_NOT_FOUND);
                        sendReport(req, errorList, null, ctx);
                    } else {
                        sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                    }
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

}
