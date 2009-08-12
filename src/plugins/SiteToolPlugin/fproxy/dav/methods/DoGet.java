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

import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;

import plugins.SiteToolPlugin.fproxy.dav.api.IMimeTyper;
import plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;

public class DoGet extends DoHead {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoGet.class);
	}

    public DoGet(Toadlet parent, IWebDAVStore store, String dftIndexFile, String insteadOf404,
            IResourceLocks resourceLocks, IMimeTyper mimeTyper,
            int contentLengthHeader) {
        super(parent, store, dftIndexFile, insteadOf404, resourceLocks, mimeTyper, contentLengthHeader);

    }

    @Override
	protected void doBody(ITransaction transaction, String path, ToadletContext ctx) {

        try {
            IStoredObject so = _store.getStoredObject(transaction, path);
            if (so.isNullResource()) {
                String methodsAllowed = DeterminableMethod.determineMethodsAllowed(so);
                MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                mvt.put("Allow", methodsAllowed);
                sendError(WebDAVStatus.SC_METHOD_NOT_ALLOWED, mvt, ctx);
                return;
            }
            Bucket out = _store.getResourceContent(transaction, path);
            ctx.writeData(out);
        } catch (Exception e) {
        	Logger.error(this, "DEBUG", e);
        }
    }

    @Override
	protected void folderBody(ITransaction transaction, String path, HTTPRequest req, ToadletContext ctx) throws IOException, ToadletContextClosedException, WebDAVException {

        IStoredObject so = _store.getStoredObject(transaction, path);
        if (so == null) {
            sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                // FIXME    .getRequestURI());
        } else {

            if (so.isNullResource()) {
                String methodsAllowed = DeterminableMethod.determineMethodsAllowed(so);
                MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                mvt.put("Allow", methodsAllowed);
                sendError(WebDAVStatus.SC_METHOD_NOT_ALLOWED, mvt, ctx);
                return;
            }

            if (so.isFolder()) {
                // TODO some folder response (for browsers, DAV tools
                // use propfind) in html?
            	Bucket bucket = ctx.getBucketFactory().makeBucket(-1);
                OutputStream out = bucket.getOutputStream();
                String[] children = _store.getChildrenNames(transaction, path);
                children = children == null ? new String[] {} : children;
                StringBuffer childrenTemp = new StringBuffer();
                childrenTemp.append("Contents of this Folder:\n");
                for (String child : children) {
                    childrenTemp.append(child);
                    childrenTemp.append("\n");
                }
                out.write(childrenTemp.toString().getBytes());
                out.close();
                ctx.writeData(bucket);
            }
        }
    }

}
