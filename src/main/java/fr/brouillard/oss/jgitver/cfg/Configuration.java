/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.brouillard.oss.jgitver.cfg;

import org.simpleframework.xml.*;

import java.util.LinkedList;
import java.util.List;

@Root
@Default(DefaultType.FIELD)
public class Configuration {
    @Element(name = "mavenLike", required = false)
    public boolean mavenLike = true;
    @Element(required = false)
    public boolean autoIncrementPatch = true;
    @Element(required = false)
    public boolean useCommitDistance = false;
    @Element(required = false)
    public boolean useDirty = false;
    @Element(required = false)
    public boolean failIfDirty = false;
    @Element(required = false)
    public boolean useDefaultBranchingPolicy = true;
    @Element(required = false)
    public boolean useGitCommitTimestamp = false;
    @Element(required = false)
    public boolean useGitCommitId = false;
    @Element(required = false)
    public int gitCommitIdLength = 8;
    @Element(required = false)
    public String nonQualifierBranches = "master";
    @Element(required = false)
    public String regexVersionTag;
    @ElementList(name = "exclusions", entry = "exclusion", required = false)
    public List<String> exclusions = new LinkedList<>();
    @ElementList(name = "branchPolicies", entry = "branchPolicy", required = false)
    public List<BranchPolicy> branchPolicies = new LinkedList<>();
    @Attribute(required = false)
    public String schemaLocation;
}
