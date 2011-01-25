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

import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.ObjectAlreadyExistsException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.ObjectNotFoundException;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;

import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;


public class DoDelete extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoDelete.class);
	}

    private IWebDAVStore _store;
    private IResourceLocks _resourceLocks;
    private boolean _readOnly;

    public DoDelete(Toadlet parent, IWebDAVStore store, IResourceLocks resourceLocks, boolean readOnly) {
    	super(parent);
        _store = store;
        _resourceLocks = resourceLocks;
        _readOnly = readOnly;
    }

    public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws IOException, ToadletContextClosedException, WebDAVException {
    	if (logDEBUG)
    		Logger.debug(this, "-- " + this.getClass().getName());

        if (!_readOnly) {
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

            String tempLockOwner = "doDelete" + System.currentTimeMillis()
                    + req.toString();
            if (_resourceLocks.lock(transaction, path, tempLockOwner, false, 0,
                    TEMP_TIMEOUT, TEMPORARY)) {
                try {
                    errorList = new Hashtable<String, Integer>();
                    deleteResource(transaction, path, errorList, req, ctx);
                    if (!errorList.isEmpty()) {
                        sendReport(req, errorList, null, ctx);
                    }
                } catch (AccessDeniedException e) {
                    sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                } catch (ObjectAlreadyExistsException e) {
                    sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
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
     * deletes the recources at "path"
     * 
     * @param transaction
     *      indicates that the method is within the scope of a WebDAV
     *      transaction
     * @param path
     *      the folder to be deleted
     * @param errorList
     *      all errors that ocurred
     * @param req
     *      HTTPRequest
     * @param resp
     *      HttpServletResponse
     * @throws WebdavException
     *      if an error in the underlying store occurs
     * @throws IOException
     *      when an error occurs while sending the response
     * @throws ToadletContextClosedException 
     */
    public void deleteResource(ITransaction transaction, String path,
            Hashtable<String, Integer> errorList, HTTPRequest req,
            ToadletContext ctx) throws IOException, WebDAVException, ToadletContextClosedException {

        if (!_readOnly) {

            IStoredObject so = _store.getStoredObject(transaction, path);
            if (so != null) {

                if (so.isResource()) {
                    _store.removeObject(transaction, path);
                } else {
                    if (so.isFolder()) {
                        deleteFolder(transaction, path, errorList, req, ctx);
                        _store.removeObject(transaction, path);
                    } else {
                        sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                    }
                }
            } else {
                sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
            }
            so = null;
            sendError(WebDAVStatus.SC_NO_CONTENT, ctx);
        } else {
            sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
        }
    }

    /**
     * 
     * helper method of deleteResource() deletes the folder and all of its
     * contents
     * 
     * @param transaction
     *      indicates that the method is within the scope of a WebDAV
     *      transaction
     * @param path
     *      the folder to be deleted
     * @param errorList
     *      all errors that ocurred
     * @param req
     *      HTTPRequest
     * @param resp
     *      HttpServletResponse
     * @throws WebdavException
     *      if an error in the underlying store occurs
     */
    private void deleteFolder(ITransaction transaction, String path,
            Hashtable<String, Integer> errorList, HTTPRequest req,
            ToadletContext ctx) throws WebDAVException {

        String[] children = _store.getChildrenNames(transaction, path);
        children = children == null ? new String[] {} : children;
        IStoredObject so = null;
        for (int i = children.length - 1; i >= 0; i--) {
            children[i] = "/" + children[i];
            try {
                so = _store.getStoredObject(transaction, path + children[i]);
                if (so.isResource()) {
                    _store.removeObject(transaction, path + children[i]);

                } else {
                    deleteFolder(transaction, path + children[i], errorList,
                            req, ctx);

                    _store.removeObject(transaction, path + children[i]);

                }
            } catch (AccessDeniedException e) {
                errorList.put(path + children[i], new Integer(WebDAVStatus.SC_FORBIDDEN));
            } catch (ObjectNotFoundException e) {
                errorList.put(path + children[i], new Integer(WebDAVStatus.SC_NOT_FOUND));
            } catch (WebDAVException e) {
                errorList.put(path + children[i], new Integer(WebDAVStatus.SC_INTERNAL_SERVER_ERROR));
            }
        }
        so = null;

    }

}
