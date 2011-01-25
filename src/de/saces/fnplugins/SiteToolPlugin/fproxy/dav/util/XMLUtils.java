/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package de.saces.fnplugins.SiteToolPlugin.fproxy.dav.util;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

/**
 * @author saces
 *
 */
public class XMLUtils {
	
	public final static void writeDeep(Writer w, int deep) throws IOException {
		if (deep == 0) return;
		for (int i=0;i<deep;i++) {
			w.write("\t");
		}
	}
	
	public final static void writeLine(Writer w, int deep, String line) throws IOException {
		if (deep >= 0) writeDeep(w, deep);
		w.write(line);
		w.write('\n');
	}
	
	public final static void writeSimpleElementLine(Writer w, int deep, String element, String content) throws IOException {
		if (deep >= 0) writeDeep(w, deep);
		w.write('<');
		w.write(element);
		w.write('>');
		w.write(content);
		w.write('<');
		w.write('/');
		w.write(element);
		w.write('>');
		w.write('\n');
	}
	
	public final static void writeSimpleElementCDATALine(Writer w, int deep, String element, String content) throws IOException {
		writeSimpleElementCDATALine(w, deep, element, new StringReader(content));
	}
	
	public final static void writeSimpleElementCDATALine(Writer w, int deep, String element, Reader content) throws IOException {
		if (deep >= 0) writeDeep(w, deep);
		w.write('<');
		w.write(element);
		w.write('>');
		writeEscCData(w, content);
		w.write('<');
		w.write('/');
		w.write(element);
		w.write('>');
		w.write('\n');
	}
	
	public final static void writeSimpleElementLine(Writer w, int deep, String element, boolean content) throws IOException {
		writeSimpleElementLine(w, deep, element, ((content)?"true":"false"));
	}
	
	public final static void writePreamble(Writer w) throws IOException {
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
	}
	
	public final static void writeEsc(Writer w, String s) throws IOException {
		writeEsc(w, s, false);
	}
	
	private static void writeEsc(Writer w, String s, boolean isAttVal) throws IOException {
		for (int i = 0; i < s.length(); i++) {
			switch (s.charAt(i)) {
			case '&':
				w.write("&amp;");
				break;
			case '<':
				w.write("&lt;");
				break;
			case '>':
				w.write("&gt;");
				break;
			case '\"':
				if (isAttVal) {
					w.write("&quot;");
				} else {
					w.write('\"');
				}
				break;
			default:
				w.write(s.charAt(i));
			}
		}
	}

	private static void writeEscCData(Writer w, Reader input) throws IOException {
		int trigger = 0;
		w.write("<![CDATA[");
		int c;
		while ((c = input.read()) != -1) {
			if (c == ']') {
				w.write(']');
				trigger++;
			} else if (( c == '>') && (trigger >= 2)) {
				w.write("]]><![CDATA[>");
				trigger = 0;
			} else {
				w.write(c);
				trigger = 0;
			}
		}
		w.write("]]>");
	}
}
