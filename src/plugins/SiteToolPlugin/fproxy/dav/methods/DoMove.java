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

import plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.ObjectAlreadyExistsException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class DoMove extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoMove.class);
	}

    private IResourceLocks _resourceLocks;
    private DoDelete _doDelete;
    private DoCopy _doCopy;
    private boolean _readOnly;

    public DoMove(IResourceLocks resourceLocks, DoDelete doDelete, DoCopy doCopy, boolean readOnly) {
        _resourceLocks = resourceLocks;
        _doDelete = doDelete;
        _doCopy = doCopy;
        _readOnly = readOnly;
    }

	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {

        if (!_readOnly) {
        	if (logDEBUG)
        		Logger.debug(this, "-- " + this.getClass().getName());

            String sourcePath = getRelativePath(req);
            Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();

            if (!checkLocks(transaction, req, ctx, _resourceLocks, sourcePath)) {
                errorList.put(sourcePath, WebDAVStatus.SC_LOCKED);
                sendReport(req, errorList, null, ctx);
                return;
            }

            String destinationPath = req.getHeader("Destination");
            if (destinationPath == null) {
                sendError(WebDAVStatus.SC_BAD_REQUEST, ctx);
                return;
            }

            if (!checkLocks(transaction, req, ctx, _resourceLocks,
                    destinationPath)) {
                errorList.put(destinationPath, WebDAVStatus.SC_LOCKED);
                sendReport(req, errorList, null, ctx);
                return;
            }

            String tempLockOwner = "doMove" + System.currentTimeMillis()
                    + req.toString();

            if (_resourceLocks.lock(transaction, sourcePath, tempLockOwner,
                    false, 0, TEMP_TIMEOUT, TEMPORARY)) {
                try {

                    if (_doCopy.copyResource(transaction, req, ctx)) {

                        errorList = new Hashtable<String, Integer>();
                        _doDelete.deleteResource(transaction, sourcePath,
                                errorList, req, ctx);
                        if (!errorList.isEmpty()) {
                            sendReport(req, errorList, null, ctx);
                        }
                    }

                } catch (AccessDeniedException e) {
                    sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                } catch (ObjectAlreadyExistsException e) {
                    sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                } catch (WebDAVException e) {
                    sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                } finally {
                    _resourceLocks.unlockTemporaryLockedObjects(transaction,
                            sourcePath, tempLockOwner);
                }
            } else {
                errorList.put(req.getHeader("Destination"),
                        WebDAVStatus.SC_LOCKED);
                sendReport(req, errorList, null, ctx);
            }
        } else {
            sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
        }

    }

}
