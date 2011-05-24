package org.apache.maven.plugins.releasehelper;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class SnapshotNaggerMojoTest {

    MavenProject project;

    @Before
    public void setup() {
        project = new MavenProject();
        ArrayList<Dependency> dependencies = new ArrayList<Dependency>(2);
        Dependency dependency = new Dependency();
        dependency.setVersion("1.0.0");
        dependency.setGroupId("org.foo");
        dependency.setArtifactId("garbel");
        dependencies.add(dependency);

        dependency = new Dependency();
        dependency.setVersion("1.0.0");
        dependency.setGroupId("org.foobar");
        dependency.setArtifactId("garbga");
        dependencies.add(dependency);

        project.setDependencies(dependencies);


        Plugin p = new Plugin();
        dependency = new Dependency();
        dependency.setVersion("1.0.0-SNAPSHOT");
        dependency.setGroupId("org.foobar");
        dependency.setArtifactId("plugindep");
        dependencies.add(dependency);

        p.setDependencies(Arrays.asList(dependency));




    }


    @Test
    public void testToString() {
        SnapshotNaggerMojo candidate = new SnapshotNaggerMojo();
        Dependency dd = new Dependency();
        dd.setArtifactId("foo");
        dd.setGroupId("com.bar");
        dd.setVersion("1.0.0-RELEASE");

        Assert.assertFalse(candidate.isSnapshot(dd));
        dd.setVersion("1.0.1-SNAPSHOT");
        Assert.assertTrue(candidate.isSnapshot(dd));
    }


    @Test
    public void testProject() throws Exception {
        SnapshotNaggerMojo candidate = new SnapshotNaggerMojo();
        candidate.project = project;
        try {
            candidate.execute();
        } catch(SnapshotNaggerMojo.SnapshotInDependenciesException e) {
            // all right
        }
    }
}
