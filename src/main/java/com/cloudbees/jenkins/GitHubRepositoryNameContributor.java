package com.cloudbees.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import hudson.model.EnvironmentContributor;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.model.Jenkins;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.multiplescms.MultiSCM;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extension point that associates {@link GitHubRepositoryName}s to a project.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.7
 */
public abstract class GitHubRepositoryNameContributor implements ExtensionPoint {
    /**
     * Looks at the definition of {@link AbstractProject} and list up the related github repositories,
     * then puts them into the collection.
     */
    public abstract void parseAssociatedNames(AbstractProject<?,?> job, Collection<GitHubRepositoryName> result);

    public static ExtensionList<GitHubRepositoryNameContributor> all() {
        return Jenkins.getInstance().getExtensionList(GitHubRepositoryNameContributor.class);
    }

    public static Collection<GitHubRepositoryName> parseAssociatedNames(AbstractProject<?,?> job) {
        Set<GitHubRepositoryName> names = new HashSet<GitHubRepositoryName>();
        for (GitHubRepositoryNameContributor c : all())
            c.parseAssociatedNames(job,names);
        return names;
    }


    /**
     * Default implementation that looks at SCM
     */
    @Extension
    public static class FromSCM extends GitHubRepositoryNameContributor {
        @Override
        public void parseAssociatedNames(AbstractProject<?, ?> job, Collection<GitHubRepositoryName> result) {

            EnvVars env = new EnvVars();
            for (EnvironmentContributor contributor : EnvironmentContributor.all()) {
                try {
                    contributor.buildEnvironmentFor(job, env, TaskListener.NULL);
                } catch (Exception e) {
                    // ignore
                }
            }

            if (Jenkins.getInstance().getPlugin("multiple-scms") != null
                    && job.getScm() instanceof MultiSCM) {
                MultiSCM multiSCM = (MultiSCM) job.getScm();
                List<SCM> scmList = multiSCM.getConfiguredSCMs();
                for (SCM scm : scmList) {
                    addRepositories(scm, env, result);
                }
            } else {
                addRepositories(job.getScm(), env, result);
            }
        }

        private void addRepositories(SCM scm, EnvVars env, Collection<GitHubRepositoryName> r) {
            if (scm instanceof GitSCM) {
                GitSCM git = (GitSCM) scm;
                for (RemoteConfig rc : git.getRepositories()) {
                    for (URIish uri : rc.getURIs()) {
                        String url = env.expand(uri.toString());
                        GitHubRepositoryName repo = GitHubRepositoryName.create(url);
                        if (repo != null) {
                            r.add(repo);
                        }
                    }
                }
            }
        }

    }
}
