# jgitver-maven-plugin (forked by Talentia)

Forked from [jgitver/jgitver-maven-plugin](https://github.com/jgitver/jgitver-maven-plugin)
At the same time, we have posted an issue to start reflecting to a solution in the original project [here](https://github.com/jgitver/jgitver-maven-plugin/issues/156)

## Why a fork ?
This fork adds support for multi-modules flat directory structure. This layout corresponds to monorepo pattern i.e. several projects in a git repository, all in their own directory, at the root of the git repository.

The original project uses directory tree structure to identify projects that have to be managed with the calculated version.
This approach supposes that the "aggregator" project directory (called multiModuleDirectory in the code) is an ancestor directory of all other projects which version is to be managed by the jgitver plugin.
This is not compatible with the flat directory layout where all projects are sibling directories, child of the git repository directory.

The assumption of the implemented solution is that, given that the goal of this plugin is to calculate a version code, based on the state of the git repository,
only projects that are managed in this git repository are eligible to be impacted by the process, whatever is the directory structure of all these projects.


## Changes

### class JGitverModelProcessor
Replace condition that determine if the project must be processed (two places) : 
```
StringUtils.containsIgnoreCase(
          relativePath.getCanonicalPath(), multiModuleDirectory.getCanonicalPath())
```
by
```
StringUtils.containsIgnoreCase(
          relativePath.getCanonicalPath(), multiModuleDirectory.getParentFile().getCanonicalPath())
```

Remove the conditional registering of the flatten-maven-plugin, in order to execute it on all projects
`//if (relativePath.getCanonicalPath().equals(multiModuleDirectory.getCanonicalPath())) {`

Set the version of the flatten-maven-plugin to the last one (faced bug has been fixed in it)
`flattenPlugin.setVersion(System.getProperty("jgitver.flatten.version", "1.0.1"));`
replaced by
`flattenPlugin.setVersion(System.getProperty("jgitver.flatten.version", "1.2.2"));`


### class JGitverUtils
Replace the declared original groupID by our own one
`public static final String EXTENSION_GROUP_ID = "fr.brouillard.oss";`
by
`public static final String EXTENSION_GROUP_ID = "com.tswe.tcf";`

### pom.xml
Force the version of the produced artifact to the one we want to use 
`<version>0</version>`
replaced by
`<version>1.7.1</version>`

Inhibit the spotless-maven-plugin execution, to avoid build failure (to be investigated later)

### README.md
Add a link to this file

## Building the forked plugin artifact
inhibit execution of the plugin on itself by launching maven with the user property `jgitver.skip=true`

## Usage with the flat project layout context
The use-case
git workdir content
- bom (declare external dependencies versions)
- aggregator (declare modules, used for global build on the developer local machine)
- parent project (importing bom project, share maven configuration for all other projects by being parent)
- project 1
- project i ( having parent project as parent, potentially referencing sibling projects)
- project n

As we want to be able to build any project alone (after having built globally), we also need the parent version to be resolved and stored correctly for each installed artifact in the maven repository.
Currently, the only solution is to execute the flatten-maven-plugin on each project by launching maven with the user property `jgitver.flatten=true`. Indeed, using the 
jgitver.resolve-project-version=true property don't manage the version inside the parent declaration.

Create a .mvn directory inside each project with this content
A jgitver.config.xml file
``` <configuration
	xmlns="http://jgitver.github.io/maven/configuration/1.1.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://jgitver.github.io/maven/configuration/1.1.0 https://jgitver.github.io/maven/configuration/jgitver-configuration-v1_1_0.xsd">
	<strategy>MAVEN</strategy>
	<gitCommitIdLength>8</gitCommitIdLength>  <!-- between [8,40] -->
	<nonQualifierBranches>master,main</nonQualifierBranches> <!-- comma separated, example "master,integration" -->
</configuration>
```
An extensions.xml file
```<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>com.tswe.tcf</groupId>
    <artifactId>jgitver-maven-plugin</artifactId>
    <version>1.7.1</version>
  </extension>
</extensions>
```
