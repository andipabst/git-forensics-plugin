package io.jenkins.plugins.git.forensics.blame;

import java.io.IOException;
import java.util.Collections;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.git.forensics.util.GitITest;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
public class GitBlamerITest extends GitITest {
    /** Jenkins rule per suite. */
    @ClassRule
    public static final JenkinsRule JENKINS_PER_SUITE = new JenkinsRule();
    private static final String WORKSPACE = "workspace";

    /**
     * Verifies that the blames are empty if there are no requests defined.
     */
    @Test
    public void shouldCreateEmptyBlamesIfRequestIsEmpty() {
        GitBlamer gitBlamer = createBlamer();

        Blames blames = gitBlamer.blame(new FileLocations(WORKSPACE));

        assertThat(blames.isEmpty()).isTrue();
    }

    /**
     * Verifies that the blames are empty if there are no requests defined.
     */
    @Test
    public void shouldCreateBlamesIfRequestIsExistingFile() {
        create2RevisionsWithDifferentAuthors();

        FileLocations locations = new FileLocations(sampleRepo.getRoot());
        String absolutePath = locations.getWorkspace() + FILE_NAME;
        locations.addLine(absolutePath, 2);
        locations.addLine(absolutePath, 3);
        locations.addLine(absolutePath, 4);
        locations.addLine(absolutePath, 5);

        GitBlamer gitBlamer = createBlamer();

        Blames blames = gitBlamer.blame(locations);

        assertThat(blames.getFiles()).isNotEmpty().containsExactly(absolutePath);
        assertThat(blames.getErrorMessages()).isEmpty();
        assertThat(blames.getInfoMessages()).contains("-> blamed authors of issues in 1 files");

        FileBlame request = blames.getBlame(absolutePath);
        assertThat(request).hasFileName(absolutePath);

        assertThatBlameIsEmpty(request, 1);
        assertThatBlameIs(request, 2);
        assertThatBlameIsHeadWith(request, 3);
        assertThatBlameIsHeadWith(request, 4);
        assertThatBlameIs(request, 5);
        assertThatBlameIsEmpty(request, 6);
    }

    /**
     * Verifies that the last committer of the whole file is used if no specific line number is given.
     */
    @Test @Issue("JENKINS-59252")
    public void shouldAssignLastCommitterIfNoLineNumberIsGiven() {
        create2RevisionsWithDifferentAuthors();

        FileLocations locations = new FileLocations(sampleRepo.getRoot());
        String absolutePath = locations.getWorkspace() + FILE_NAME;
        locations.addLine(absolutePath, 0);

        GitBlamer gitBlamer = createBlamer();

        Blames blames = gitBlamer.blame(locations);

        assertThat(blames.getFiles()).isNotEmpty().containsExactly(absolutePath);
        assertThat(blames.getErrorMessages()).isEmpty();
        assertThat(blames.getInfoMessages()).contains("-> blamed authors of issues in 1 files");

        FileBlame request = blames.getBlame(absolutePath);
        assertThat(request).hasFileName(absolutePath);

        assertThat(request.getName(0)).isEqualTo(BAR_NAME);
        assertThat(request.getEmail(0)).isEqualTo(BAR_EMAIL);
        assertThat(request.getCommit(0)).isEqualTo(getHead());
    }

    private GitBlamer createBlamer() {
        try {
            GitSCM scm = new GitSCM(
                    GitSCM.createRepoList("file:///" + sampleRepo.getRoot(), null),
                    Collections.emptyList(), false, Collections.emptyList(),
                    null, null, Collections.emptyList());
            Run run = mock(Run.class);
            Job job = mock(Job.class);
            when(run.getParent()).thenReturn(job);

            GitClient gitClient = scm.createClient(TaskListener.NULL, new EnvVars(), run,
                    new FilePath(sampleRepo.getRoot()));
            return new GitBlamer(gitClient, "HEAD");
        }
        catch (IOException | InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    private void create2RevisionsWithDifferentAuthors() {
        writeFile(FILE_NAME, "OLD\nOLD\nOLD\nOLD\nOLD\nOLD\n");
        git("add", FILE_NAME);
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        git("commit", "--message=Init");
        git("rev-parse", "HEAD");

        writeFile(FILE_NAME, "OLD\nOLD\nNEW\nNEW\nOLD\nOLD\n");
        git("add", FILE_NAME);
        git("config", "user.name", BAR_NAME);
        git("config", "user.email", BAR_EMAIL);
        git("commit", "--message=Change");
        git("rev-parse", "HEAD");
    }

    private void assertThatBlameIsHeadWith(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo(BAR_NAME);
        assertThat(request.getEmail(line)).isEqualTo(BAR_EMAIL);
        assertThat(request.getCommit(line)).isEqualTo(getHead());
    }

    private void assertThatBlameIs(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo(FOO_NAME);
        assertThat(request.getEmail(line)).isEqualTo(FOO_EMAIL);
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }

    private void assertThatBlameIsEmpty(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo("-");
        assertThat(request.getEmail(line)).isEqualTo("-");
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }
}
