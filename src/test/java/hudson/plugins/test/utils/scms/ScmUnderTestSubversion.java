package hudson.plugins.test.utils.scms;

import hudson.plugins.scm_sync_configuration.scms.SCM;
import hudson.plugins.scm_sync_configuration.scms.ScmSyncSubversionSCM;

import java.io.File;

public class ScmUnderTestSubversion implements ScmUnderTest {

	public void initRepo(File path) throws Exception {
		ProcessBuilder pb = new ProcessBuilder("svnadmin", "create", ".");
		pb.directory(path);
		Process p = pb.start();
		if (p.waitFor() != 0) {
			throw new Exception("Unable to init svn repo in " + path.getAbsolutePath());
		}
	}

	public String createUrl(String url) {
		return "scm:svn:file:///" + url.replace('\\', '/');
	}

	public Class<? extends SCM> getClazz() {
		return ScmSyncSubversionSCM.class;
	}

	public boolean useCredentials() {
		return true;
	}

	public String getSuffixForTestFiles() {
		return ".subversion";
	}

}
