package cz.bliksoft.meshcorecompanion;

import cz.bliksoft.javautils.modules.IVersionInfo;

/**
 * Generated at build time – DO NOT EDIT.
 */
public final class VersionInfo implements IVersionInfo {

    @Override
    public String getArtifactId() {
        return "bsmeshcorecompanion-desktop";
    }

    @Override
    public String getGroupId() {
        return "cz.bliksoft.meshcorecompanion";
    }

    @Override
    public String getVersion() {
        return "0.0.1-SNAPSHOT";
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
