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

import plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

public class DoOptions extends DeterminableMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoOptions.class);
	}

    private IWebDAVStore _store;
    private IResourceLocks _resourceLocks;

    public DoOptions(IWebDAVStore store, IResourceLocks resLocks) {
        _store = store;
        _resourceLocks = resLocks;
    }

	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {
        if (logDEBUG)
        	Logger.debug(this, "-- " + this.getClass().getName());

        String tempLockOwner = "doOptions" + System.currentTimeMillis() + req.toString();
        String path = getRelativePath(req);
        if (_resourceLocks.lock(transaction, path, tempLockOwner, false, 0,TEMP_TIMEOUT, TEMPORARY)) {
            IStoredObject so = null;
            try {
                so = _store.getStoredObject(transaction, path);
                String methodsAllowed = determineMethodsAllowed(so);
                MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                mvt.put("DAV", "1, 2");
                mvt.put("Allow", methodsAllowed);
                mvt.put("MS-Author-Via", "DAV");
                sendError(WebDAVStatus.SC_OK, mvt, ctx);
            } catch (AccessDeniedException e) {
                sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
            } catch (WebDAVException e) {
                sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            } finally {
                _resourceLocks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
            sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
        }
    }
}
