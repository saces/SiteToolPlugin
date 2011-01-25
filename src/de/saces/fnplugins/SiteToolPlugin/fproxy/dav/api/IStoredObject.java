package de.saces.fnplugins.SiteToolPlugin.fproxy.dav.api;

import java.util.Date;

public interface IStoredObject {

	public boolean isNullResource();

	public boolean isFolder();

	public Date getCreationDate();

	public Date getLastModified();

	public long getResourceLength();

	public boolean isResource();

	public void setNullResource(boolean b);

	public void setFolder(boolean b);

	public void setResourceLength(long resourceLength);

}
