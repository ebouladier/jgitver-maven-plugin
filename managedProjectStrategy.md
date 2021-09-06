# Strategy that determine which projects are processed by the version replacement

The strategy depends on the content of the jgitver session and in case of multi modules, their order of processing by the JGitverModelProcessor (which is not the one calculated by Maven but their order in the modules declaration).
It could be enforced that only project that share the same groupId with the project being built are processed.
Currently, if projects belongs to several git repositories, the version is only calculated once using the git repository of the first project on which maven is launched.

## How to determine parent project version and project version

### If the project and the parent definition are enforced to use a same version
This is the case if the project version is inherited from the parent (project version is not defined), or if both use the same variable, or if parent definition uses ${project.version} as version content.
We consider that the parent and the project must have the same version, and this, regardless of any other considerations like sharing directory path or git repository.

### If the project version defines its own version
The parent project and the project itself can have different versions
  - If the parent project is processed in this session (modules), it already has a calculated version in this session, the parent project version must be equal to it. The version of the project is calculated separately
  (if the two projects are in the same git repository, it should be the same as the parent version). And this, regardless of any other considerations like sharing directory path or git repository.
  - If the parent version is not processed in this session, the situation is ambiguous. We should use parameters to give some indications to let the process know what is expected.
  By default :
    - If the parent and project GroupIDs are the same, the parent version is forced to project calculated one. If not, we don't change the parent version.


## How to determine dependencies version (dependencyManagement as well as dependencies sections)
The processing is the same for all dependencies : imported bom project version or any regular dependency.

### Dependency version is defined with ${project.version} Maven variable or with the same variable as the one used for the project version
The strategy is the same as the one established for a parent pom referenced in the project being built.

### Dependency version is hardly coded or use a distinct variable than the parent project one
We consider that this dependency 


## What if projects are in different git repository ?
jgitversion plugin calculate a version number for only one repository, the one that contains the first project built ("aggregator" in case on multi-modules). Even if declared modules point to right physical location 
in another git repository, it will not be calculated another related version number. 