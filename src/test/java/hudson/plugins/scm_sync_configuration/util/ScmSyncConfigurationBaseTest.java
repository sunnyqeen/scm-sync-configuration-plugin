package hudson.plugins.scm_sync_configuration.util;

import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.plugins.scm_sync_configuration.SCMManagerFactory;
import hudson.plugins.scm_sync_configuration.SCMManipulator;
import hudson.plugins.scm_sync_configuration.ScmSyncConfigurationBusiness;
import hudson.plugins.scm_sync_configuration.ScmSyncConfigurationPlugin;
import hudson.plugins.scm_sync_configuration.model.ScmContext;
import hudson.plugins.scm_sync_configuration.scms.SCM;
import hudson.plugins.scm_sync_configuration.scms.SCMCredentialConfiguration;
import hudson.plugins.scm_sync_configuration.scms.ScmSyncSubversionSCM;
import hudson.plugins.scm_sync_configuration.xstream.migration.DefaultSSCPOJO;
import hudson.plugins.scm_sync_configuration.xstream.migration.ScmSyncConfigurationPOJO;
import hudson.plugins.test.utils.DirectoryUtils;
import hudson.plugins.test.utils.scms.ScmUnderTest;

import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.core.io.ClassPathResource;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "org.tmatesoft.svn.*" })
@PrepareForTest({Jenkins.class, SCM.class, ScmSyncSubversionSCM.class, PluginWrapper.class})
public abstract class ScmSyncConfigurationBaseTest {

    @Rule protected TestName testName = new TestName();

    private static final String TEST_URL = "https://jenkins.example.org/";

    private File currentTestDirectory = null;
    private File curentLocalRepository = null;
    private File currentHudsonRootDirectory = null;
    protected ScmSyncConfigurationBusiness sscBusiness = null;
    protected ScmContext scmContext = null;

    private final ScmUnderTest scmUnderTest;

    protected ScmSyncConfigurationBaseTest(ScmUnderTest scmUnderTest) {
        this.scmUnderTest = scmUnderTest;
        this.scmContext = null;
    }

    @Mock
    private PluginWrapper pluginWrapper;

    @Mock
    private Jenkins hudsonMockedInstance;

    @Mock
    private User mockedUser;

    @Before
    public void setup() throws Throwable {
        // Instantiating ScmSyncConfigurationPlugin instance for unit tests by using
        // synchronous transactions (instead of an asynchronous ones)
        // => this way, every commit will be processed synchronously !
        ScmSyncConfigurationPlugin scmSyncConfigPluginInstance = new ScmSyncConfigurationPlugin(true) {
            @Override
            public void initialInit() throws Exception {
                // No-op. We *must not* initialize here in tests because the tests provide their own setup.
            }
        };

        // Mocking PluginWrapper attached to current ScmSyncConfigurationPlugin instance
        when(pluginWrapper.getShortName()).thenReturn("scm-sync-configuration");
        // Setting field on current plugin instance
        Field wrapperField = Plugin.class.getDeclaredField("wrapper");
        boolean wrapperFieldAccessibility = wrapperField.isAccessible();
        wrapperField.setAccessible(true);
        wrapperField.set(scmSyncConfigPluginInstance, pluginWrapper);
        wrapperField.setAccessible(wrapperFieldAccessibility);

        Field businessField = ScmSyncConfigurationPlugin.class.getDeclaredField("business");
        businessField.setAccessible(true);
        sscBusiness = (ScmSyncConfigurationBusiness) businessField.get(scmSyncConfigPluginInstance);

        // Mocking Hudson root directory
        currentTestDirectory = createTmpDirectory("SCMSyncConfigTestsRoot");
        currentHudsonRootDirectory = new File(currentTestDirectory.getAbsolutePath()+"/hudsonRootDir/");
        if(!(currentHudsonRootDirectory.mkdir())) { throw new IOException("Could not create hudson root directory: " + currentHudsonRootDirectory.getAbsolutePath()); }
        FileUtils.copyDirectoryStructure(new ClassPathResource(getHudsonRootBaseTemplate()).getFile(), currentHudsonRootDirectory);

        //EnvVars env = Computer.currentComputer().getEnvironment();
        //env.put("HUDSON_HOME", tmpHudsonRoot.getPath() );

        // Creating local repository...
        curentLocalRepository = new File(currentTestDirectory.getAbsolutePath()+"/localRepo/");
        if(!(curentLocalRepository.mkdir())) { throw new IOException("Could not create local repo directory: " + curentLocalRepository.getAbsolutePath()); }
        scmUnderTest.initRepo(curentLocalRepository);

        // Mocking user
        when(mockedUser.getId()).thenReturn("fcamblor");

        // Mocking Hudson singleton instance ...
        PowerMockito.doReturn(currentHudsonRootDirectory).when(hudsonMockedInstance).getRootDir();
        PowerMockito.doReturn(mockedUser).when(hudsonMockedInstance).getMe();
        PowerMockito.doReturn(scmSyncConfigPluginInstance).when(hudsonMockedInstance).getPlugin(ScmSyncConfigurationPlugin.class);
        PowerMockito.doReturn(TEST_URL).when(hudsonMockedInstance).getRootUrl();
        PowerMockito.doReturn(TEST_URL).when(hudsonMockedInstance).getRootUrlFromRequest();

        PowerMockito.mockStatic(Jenkins.class);
        //PowerMockito.doReturn(hudsonMockedInstance).when(Jenkins.class); Jenkins.get();
        when(Jenkins.get()).thenReturn(hudsonMockedInstance);
    }

