/*
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
 *
 */
package plugins.SiteToolPlugin.fproxy.dav.sampleimpl;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.FileBucket;

import plugins.SiteToolPlugin.fproxy.dav.api.IStoredObject;
import plugins.SiteToolPlugin.fproxy.dav.api.ITransaction;
import plugins.SiteToolPlugin.fproxy.dav.api.IWebDAVStore;
import plugins.SiteToolPlugin.fproxy.dav.exceptions.WebDAVException;

/**
 * Reference Implementation of WebdavStore
 * 
 * @author joa
 * @author re
 */
public class LocalFileSystemStore implements IWebDAVStore {
	
	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;
	
	static {
		Logger.registerClass(LocalFileSystemStore.class);
	}

	private static int BUF_SIZE = 65536;

	private File _root = null;

	public LocalFileSystemStore(File root) {
		_root = root;
	}

	public ITransaction begin(Principal principal) throws WebDAVException {
		Logger.debug(this, "LocalFileSystemStore.begin()");
		if (!_root.exists()) {
			if (!_root.mkdirs()) {
				throw new WebDAVException("root path: "
						+ _root.getAbsolutePath()
						+ " does not exist and could not be created");
			}
		}
		return null;
	}

	public void checkAuthentication(ITransaction transaction) throws SecurityException {
		Logger.debug(this, "LocalFileSystemStore.checkAuthentication()");
		// do nothing
	}

	public void commit(ITransaction transaction) throws WebDAVException {
		// do nothing
		Logger.debug(this, "LocalFileSystemStore.commit()");
	}

	public void rollback(ITransaction transaction) throws WebDAVException {
		// do nothing
		Logger.debug(this, "LocalFileSystemStore.rollback()");
	}

	public void createFolder(ITransaction transaction, String uri) throws WebDAVException {
        Logger.debug(this, "LocalFileSystemStore.createFolder(" + uri + ")");
        File file = new File(_root, uri);
        if (!file.mkdir())
            throw new WebDAVException("cannot create folder: " + uri);
    }

	public void createResource(ITransaction transaction, String uri) throws WebDAVException {
		Logger.debug(this, "LocalFileSystemStore.createResource(" + uri + ")");
		File file = new File(_root, uri);
		try {
			if (!file.createNewFile())
				throw new WebDAVException("cannot create file: " + uri);
		} catch (IOException e) {
			Logger.error(this, "LocalFileSystemStore.createResource(" + uri + ") failed");
			throw new WebDAVException(e);
		}
	}

	public long setResourceContent(ITransaction transaction, String uri, Bucket content, String contentType, String characterEncoding) throws WebDAVException {
        Logger.debug(this, "LocalFileSystemStore.setResourceContent(" + uri + ")");
        File file = new File(_root, uri);
        try {
        	InputStream is = content.getInputStream();
            OutputStream os = new BufferedOutputStream(new FileOutputStream(file), BUF_SIZE);
            try {
                int read;
                byte[] copyBuffer = new byte[BUF_SIZE];

                while ((read = is.read(copyBuffer, 0, copyBuffer.length)) != -1) {
                    os.write(copyBuffer, 0, read);
                }
            } finally {
                try {
                    is.close();
                } finally {
                    os.close();
                }
            }
        } catch (IOException e) {
            Logger.error(this, "LocalFileSystemStore.setResourceContent(" + uri
                    + ") failed");
            throw new WebDAVException(e);
        }
        long length = -1;

        try {
            length = file.length();
        } catch (SecurityException e) {
            Logger.error(this, "LocalFileSystemStore.setResourceContent(" + uri
                    + ") failed" + "\nCan't get file.length");
        }

        return length;
    }

    public String[] getChildrenNames(ITransaction transaction, String uri)
            throws WebDAVException {
        Logger.debug(this, "LocalFileSystemStore.getChildrenNames(" + uri + ")");
        File file = new File(_root, uri);
        String[] childrenNames = null;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            List<String> childList = new ArrayList<String>();
            String name = null;
            for (int i = 0; i < children.length; i++) {
                name = children[i].getName();
                childList.add(name);
                Logger.debug(this, "Child " + i + ": " + name);
            }
            childrenNames = new String[childList.size()];
            childrenNames = childList.toArray(childrenNames);
        }
        return childrenNames;
    }

    public void removeObject(ITransaction transaction, String uri) throws WebDAVException {
        File file = new File(_root, uri);
        boolean success = file.delete();
        Logger.debug(this, "LocalFileSystemStore.removeObject(" + uri + ")=" + success);
        if (!success) {
            throw new WebDAVException("cannot delete object: " + uri);
        }
    }

    public Bucket getResourceContent(ITransaction transaction, String uri) throws WebDAVException {
        Logger.debug(this, "LocalFileSystemStore.getResourceContent(" + uri + ")");
        File file = new File(_root, uri);

        FileBucket fb;
        fb = new FileBucket(file, false, false, false, false, false);
        return fb;
    }

    public long getResourceLength(ITransaction transaction, String uri)
            throws WebDAVException {
        Logger.debug(this, "LocalFileSystemStore.getResourceLength(" + uri + ")");
        File file = new File(_root, uri);
        return file.length();
    }

    public IStoredObject getStoredObject(ITransaction transaction, String uri) throws WebDAVException {
        SimpleStoredObject so = null;
        File file = new File(_root, uri);
        Logger.error(this, "Look for file: " + file.exists() + " - " + file.getAbsolutePath(), new Exception("DEBUG"));
        if (file.exists()) {
            so = new SimpleStoredObject();
            so.setFolder(file.isDirectory());
            so.setLastModified(new Date(file.lastModified()));
            so.setCreationDate(new Date(file.lastModified()));
            so.setResourceLength(getResourceLength(transaction, uri));
        }
        return so;
    }

}
