package plugins.SiteToolPlugin.fproxy.dav.util;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Map;

/**
 * XMLWriter knows how to write elements and attributes, and how to use
 * indenting.
 */
public class XMLWriter {

	/**
	 * tag type: Opening, Closing, without content
	 *
	 */
	public enum TAG { OPENING, CLOSING, NO_CONTENT };

	/** use a two space indent */
	public static final String INDENT = "  ";

	// write states

	/** writing content */
	protected final static int CONTENT = 0;

	/** wrote start */
	protected final static int START = 1;

	/** writing attributes */
	protected final static int ATTRIBUTE = 2;

	/** wrote end */
	protected final static int END = 3;

	/** Write to XML document */
	protected Writer mOut;

	/** indent */
	protected Indent indent;

	/** writes PCDATA replacing XML characters with escape entities */
	protected PCDATAFilterWriter mFilter;

	/** current state */
	protected int state = CONTENT;

	/** Namespaces to be declared in the root element */
	private Map<String, String> _namespaces;

	/** Is true until the root element is written */
	private boolean _isRootElement = true;

	/**
	 * @param namespaces
	 * 
	 * 
	 */
	public XMLWriter(OutputStream out, Map<String, String> namespaces) {
		try {
			mOut = new OutputStreamWriter(out, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Impossible, JVM doesnt support UTF-8");
		}
		mFilter = new PCDATAFilterWriter(mOut);
		indent = new Indent();
		_namespaces = namespaces;
	}

	/**
	 * Write &lt;?xml version="1.0"?&gt;
	 * @throws IOException 
	 */
	public void writeXMLHeader() throws IOException {
		mOut.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
	}

	/**
	 * Write a DOCTYPE
	 * 
	 * @param name
	 *            document type name
	 * @param publicId
	 *            public Id. May be Null.
	 * @param systemId
	 *            system Id.
	 * @throws IOException 
	 */
	public void writeDocumentType(String name, String publicId, String systemId) throws IOException {
		mOut.write("<!DOCTYPE ");
		mOut.write(name);
		mOut.write(' ');

		if (publicId != null) {
			mOut.write('"');
			mOut.write(publicId);
			mOut.write("\" ");
		}

		mOut.write("SYSTEM \"");
		mOut.write(systemId);
		mOut.write("\">\n");
	}

	/**
     *
     */
	public void attribute(String name, int value) throws IOException {
		attribute(name, String.valueOf(value));
	}

	/**
     *
     */
	public void attribute(String name, boolean value) throws IOException {
		attribute(name, String.valueOf(value));
	}

	/**
     *
     */
	public void attribute(String name, Object value) throws IOException {
		if (value == null) {
			return;
		}

		attribute(name, value.toString());
	}

	/**
     *
     */
	public void attribute(String name, String value) throws IOException {
		if (value == null) {
			return;
		}

		state = ATTRIBUTE;
		mOut.write(' ');
		mOut.write(name);
		mOut.write('=');
		mOut.write('"');
		mFilter.write(value);
		mOut.write('"');
	}

	/**
     *
     */
	public void content(String content) throws IOException {
		// close the last tag
		if ((state == START) || (state == ATTRIBUTE)) {
			mOut.write('>');
		}

		mFilter.write(content);
		state = CONTENT;
	}

	/**
	 * Write start tag.
	 * @throws IOException 
	 */
	public void start(String name) throws IOException {
		StringBuffer nsdecl = new StringBuffer();

		if (_isRootElement) {
			for (String fullName : _namespaces.keySet()) {
				String abbrev = _namespaces.get(fullName);
				nsdecl.append(" xmlns:");
				nsdecl.append(abbrev);
				nsdecl.append("=\"");
				nsdecl.append(fullName);
				nsdecl.append("\"");
			}
			_isRootElement = false;
		}

		int pos = name.lastIndexOf(':');
		if (pos >= 0) {
			// lookup prefix for namespace
			String fullns = name.substring(0, pos);
			String prefix = _namespaces.get(fullns);
			if (prefix == null) {
				// there is no prefix for this namespace
				name = name.substring(pos + 1);
				nsdecl.append(" xmlns=\"").append(fullns).append("\"");
			} else {
				// there is a prefix
				name = prefix + ":" + name.substring(pos + 1);
			}
		} else {
			throw new IllegalArgumentException("All XML elements must have a namespace");
		}

		// close the last tag
		if ((state == START) || (state == ATTRIBUTE)) {
			mOut.write(">\n");
		}

		mOut.write(indent.toString());
		mOut.write('<');
		mOut.write(name + nsdecl);
		indent.indent();
		state = START;
	}

	/**
	 * Write end tag.
	 * @throws IOException 
	 * 
	 */
	public void end(String name) throws IOException {
		indent.unIndent();

		StringBuffer nsdecl = new StringBuffer();

		int pos = name.lastIndexOf(':');
		if (pos >= 0) {
			// lookup prefix for namespace
			String fullns = name.substring(0, pos);
			String prefix = _namespaces.get(fullns);
			if (prefix == null) {
				// there is no prefix for this namespace
				name = name.substring(pos + 1);
				nsdecl.append(" xmlns=\"").append(fullns).append("\"");
			} else {
				// there is a prefix
				name = prefix + ":" + name.substring(pos + 1);
			}
		} else {
			throw new IllegalArgumentException("All XML elements must have a namespace");
		}


		if ((state == START) || (state == ATTRIBUTE)) {
			mOut.write("/>\n");
		} else if (state == CONTENT) {
			mOut.write("</");
			mOut.write(name + nsdecl);
			mOut.write("'>\n");
		} else {
			mOut.write(indent.toString());
			mOut.write("</");
			mOut.write(name + nsdecl);
			mOut.write(">\n");
		}

		state = END;
	}

	/**
	 * Implements indenting for elements.
	 * 
	 * @author Brian King
	 */
	protected class Indent {
		/** Base indent */
		protected String mIndent = new String();

		/**
		 * Add a level of indentation
		 */
		public void indent() {
			mIndent += INDENT;
		}

		/**
		 * Remove a level of indentation
		 * 
		 */
		public void unIndent() {
			int len = mIndent.length();
			int iLen = INDENT.length();

			if (len >= iLen) {
				mIndent = mIndent.substring(0, len - iLen);
			}
		}

		/**
		 * Return the current indent
		 * 
		 */
		@Override
		public String toString() {
			return mIndent;
		}
	}

	public void writeElement(String name, TAG type) throws IOException {
		switch (type) {
		case OPENING:
			start(name);
			break;
		case CLOSING:
			end(name);
			break;
		case NO_CONTENT:
			start(name);
			end(name);
		}
	}

	public void writeProperty(String name, String value) throws IOException {
		start(name);
		content(value);
		end(name);
	}

	public void writeData(String data) throws IOException {
		// close the last tag
		if ((state == START) || (state == ATTRIBUTE)) {
			mOut.write('>');
		}

		mOut.write("<![CDATA[" + data + "]]>");
		state = CONTENT;
	}
	
    public void writeProperty(String name) throws IOException {
        writeElement(name, TAG.NO_CONTENT);
    }
    
    public void close() throws IOException {
		mOut.close();
	}


	public void writeText(String text) throws IOException {
		content(text);
	}

	/**
	 * Write XML PCDATA using entity substitutions.
	 * 
	 */
	protected class PCDATAFilterWriter extends FilterWriter {
		/** entity substitution for < */
		protected char[] LT = { '&', 'l', 't', ';' };

		/** entity substitution for > */
		protected char[] GT = { '&', 'g', 't', ';' };

		/** entity substitution for & */
		protected char[] AMP = { '&', 'a', 'm', 'p', ';' };

		/**
		 * Constructor.
		 */
		public PCDATAFilterWriter(Writer out) {
			super(out);
		}

		/**
		 * Write a single character.
		 * 
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		@Override
		public void write(int c) throws IOException {
			synchronized (lock) {
				if (c == '<') {
					out.write(LT);
				} else if (c == '>') {
					out.write(GT);
				} else if (c == '&') {
					out.write(AMP);
				} else {
					out.write(c);
				}
			}
		}

		/**
		 * Write a portion of an array of characters.
		 * 
		 * @param cbuf
		 *            Buffer of characters to be written
		 * @param off
		 *            Offset from which to start reading characters
		 * @param len
		 *            Number of characters to be written
		 * 
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			synchronized (lock) {
				for (int i = 0; i < len; i++) {
					write(cbuf[off + i]);
				}
			}
		}

		/**
		 * Write a portion of a string.
		 * 
		 * @param str
		 *            String to be written
		 * @param off
		 *            Offset from which to start reading characters
		 * @param len
		 *            Number of characters to be written
		 * 
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		@Override
		public void write(String str, int off, int len) throws IOException {
			if (str == null) {
				return;
			}
			synchronized (lock) {
				for (int i = 0; i < len; i++) {
					write(str.charAt(off + i));
				}
			}
		}

		/**
		 * Write a string.
		 * 
		 * @param str
		 *            String to be written
		 * 
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		@Override
		public void write(String str) throws IOException {
			if (str == null) {
				return;
			}
			synchronized (lock) {
				int len = str.length();

				for (int i = 0; i < len; i++) {
					int c = str.charAt(i);

					if (c == '<') {
						out.write(LT);
					} else if (c == '>') {
						out.write(GT);
					} else if (c == '&') {
						out.write(AMP);
					} else {
						out.write(c);
					}
				}
			}
		}
	}
}
