package de.saces.fnplugins.SiteToolPlugin.fproxy.dav.methods;

import java.io.IOException;
import java.net.URI;

import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.IMethodExecutor;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api.WebDAVStatus;
import de.saces.fnplugins.SiteToolPlugin.fproxy.dav.exceptions.LockFailedException;

import freenet.clients.http.RedirectException;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;


public class DoNotImplemented implements IMethodExecutor {

	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(DoNotImplemented.class);
	}

	private boolean _readOnly;

	public DoNotImplemented(boolean readOnly) {
		_readOnly = readOnly;
	}
	public void handle(ITransaction transaction, URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException, LockFailedException {
		if (logDEBUG)
			Logger.debug(this, "-- " + req.getMethod());

		if (_readOnly) {
			ctx.sendReplyHeaders(WebDAVStatus.SC_FORBIDDEN, WebDAVStatus.getStatusText(WebDAVStatus.SC_FORBIDDEN), null, null, -1);
		} else {
			ctx.sendReplyHeaders(WebDAVStatus.SC_NOT_IMPLEMENTED, WebDAVStatus.getStatusText(WebDAVStatus.SC_NOT_IMPLEMENTED), null, null, -1);
		}
	}
}
