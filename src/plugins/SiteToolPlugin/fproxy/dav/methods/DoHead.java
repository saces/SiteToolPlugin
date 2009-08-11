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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package plugins.SiteToolPlugin.fproxy.dav.methods;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.clients.http.ToadletContextImpl;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.TimeUtil;
import freenet.support.api.HTTPRequest;

import plugins.SiteToolPlugin.fproxy.dav.api.IMimeTyper;
import plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.ObjectAlreadyExistsException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;

public class DoHead extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoHead.class);
	}

    protected String _dftIndexFile;
    protected IWebDAVStore _store;
    protected String _insteadOf404;
    protected IResourceLocks _resourceLocks;
    protected IMimeTyper _mimeTyper;
    protected int _contentLength;

    public DoHead(IWebDAVStore store, String dftIndexFile, String insteadOf404,
            IResourceLocks resourceLocks, IMimeTyper mimeTyper,
            int contentLengthHeader) {
        _store = store;
        _dftIndexFile = dftIndexFile;
        _insteadOf404 = insteadOf404;
        _resourceLocks = resourceLocks;
        _mimeTyper = mimeTyper;
        _contentLength = contentLengthHeader;
    }

	public void handle(ITransaction transaction, URI uri, HTTPRequest req,
			ToadletContext ctx) throws ToadletContextClosedException,
			IOException, RedirectException, WebDAVException {

        // determines if the uri exists.

        boolean bUriExists = false;

        String path = getRelativePath(req);
        if (logDEBUG)
        	Logger.debug(this, "-- " + this.getClass().getName());

        IStoredObject so = _store.getStoredObject(transaction, path);
        if (so == null) {
            if (this._insteadOf404 != null && !_insteadOf404.trim().equals("")) {
                path = this._insteadOf404;
                so = _store.getStoredObject(transaction, this._insteadOf404);
            }
        } else
            bUriExists = true;

        if (so != null) {
            if (so.isFolder()) {
                if (_dftIndexFile != null && !_dftIndexFile.trim().equals("")) {
                	URI newUri = null;
					try {
						newUri = new URI(req.getPath()+ this._dftIndexFile);
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                	throw new RedirectException(newUri);
                    //return;
                }
            } else if (so.isNullResource()) {
                String methodsAllowed = DeterminableMethod.determineMethodsAllowed(so);
                MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                mvt.put("Allow", methodsAllowed);
                sendError(WebDAVStatus.SC_METHOD_NOT_ALLOWED, mvt, ctx);
                return;
            }

            String tempLockOwner = "doGet" + System.currentTimeMillis()
                    + req.toString();

            if (_resourceLocks.lock(transaction, path, tempLockOwner, false, 0,
                    TEMP_TIMEOUT, TEMPORARY)) {
                try {

                    String eTagMatch = req.getHeader("If-None-Match");
                    if (eTagMatch != null) {
                        if (eTagMatch.equals(getETag(so))) {
                            sendError(WebDAVStatus.SC_NOT_MODIFIED, ctx);
                            return;
                        }
                    }

                    if (so.isResource()) {
                        // path points to a file but ends with / or \
                        if (path.endsWith("/") || (path.endsWith("\\"))) {
                            sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                                    // FIXME req.getRequestURI());
                        } else {

                            // setting headers
                            long lastModified = so.getLastModified().getTime();
                            
                            MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                            mvt.put("last-modified", TimeUtil.makeHTTPDate(lastModified));

                            String eTag = getETag(so);
                            mvt.put("ETag", eTag);

                            long resourceLength = so.getResourceLength();

                            if (_contentLength == 1) {
                                if (resourceLength > 0) {
                                    mvt.put("content-length", "" + resourceLength);
                                        // is "content-length" the right header?
                                        // is long a valid format?
                                }
                            }

                            String mimeType = _mimeTyper.getMimeType(path);
                            if (mimeType != null) {
                                mvt.put("content-type", mimeType);
                            } else {
                                int lastSlash = path.replace('\\', '/')
                                        .lastIndexOf('/');
                                int lastDot = path.indexOf(".", lastSlash);
                                if (lastDot == -1) {
                                	mvt.put("content-type", "text/html");
                                }
                            }

                            doBody(transaction, path, ctx);
                        }
                    } else {
                        folderBody(transaction, path, req, ctx);
                    }
                } catch (AccessDeniedException e) {
                    sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                } catch (ObjectAlreadyExistsException e) {
                    sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                           // FIXME .getRequestURI());
                } catch (WebDAVException e) {
                    sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
                } finally {
                    _resourceLocks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
                }
            } else {
                sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            }
        } else {
            folderBody(transaction, path, req, ctx);
        }

        if (!bUriExists)
        	sendError(WebDAVStatus.SC_NOT_FOUND, ctx);

    }

    protected void folderBody(ITransaction transaction, String path, HTTPRequest req, ToadletContext ctx) throws IOException, ToadletContextClosedException, WebDAVException {
        // no body for HEAD
    }

    protected void doBody(ITransaction transaction, String path, ToadletContext ctx) throws IOException {
        // no body for HEAD
    }
}
