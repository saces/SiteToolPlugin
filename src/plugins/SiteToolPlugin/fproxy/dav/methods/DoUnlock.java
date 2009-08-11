package plugins.SiteToolPlugin.fproxy.dav.methods;

import java.io.IOException;
import java.net.URI;

import plugins.SiteToolPlugin.fproxy.dav.api.ILockedObject;
import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;
import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class DoUnlock extends DeterminableMethod {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoUnlock.class);
	}

    private IWebDAVStore _store;
    private plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks _resourceLocks;
    private boolean _readOnly;

    public DoUnlock(IWebDAVStore store, plugins.SiteToolPlugin.fproxy.dav.api.IResourceLocks resourceLocks, boolean readOnly) {
        _store = store;
        _resourceLocks = resourceLocks;
        _readOnly = readOnly;
    }

	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {
		if (logDEBUG)
			Logger.debug(this, "-- " + this.getClass().getName());

        if (_readOnly) {
            sendError(WebDAVStatus.SC_FORBIDDEN, ctx);
            return;
        } else {

            String path = getRelativePath(req);
            String tempLockOwner = "doUnlock" + System.currentTimeMillis()
                    + req.toString();
            try {
                if (_resourceLocks.lock(transaction, path, tempLockOwner,
                        false, 0, TEMP_TIMEOUT, TEMPORARY)) {

                    String lockId = getLockIdFromLockTokenHeader(req);
                    ILockedObject lo;
                    if (lockId != null
                            && ((lo = _resourceLocks.getLockedObjectByID(
                                    transaction, lockId)) != null)) {

                        String[] owners = lo.getOwner();
                        String owner = null;
                        if (lo.isShared()) {
                            // more than one owner is possible
                            if (owners != null) {
                                for (int i = 0; i < owners.length; i++) {
                                    // remove owner from LockedObject
                                    lo.removeLockedObjectOwner(owners[i]);
                                }
                            }
                        } else {
                            // exclusive, only one lock owner
                            if (owners != null)
                                owner = owners[0];
                            else
                                owner = null;
                        }

                        if (_resourceLocks.unlock(transaction, lockId, owner)) {
                            IStoredObject so = _store.getStoredObject(
                                    transaction, path);
                            if (so.isNullResource()) {
                                _store.removeObject(transaction, path);
                            }

                            sendError(WebDAVStatus.SC_NO_CONTENT, ctx);
                        } else {
                        	if (logDEBUG)
                        		Logger.debug(this, "DoUnlock failure at " + lo.getPath());
                            sendError(WebDAVStatus.SC_METHOD_FAILURE, ctx);
                        }

                    } else {
                        sendError(WebDAVStatus.SC_BAD_REQUEST, ctx);
                    }
                }
            } catch (LockFailedException e) {
            	Logger.error(this, "DUBUG", e);
            } finally {
                _resourceLocks.unlockTemporaryLockedObjects(transaction, path,
                        tempLockOwner);
            }
        }
    }

}
