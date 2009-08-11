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

import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks;
import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.AccessDeniedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.ObjectAlreadyExistsException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.ObjectNotFoundException;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;

public class DoCopy extends AbstractMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoCopy.class);
	}

    private IWebDAVStore _store;
    private IResourceLocks _resourceLocks;
    private DoDelete _doDelete;
    private boolean _readOnly;

    public DoCopy(IWebDAVStore store, IResourceLocks resourceLocks,
            DoDelete doDelete, boolean readOnly) {
        _store = store;
        _resourceLocks = resourceLocks;
        _doDelete = doDelete;
        _readOnly = readOnly;
    }

    
	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {
        if (logDEBUG)
        	Logger.debug(this, "-- " + this.getClass().getName());

        String path = getRelativePath(req);
        if (!_readOnly) {

            String tempLockOwner = "doCopy" + System.currentTimeMillis()
                    + req.toString();
            if (_resourceLocks.lock(transaction, path, tempLockOwner, false, 0,
                    TEMP_TIMEOUT, TEMPORARY)) {
                try {
                    if (!copyResource(transaction, req, ctx))
                        return;
                } catch (AccessDeniedException e) {
                    sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
                } catch (ObjectAlreadyExistsException e) {
                    sendError(WebDAVStatus.SC_CONFLICT, ctx);
                        // FIXME    .getRequestURI());
                } catch (ObjectNotFoundException e) {
                    sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                         // FIXME   .getRequestURI());
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
     * Copy a resource.
     * 
     * @param transaction
     *      indicates that the method is within the scope of a WebDAV
     *      transaction
     * @param req
     *      Servlet request
     * @param resp
     *      Servlet response
     * @return true if the copy is successful
     * @throws WebdavException
     *      if an error in the underlying store occurs
     * @throws IOException
     *      when an error occurs while sending the response
     * @throws LockFailedException
     * @throws ToadletContextClosedException 
     */
    public boolean copyResource(ITransaction transaction, HTTPRequest req, ToadletContext ctx) throws WebDAVException, IOException, LockFailedException, ToadletContextClosedException {

        // Parsing destination header
        String destinationPath = parseDestinationHeader(req, ctx);

        if (destinationPath == null)
            return false;

        String path = getRelativePath(req);

        if (path.equals(destinationPath)) {
            sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
            return false;
        }

        Hashtable<String, Integer> errorList = new Hashtable<String, Integer>();
        String parentDestinationPath = getParentPath(getCleanPath(destinationPath));

        if (!checkLocks(transaction, req, ctx, _resourceLocks,
                parentDestinationPath)) {
            errorList.put(parentDestinationPath, WebDAVStatus.SC_LOCKED);
            sendReport(req, errorList, null, ctx);
            return false; // parentDestination is locked
        }

        if (!checkLocks(transaction, req, ctx, _resourceLocks, destinationPath)) {
            errorList.put(destinationPath, WebDAVStatus.SC_LOCKED);
            sendReport(req, errorList, null, ctx);
            return false; // destination is locked
        }

        // Parsing overwrite header

        boolean overwrite = true;
        String overwriteHeader = req.getHeader("Overwrite");

        if (overwriteHeader != null) {
            overwrite = overwriteHeader.equalsIgnoreCase("T");
        }

        // Overwriting the destination
        String lockOwner = "copyResource" + System.currentTimeMillis()
                + req.toString();

        if (_resourceLocks.lock(transaction, destinationPath, lockOwner, false,
                0, TEMP_TIMEOUT, TEMPORARY)) {
            IStoredObject copySo, destinationSo = null;
            try {
                copySo = _store.getStoredObject(transaction, path);
                // Retrieve the resources
                if (copySo == null) {
                    sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
                    return false;
                }

                if (copySo.isNullResource()) {
                    String methodsAllowed = DeterminableMethod.determineMethodsAllowed(copySo);
                    MultiValueTable<String, String> mvt = new MultiValueTable<String, String>();
                    mvt.put("Allow", methodsAllowed);
                    sendError(WebDAVStatus.SC_METHOD_NOT_ALLOWED, mvt, ctx);
                    return false;
                }

                errorList = new Hashtable<String, Integer>();

                destinationSo = _store.getStoredObject(transaction,
                        destinationPath);

                if (overwrite) {

                    // Delete destination resource, if it exists
                    if (destinationSo != null) {
                        _doDelete.deleteResource(transaction, destinationPath,
                                errorList, req, ctx);

                    } else {
                        sendError(WebDAVStatus.SC_CREATED, ctx);
                    }
                } else {

                    // If the destination exists, then it's a conflict
                    if (destinationSo != null) {
                        sendError(WebDAVStatus.SC_PRECONDITION_FAILED, ctx);
                        return false;
                    } else {
                        sendError(WebDAVStatus.SC_CREATED, ctx);
                    }

                }
                copy(transaction, path, destinationPath, errorList, req, ctx);

                if (!errorList.isEmpty()) {
                    sendReport(req, errorList, null, ctx);
                }

            } finally {
                _resourceLocks.unlockTemporaryLockedObjects(transaction,
                        destinationPath, lockOwner);
            }
        } else {
            sendError(WebDAVStatus.SC_INTERNAL_SERVER_ERROR, ctx);
            return false;
        }
        return true;

    }

    /**
     * copies the specified resource(s) to the specified destination.
     * preconditions must be handled by the caller. Standard status codes must
     * be handled by the caller. a multi status report in case of errors is
     * created here.
     * 
     * @param transaction
     *      indicates that the method is within the scope of a WebDAV
     *      transaction
     * @param sourcePath
     *      path from where to read
     * @param destinationPath
     *      path where to write
     * @param req
     *      HTTPRequest
     * @param resp
     *      HttpServletResponse
     * @throws WebdavException
     *      if an error in the underlying store occurs
     * @throws IOException
     * @throws ToadletContextClosedException 
     */
    private void copy(ITransaction transaction, String sourcePath,
            String destinationPath, Hashtable<String, Integer> errorList,
            HTTPRequest req, ToadletContext ctx)
            throws WebDAVException, IOException, ToadletContextClosedException {

        IStoredObject sourceSo = _store.getStoredObject(transaction, sourcePath);
        if (sourceSo.isResource()) {
            _store.createResource(transaction, destinationPath);
            long resourceLength = _store.setResourceContent(transaction,
                    destinationPath, _store.getResourceContent(transaction,
                            sourcePath), null, null);

            if (resourceLength != -1) {
                IStoredObject destinationSo = _store.getStoredObject(
                        transaction, destinationPath);
                destinationSo.setResourceLength(resourceLength);
            }

        } else {

            if (sourceSo.isFolder()) {
                copyFolder(transaction, sourcePath, destinationPath, errorList,
                        req, ctx);
            } else {
                sendError(WebDAVStatus.SC_NOT_FOUND, ctx);
            }
        }
    }

    /**
     * helper method of copy() recursively copies the FOLDER at source path to
     * destination path
     * 
     * @param transaction
     *      indicates that the method is within the scope of a WebDAV
     *      transaction
     * @param sourcePath
     *      where to read
     * @param destinationPath
     *      where to write
     * @param errorList
     *      all errors that ocurred
     * @param req
     *      HTTPRequest
     * @param resp
     *      HttpServletResponse
     * @throws WebdavException
     *      if an error in the underlying store occurs
     */
    private void copyFolder(ITransaction transaction, String sourcePath,
            String destinationPath, Hashtable<String, Integer> errorList,
            HTTPRequest req, ToadletContext ctx)
            throws WebDAVException {

        _store.createFolder(transaction, destinationPath);
        boolean infiniteDepth = true;
        String depth = req.getHeader("Depth");
        if (depth != null) {
            if (depth.equals("0")) {
                infiniteDepth = false;
            }
        }
        if (infiniteDepth) {
            String[] children = _store
                    .getChildrenNames(transaction, sourcePath);
            children = children == null ? new String[] {} : children;

            IStoredObject childSo;
            for (int i = children.length - 1; i >= 0; i--) {
                children[i] = "/" + children[i];
                try {
                    childSo = _store.getStoredObject(transaction,
                            (sourcePath + children[i]));
                    if (childSo.isResource()) {
                        _store.createResource(transaction, destinationPath
                                + children[i]);
                        long resourceLength = _store.setResourceContent(
                                transaction, destinationPath + children[i],
                                _store.getResourceContent(transaction,
                                        sourcePath + children[i]), null, null);

                        if (resourceLength != -1) {
                            IStoredObject destinationSo = _store
                                    .getStoredObject(transaction,
                                            destinationPath + children[i]);
                            destinationSo.setResourceLength(resourceLength);
                        }

                    } else {
                        copyFolder(transaction, sourcePath + children[i],
                                destinationPath + children[i], errorList, req,
                                ctx);
                    }
                } catch (AccessDeniedException e) {
                    errorList.put(destinationPath + children[i], new Integer(
                            WebDAVStatus.SC_FORBIDDEN));
                } catch (ObjectNotFoundException e) {
                    errorList.put(destinationPath + children[i], new Integer(
                            WebDAVStatus.SC_NOT_FOUND));
                } catch (ObjectAlreadyExistsException e) {
                    errorList.put(destinationPath + children[i], new Integer(
                            WebDAVStatus.SC_CONFLICT));
                } catch (WebDAVException e) {
                    errorList.put(destinationPath + children[i], new Integer(
                            WebDAVStatus.SC_INTERNAL_SERVER_ERROR));
                }
            }
        }
    }

    /**
     * Parses and normalizes the destination header.
     * 
     * @param req
     *      Servlet request
     * @param resp
     *      Servlet response
     * @return destinationPath
     * @throws IOException
     *      if an error occurs while sending response
     * @throws ToadletContextClosedException 
     */
    private String parseDestinationHeader(HTTPRequest req,
            ToadletContext ctx) throws IOException, ToadletContextClosedException {
        String destinationPath = req.getHeader("Destination");

        if (destinationPath == null) {
            sendError(WebDAVStatus.SC_BAD_REQUEST, ctx);
            return null;
        }

        // Remove url encoding from destination
        //destinationPath = RequestUtil.URLDecode(destinationPath, "UTF8");

        int protocolIndex = destinationPath.indexOf("://");
        if (protocolIndex >= 0) {
            // if the Destination URL contains the protocol, we can safely
            // trim everything upto the first "/" character after "://"
            int firstSeparator = destinationPath
                    .indexOf("/", protocolIndex + 4);
            if (firstSeparator < 0) {
                destinationPath = "/";
            } else {
                destinationPath = destinationPath.substring(firstSeparator);
            }
        } else {
            String hostName = req.getHeader("Host");
            if ((hostName != null) && (destinationPath.startsWith(hostName))) {
                destinationPath = destinationPath.substring(hostName.length());
            }

            int portIndex = destinationPath.indexOf(":");
            if (portIndex >= 0) {
                destinationPath = destinationPath.substring(portIndex);
            }

            if (destinationPath.startsWith(":")) {
                int firstSeparator = destinationPath.indexOf("/");
                if (firstSeparator < 0) {
                    destinationPath = "/";
                } else {
                    destinationPath = destinationPath.substring(firstSeparator);
                }
            }
        }

        // Normalize destination path (remove '.' and' ..')
        destinationPath = normalize(destinationPath);

        String contextPath = req.getPath();
        if ((contextPath != null) && (destinationPath.startsWith(contextPath))) {
            destinationPath = destinationPath.substring(contextPath.length());
        }

        String pathInfo = req.getPath();
        if (pathInfo != null) {
            String servletPath = req.getPath();
            if ((servletPath != null)
                    && (destinationPath.startsWith(servletPath))) {
                destinationPath = destinationPath.substring(servletPath
                        .length());
            }
        }

        return destinationPath;
    }

    /**
     * Return a context-relative path, beginning with a "/", that represents the
     * canonical version of the specified path after ".." and "." elements are
     * resolved out. If the specified path attempts to go outside the boundaries
     * of the current context (i.e. too many ".." path elements are present),
     * return <code>null</code> instead.
     * 
     * @param path
     *      Path to be normalized
     * @return normalized path
     */
    protected String normalize(String path) {

        if (path == null)
            return null;

        // Create a place for the normalized path
        String normalized = path;

        if (normalized.equals("/."))
            return "/";

        // Normalize the slashes and add leading slash if necessary
        if (normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');
        if (!normalized.startsWith("/"))
            normalized = "/" + normalized;

        // Resolve occurrences of "//" in the normalized path
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index)
                    + normalized.substring(index + 1);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index)
                    + normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                return (null); // Trying to go outside our context
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2)
                    + normalized.substring(index + 3);
        }

        // Return the normalized path that we have completed
        return (normalized);

    }

}
