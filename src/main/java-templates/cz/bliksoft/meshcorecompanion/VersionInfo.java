package cz.bliksoft.meshcorecompanion;

import cz.bliksoft.javautils.modules.IVersionInfo;

/**
 * Generated at build time – DO NOT EDIT.
 */
public final class VersionInfo implements IVersionInfo {

    @Override
    public String getArtifactId() {
        return "@project.artifactId@";
    }

    @Override
    public String getGroupId() {
        return "@project.groupId@";
    }

    @Override
    public String getVersion() {
        return "@project.version@";
    }

    @Override
    public String getBranch() {
        return "@git.branch@";
    }

    @Override
    public String getCommitIdAbbrev() {
        return "@git.commit.id.abbrev@";
    }

    @Override
    public String getTags() {
        return "@git.tags@";
    }

    @Override
    public String getClosestTag() {
        return "@git.closest.tag.name@";
    }

    @Override
    public String getClosestTagCommitCount() {
        return "@git.closest.tag.commit.count@";
    }
}
