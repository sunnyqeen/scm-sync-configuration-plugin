package hudson.plugins.scm_sync_configuration.scms;

import org.kohsuke.stapler.StaplerRequest;

public class ScmSyncSubversionSCM extends SCM {

    private static final String SCM_URL_PREFIX="scm:svn:";

    ScmSyncSubversionSCM(){
        super("Subversion", "svn/config.jelly", "hudson.scm.SubversionSCM", "/hudson/plugins/scm_sync_configuration/ScmSyncConfigurationPlugin/scms/svn/url-help.jelly");
    }

    @Override
    public String createScmUrlFromRequest(StaplerRequest req) {
        String repoURL = req.getParameter("repositoryUrl");
        if (repoURL == null) {
            return null;
        } else {
            return SCM_URL_PREFIX + repoURL.trim();
        }
    }

    @Override
    public String extractScmUrlFrom(String scmUrl) {
        return scmUrl.substring(SCM_URL_PREFIX.length());
    }

    @Override
    public SCMCredentialConfiguration extractScmCredentials(String scmUrl) {
        return null;
    }
}
