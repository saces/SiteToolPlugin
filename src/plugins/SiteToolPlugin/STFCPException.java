/**
 * 
 */
package plugins.SiteToolPlugin;

import freenet.support.plugins.helpers1.AbstractFCPHandler.FCPException;

/**
 * @author saces
 *
 */
public class STFCPException extends FCPException {

	private static final long serialVersionUID = 1L;

	public static final int MISSING_SESSION_IDENTIFIER = 100;
	public static final int INVALID_URI = 101;
	public static final int DUPLICATE_SESSION = 102;
	public static final int NO_SUCH_SESSION = 101;
//	public static final int DUPLICATE_SESSION_RUNNING = 101;
//	public static final int INVALID_OLDURI = 6;
//	public static final int MISSING_OLDURI = 7;
//	public static final int INVALID_SESSION = 8;
//	public static final int SESSION_CANTSTOP = 9;
//	public static final int MISSING_INSERTURI = 10;
//	public static final int INVALID_INSERTURI = 11;
//	public static final int INTERNAL = 12;

	/**
	 * 
	 */
	public STFCPException(int code, String message) {
		super(code, message);
	}
}
