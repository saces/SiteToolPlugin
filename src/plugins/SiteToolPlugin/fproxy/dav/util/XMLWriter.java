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

package plugins.SiteToolPlugin.fproxy.dav.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Map;

/**
 * XMLWriter helper class.
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class XMLWriter {

    // -------------------------------------------------------------- Constants

	
	/**
	 * tag type: Opening, Closing, without content
	 *
	 */
	public enum TAG { OPENING, CLOSING, NO_CONTENT }

    // ----------------------------------------------------- Instance Variables

    /**
     * Buffer.
     */
    private Writer _writer;

    /**
     * Namespaces to be declared in the root element
     */
    private Map<String, String> _namespaces;

    /**
     * Is true until the root element is written
     */
    private boolean _isRootElement = true;

    // ----------------------------------------------------------- Constructors

    /**
     * Constructor.
     */
    public XMLWriter(OutputStream os, Map<String, String> namespaces) {
        _namespaces = namespaces;
        try {
			_writer = new OutputStreamWriter(os, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Impossible, JVM doesnt support UTF-8");
		}
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Write property to the XML.
     * 
     * @param name
     *      Property name
     * @param value
     *      Property value
     * @throws IOException 
     */
    public void writeProperty(String name, String value) throws IOException {
        writeElement(name, TAG.OPENING);
        _writer.append(value);
        writeElement(name, TAG.CLOSING);
    }

    /**
     * Write property to the XML.
     * 
     * @param name
     *      Property name
     * @throws IOException 
     */
    public void writeProperty(String name) throws IOException {
        writeElement(name, TAG.NO_CONTENT);
    }

    /**
     * Write an element.
     * 
     * @param name
     *      Element name
     * @param type
     *      Element type
     * @throws IOException 
     */
    public void writeElement(String name, TAG type) throws IOException {
        StringBuffer nsdecl = new StringBuffer();

        if (_isRootElement) {
            for (String fullName: _namespaces.keySet()) {
                String abbrev = (String) _namespaces.get(fullName);
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
            String prefix = (String) _namespaces.get(fullns);
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

        switch (type) {
        case OPENING:
            _writer.append("<" + name + nsdecl + ">");
            break;
        case CLOSING:
            _writer.append("</" + name + ">\n");
            break;
        case NO_CONTENT:
        default:
            _writer.append("<" + name + nsdecl + "/>");
            break;
        }
    }

    /**
     * Write text.
     * 
     * @param text
     *      Text to append
     * @throws IOException 
     */
    public void writeText(String text) throws IOException {
        _writer.append(text);
    }

    /**
     * Write data.
     * 
     * @param data
     *      Data to append
     * @throws IOException 
     */
    public void writeData(String data) throws IOException {
        _writer.append("<![CDATA[" + data + "]]>");
    }

    /**
     * Write XML Header.
     * @throws IOException 
     */
    public void writeXMLHeader() throws IOException {
        _writer.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
    }

	public void close() throws IOException {
		_writer.close();
	}
}