    @After
    public void teardown() throws Throwable {
        // Deleting current test directory
        FileUtils.deleteDirectory(currentTestDirectory);
    }

    // Overridable
    protected String getHudsonRootBaseTemplate(){
        return "hudsonRootBaseTemplate/";
    }

    protected static File createTmpDirectory(String directoryPrefix) throws IOException {
        final File temp = File.createTempFile(directoryPrefix, Long.toString(System.nanoTime()));
        if(!(temp.delete())) { throw new IOException("Could not delete temp file: " + temp.getAbsolutePath()); }
        if(!(temp.mkdir())) { throw new IOException("Could not create temp directory: " + temp.getAbsolutePath()); }
        return (temp);
    }

    protected SCM createSCMMock(){
        return createSCMMock(getSCMRepositoryURL());
    }

    protected SCM createSCMMock(String url){
        SCM mockedSCM = spy(SCM.valueOf(getSCMClass().getName()));

        if(scmUnderTest.useCredentials()){
            SCMCredentialConfiguration mockedCredential = new SCMCredentialConfiguration("toto");
            PowerMockito.doReturn(mockedCredential).when(mockedSCM).extractScmCredentials((String)Mockito.notNull());
        }

        scmContext = new ScmContext(mockedSCM, url);
        ScmSyncConfigurationPOJO config = new DefaultSSCPOJO();
        config.setScm(scmContext.getScm());
        config.setScmRepositoryUrl(scmContext.getScmRepositoryUrl());
        ScmSyncConfigurationPlugin.getInstance().loadData(config);
        ScmSyncConfigurationPlugin.getInstance().init();

        return mockedSCM;
    }

    protected SCMManipulator createMockedScmManipulator() {
        // Settling up scm context
        SCMManipulator scmManipulator = new SCMManipulator(SCMManagerFactory.getInstance().createScmManager());
        boolean configSettledUp = scmManipulator.scmConfigurationSettledUp(scmContext, true);
        assertThat(configSettledUp, is(true));

        return scmManipulator;
    }

    protected void verifyCurrentScmContentMatchesCurrentHudsonDir(boolean match) throws IOException{
        verifyCurrentScmContentMatchesHierarchy(getCurrentHudsonRootDirectory(), match);
    }

    protected void verifyCurrentScmContentMatchesHierarchy(String hierarchyPath, boolean match) throws IOException{
        verifyCurrentScmContentMatchesHierarchy(new ClassPathResource(hierarchyPath).getFile(), match);
    }

    protected void verifyCurrentScmContentMatchesHierarchy(File hierarchy, boolean match) throws IOException{
        SCMManipulator scmManipulator = createMockedScmManipulator();

        // Checkouting scm in temp directory
        File checkoutDirectoryForVerifications = createTmpDirectory(this.getClass().getSimpleName()+"_"+testName.getMethodName()+"__verifyCurrentScmContentMatchesHierarchy");
        scmManipulator.checkout(checkoutDirectoryForVerifications);
        List<String> diffs = DirectoryUtils.diffDirectories(checkoutDirectoryForVerifications, hierarchy,
                getSpecialSCMDirectoryExcludePattern(), true);

        FileUtils.deleteDirectory(checkoutDirectoryForVerifications);

        if(match){
            assertTrue("Directories doesn't match : "+diffs, diffs.isEmpty());
        } else {
            assertFalse("Directories should _not_ match !", diffs.isEmpty());
        }
    }

    protected void verifyCurrentScmContentMatchesHierarchy(String hierarchyPath) throws IOException{
        verifyCurrentScmContentMatchesHierarchy(hierarchyPath, true);
    }

    // Overridable in a near future (when dealing with multiple scms ...)
    protected String getSCMRepositoryURL(){
        return scmUnderTest.createUrl(this.getCurentLocalRepository().getAbsolutePath());
    }

    protected static List<Pattern> getSpecialSCMDirectoryExcludePattern(){
        return Lists.newArrayList(
                Pattern.compile("\\.svn"),
                Pattern.compile("\\.git.*"),
                Pattern.compile("scm-sync-configuration\\..*\\.log"),
                Pattern.compile("scm-sync-configuration")
                );
    }

    protected String getSuffixForTestFiles() {
        return scmUnderTest.getSuffixForTestFiles();
    }

    // Overridable in a near future (when dealing with multiple scms ...)
    protected Class<? extends SCM> getSCMClass(){
        return scmUnderTest.getClazz();
    }

    protected File getCurrentTestDirectory() {
        return currentTestDirectory;
    }

    protected File getCurentLocalRepository() {
        return curentLocalRepository;
    }

    public File getCurrentHudsonRootDirectory() {
        return currentHudsonRootDirectory;
    }

    public File getCurrentScmSyncConfigurationCheckoutDirectory(){
        return new File(currentHudsonRootDirectory.getAbsolutePath()+"/scm-sync-configuration/checkoutConfiguration/");
    }

}
