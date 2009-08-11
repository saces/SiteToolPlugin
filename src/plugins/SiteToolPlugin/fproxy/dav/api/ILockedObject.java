package plugins.SiteToolPlugin.fproxy.dav.api;

public interface ILockedObject {

	boolean isShared();

	String[] getOwner();

	/**
	 * deletes this Lock object. assumes that it has no children and no owners
	 * (does not check this itself)
	 * 
	 */
	public void removeLockedObject();

	void removeLockedObjectOwner(String string);

	int getLockDepth();

	long getTimeoutMillis();

	String getID();

	boolean isExclusive();
	
	   /**
     * Sets a new timeout for the LockedObject
     * 
     * @param timeout
     */
    public void setExpires(long expires);

	void refreshTimeout(int timeout);

	String getPath();

	boolean hasExpired();

	String getType();

	void removeTempLockedObject();

	ILockedObject[] getChildren();

}
