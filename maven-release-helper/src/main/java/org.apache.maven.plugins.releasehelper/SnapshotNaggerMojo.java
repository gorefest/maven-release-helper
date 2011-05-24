package org.apache.maven.plugins.releasehelper;


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * @Author MNT (kontakt@mantucon.net)
 * @Since 1.0.0
 * @version 1.0.0
 *
 * Goal which touches a timestamp file.
 *
 * @goal snapshotNagging
 * @aggregator
 * @requiresProject true
 * @requiresDirectInvocation true
 */
public class SnapshotNaggerMojo
    extends AbstractMojo {

    /**
     * custom f*ckup exception for a better reading
     */
    public class SnapshotInDependenciesException extends MojoExecutionException {
        public SnapshotInDependenciesException(String s) {
            super(s);
        }
    }

    enum Area { PluginsDependencies, Dependencies} // used to differ the checks

    public final Pattern matchSnapshotRegex // how to match a snapshot
            = Pattern.compile( "^(.+)-((SNAPSHOT)|(\\d{8}\\.\\d{6}-\\d+))$" );

    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @since 1.0-alpha-1
     *
     */
    protected MavenProject project;

    /**
     * excecute the test. this test relies on the injection of the project variable
     *
     * @throws MojoExecutionException
     */
    public void execute()
        throws MojoExecutionException
    {

        Log log = this.getLog(); // maven logger implementation

        if (project == null) {
            log.info("Project variable was null. skipping...");  // no project, no test
        } else {
            List<Dependency> badDependencies = new ArrayList<Dependency>(); // bad snapshot dependencies
            log.info("SnapshotNaggerMojo firing.");
            log.debug("fetchting dependencies.");

            List<Dependency> deps = project.getDependencies();
            log.debug("The project has got "+deps.size()+" dependency entries.");

            log.debug("testing dependencies of "+ Area.Dependencies);
            probeDependencies(Area.Dependencies, log, badDependencies, deps);

            List<Plugin> plugins =  project.getBuildPlugins();
            log.debug("the project contains "+plugins.size()+" plugin references.");
            for(Plugin plugin : plugins) {
                List<Dependency> pluginDeps = plugin.getDependencies();
                log.debug("the plugin "+plugin.getArtifactId()+" has got "+pluginDeps.size()+" dependencies.");
                probeDependencies(Area.PluginsDependencies, log,badDependencies,pluginDeps);
            }

            if (!badDependencies.isEmpty()) {
                throw new SnapshotInDependenciesException("Snapshot dependencies have been found :"+toString
                        (badDependencies));
            }
        }
    }

    /**
     *
     * @param area - where am i checking dependencies?
     * @param log - the logfile
     * @param badDependencies  - the result collection containing bad dependencies
     * @param deps - the input dependencies
     */
    private void probeDependencies(Area area, Log log, List<Dependency> badDependencies, List<Dependency> deps) {
        log.debug("I am going to check "+deps.size()+" dependency entries");
        int maleficium = 0;
        for (Dependency dep  : deps) {
            if (isSnapshot(dep)) {
                log.error("["+area+"] found snapshot dependency "+dep.toString());
                badDependencies.add(dep);
                maleficium++;
            } else {
                log.debug("["+area+"] not a snapshot "+dep.getArtifactId().toString());
            }
        }
        if (maleficium > 0) {
            log.info("I have found "+maleficium+" dependencies failing the SNAPSHOT test while scanning "+area);
        }
    }

    /**
     * make a comma seperated string out of the list of failing dependencies
     *
     * @param items - the bad dependencies
     * @return a string
     */
    public String toString(List<Dependency> items) {
        StringBuilder sb = new StringBuilder();

        for (Dependency dep : items) {
            sb.append(dep.getGroupId())
                    .append(":")
                    .append(dep.getArtifactId())
                    .append(":")
                    .append(dep.getVersion())
                    .append(",");
        }
        String result = sb.toString();
        return result.substring(0,result.length()-2);
    }

    /**
     * @param dep - the dependency
     * @return true, if the dependency is considered to be a snapshot
     */
    public boolean isSnapshot(Dependency dep) {
        return matchSnapshotRegex.matcher(dep.getVersion()).find();
    }
}
