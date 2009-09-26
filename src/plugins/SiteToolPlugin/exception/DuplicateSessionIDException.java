package plugins.SiteToolPlugin.exception;

/**
 * @author saces
 *
 */
public class DuplicateSessionIDException extends Exception {

	private static final long serialVersionUID = 1L;

	public DuplicateSessionIDException(String sessionid) {
		super("Duplicate site: '" + sessionid + "'.");
	}
}
