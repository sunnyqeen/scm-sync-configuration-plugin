package hudson.plugins.scm_sync_configuration;

import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.manager.BasicScmManager;
import org.apache.maven.scm.provider.svn.svnexe.SvnExeScmProvider;
import org.apache.maven.scm.provider.git.gitexe.GitExeScmProvider;

public class SCMManagerFactory {

	private static final SCMManagerFactory INSTANCE = new SCMManagerFactory();

	private SCMManagerFactory(){
	}
	
	public ScmManager createScmManager() {
		BasicScmManager scmManager = new BasicScmManager();
		scmManager.setScmProvider("git", new GitExeScmProvider());
		scmManager.setScmProvider("svn", new SvnExeScmProvider());
		return scmManager;
	}
	
	public static SCMManagerFactory getInstance(){
		return INSTANCE;
	}
}
