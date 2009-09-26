package plugins.SiteToolPlugin.toadlets.siteexport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;

import freenet.support.api.Bucket;
import freenet.support.api.BucketFactory;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;

public class SiteCollector implements ISiteParserCallback {

	OutputStream _os;
	Collector _collector;
	StringBuilder _sb;
	BucketFactory _bf;

	private interface Collector {
		void addItem(String name, Bucket data) throws IOException;
		void finish();
	}

	private class ZipCollector implements Collector {

		ZipOutputStream __zos;

		ZipCollector() {
			__zos = new ZipOutputStream(_os);
		}

		public void finish() {
			Closer.close(__zos);
		}

		public void addItem(String name, Bucket data) throws IOException {
			ZipEntry ze = new ZipEntry(name);
			ze.setTime(0);
			__zos.putNextEntry(ze);
			BucketTools.copyTo(data, __zos, data.size());
			__zos.closeEntry();
		}

	}

	private class TarCollector implements Collector {

		TarOutputStream __tos;

		TarCollector() {
			__tos = new TarOutputStream(_os);
		}

		public void finish() {
			Closer.close(__tos);
		}

		public void addItem(String name, Bucket data) throws IOException {
			TarEntry te = new TarEntry(name);
			te.setModTime(0);
			long size = data.size();
			te.setSize(size);
			__tos.putNextEntry(te);
			BucketTools.copyTo(data, __tos, size);
			__tos.closeEntry();
		}

	}

	public SiteCollector(OutputStream os, boolean zip, BucketFactory bf) {
		_os = os;
		if (zip) {
			_collector = new ZipCollector();
		} else {
			_collector = new TarCollector();
		}
		_sb = new StringBuilder();
		_bf = bf;
	}

	public void addItem(String name, Bucket data) throws IOException {
		_collector.addItem("content" + name, data);
	}

	public void addReport(String name, String report) {
		_sb.append(name);
		_sb.append(" : ");
		_sb.append(report);
		_sb.append('\n');
	}

	public void finish() throws IOException {
		if (_sb.length() > 0) {
			Bucket b = BucketTools.makeImmutableBucket(_bf, _sb.toString().getBytes());
			_collector.addItem("report.txt", b);
		}
		_collector.finish();
	}

}
