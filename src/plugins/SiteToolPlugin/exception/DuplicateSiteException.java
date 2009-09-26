package plugins.SiteToolPlugin.exception;

/**
 * @author saces
 *
 */
public class DuplicateSiteException extends Exception {

	private static final long serialVersionUID = 1L;

	public DuplicateSiteException(String sitename) {
		super("Duplicate site: '" + sitename + "'.");
	}

}
