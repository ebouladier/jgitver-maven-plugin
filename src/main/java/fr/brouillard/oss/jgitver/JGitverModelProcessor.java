/*
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
package fr.brouillard.oss.jgitver;

import fr.brouillard.oss.jgitver.metadata.Metadatas;
import fr.brouillard.oss.jgitver.mojos.JGitverAttachModifiedPomsMojo;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Scm;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

;

/** Replacement ModelProcessor using jgitver while loading POMs in order to adapt versions. */
@Component(role = ModelProcessor.class)
public class JGitverModelProcessor extends DefaultModelProcessor {
  public static final String FLATTEN_MAVEN_PLUGIN = "flatten-maven-plugin";
  public static final String ORG_CODEHAUS_MOJO = "org.codehaus.mojo";
  @Requirement private Logger logger = null;

  @Requirement private LegacySupport legacySupport = null;

  @Requirement private JGitverConfiguration configurationProvider;

  @Requirement private JGitverSessionHolder jgitverSession;

  public JGitverModelProcessor() {
    super();
  }

  @Override
  public Model read(File input, Map<String, ?> options) throws IOException {
    return provisionModel(super.read(input, options), options);
  }

  @Override
  public Model read(Reader input, Map<String, ?> options) throws IOException {
    return provisionModel(super.read(input, options), options);
  }

  @Override
  public Model read(InputStream input, Map<String, ?> options) throws IOException {
    return provisionModel(super.read(input, options), options);
  }

  private Model provisionModel(Model model, Map<String, ?> options) throws IOException {
    MavenSession session = legacySupport.getSession();
    boolean shouldUseFlattenPlugin = JGitverUtils.shouldUseFlattenPlugin(session);

    Optional<JGitverSession> optSession = jgitverSession.session();
    if (!optSession.isPresent()) {
      // don't do anything in case no jgitver is there (execution could have been skipped)
      return model;
    } else {

      Source source = (Source) options.get(ModelProcessor.SOURCE);
      if (source == null) {
        return model;
      }

      File location = new File(source.getLocation());
      if (configurationProvider.ignore(location)) {
        logger.debug("file " + location + " ignored by configuration");
        return model;
      }

      logger.info("provisionModel is processing " + location);
      JGitverSession jgitverSession = optSession.get();
      String calculatedVersion = jgitverSession.getVersion();

      GAV projectGAV = GAV.from(model.clone());
      String projectGroupID = projectGAV.getGroupId();

      // First call of the ModelProcessor
      if (null == jgitverSession.getRootProjectGroupID()) {
        // store the groupId of multiModuleDirectory pom project in the session
        jgitverSession.setRootProjectGroupID(projectGroupID);
        addArtifact(projectGAV, jgitverSession, model.getVersion());
      } else {
        // if groupId is not equal to the root processed project groupId, do nothing
        if (!jgitverSession.getRootProjectGroupID().equals(projectGroupID)) {
          logger.debug("skipping Model from " + location);
          return model;
        }
      }

      // the pom is processed only if it already belongs to the list of projects
      if (jgitverSession.getProjects().contains(projectGAV)) {
        // In case of modules, store declared modules
        if (Objects.nonNull(model.getModules()) && location.isFile()) {
          File relativePath = location.getParentFile().getCanonicalFile();

          model
              .getModules()
              .forEach(
                  moduleRelPath -> {
                    try {
                      String pomPath =
                          relativePath.getCanonicalPath() + File.separator + moduleRelPath;
                      File modulePomFile = new File(pomPath);
                      if (modulePomFile.exists()) {
                        // if path is a directory, transform it to the pom file in it
                        if (modulePomFile.isDirectory()) {
                          modulePomFile = new File(pomPath + File.separator + "pom.xml");
                        }
                        // read the module pom file to extract its artifact identifier (without
                        // version)
                        if (modulePomFile.canRead()) {
                          try {
                            Model moduleModel = JGitverUtils.loadInitialModel(modulePomFile);
                            GAV moduleGAV = GAV.from(moduleModel.clone());
                            addArtifact(moduleGAV, jgitverSession, moduleModel.getVersion());
                          } catch (XmlPullParserException e) {
                            logger.warn("impossible to parse module pom file " + pomPath);
                          }

                          jgitverSession.addModulePomPath(modulePomFile.getCanonicalPath());
                          logger.debug("adding module " + modulePomFile.getCanonicalPath());
                        }
                      } else {
                        logger.info("impossible to read module " + pomPath);
                      }

                    } catch (IOException e) {
                      // TODO Auto-generated catch block
                      e.printStackTrace();
                    }
                  });
        }

        // change version of project
        String initialProjectVersion = null;
        if (Objects.nonNull(model.getVersion())) {
          initialProjectVersion = model.getVersion();
          model.setVersion(calculatedVersion);
        }

        // change version of parent project if parent project already belongs to the list or if it
        // shares the same version as the current project
        String initialParentProjectVersion = null;
        if (Objects.nonNull(model.getParent())) {
          initialParentProjectVersion = model.getParent().getVersion();
          if (jgitverSession.getProjects().contains(GAV.from(model.getParent()))
              || isVersionShared(initialProjectVersion, initialParentProjectVersion)) {
            GAV parentGAV = GAV.from(model.getParent());
            addArtifact(parentGAV, jgitverSession, initialParentProjectVersion);
            model.getParent().setVersion(calculatedVersion);
          }
        }

        // change version of dependencies if depdendency already belongs to the list or if it shares
        // the same version as the current project
        if (Objects.nonNull(model.getDependencies())) {
          for (Dependency dependency : model.getDependencies()) {
            GAV dependencyGAV = GAV.from(dependency);
            if (jgitverSession.getProjects().contains(dependencyGAV)
                || (isDefinedAsVariable(dependency.getVersion())
                    && dependency.getVersion().equals(initialProjectVersion))) {

              addArtifact(dependencyGAV, jgitverSession, initialParentProjectVersion);
              dependency.setVersion(calculatedVersion);
            }
          }
        }

        // manage adding flatten plugin once
        if (null == jgitverSession.isManagedWithPlugin(projectGAV)) {
          jgitverSession.setManagedWithPlugin(
              GAV.from(model), managePluginAddition(model, shouldUseFlattenPlugin));
        }

        updateScmTag(jgitverSession.getCalculator(), model);
      }

      try {
        session
            .getUserProperties()
            .put(
                JGitverUtils.SESSION_MAVEN_PROPERTIES_KEY,
                JGitverSession.serializeTo(jgitverSession));
      } catch (Exception ex) {
        throw new IOException("cannot serialize JGitverSession", ex);
      }
    }

    return model;
  }

  /**
   * @param gav identifier of the artifiact to add
   * @param jgitverSession
   * @param version
   */
  private void addArtifact(GAV gav, JGitverSession jgitverSession, String version) {
    if (!jgitverSession.getProjects().contains(gav)) {
      jgitverSession.addProject(gav);
      logger.info("handling version " + version + " of artifact " + gav.toString());
    }
  }

  /**
   * Manage adding maven-flatten-plugin, jgitver-maven-plugin or none to plugins list attached to
   * the project.
   *
   * @param model
   * @param shouldUseFlattenPlugin
   * @return true if it has to be managed by one of the two plugins, false if not
   */
  private boolean managePluginAddition(Model model, boolean shouldUseFlattenPlugin) {
    boolean flag = false;
    if (shouldUseFlattenPlugin) {
      try {
        if (shouldSkipPomUpdate(model)) {
          logger.info(
              "skipPomUpdate property is activated, jgitver will not define any maven-flatten-plugin execution");
        } else {
          if (isFlattenPluginDirectlyUsed(model)) {
            logger.info(
                "maven-flatten-plugin detected, jgitver will not define it's own execution");
          } else {
            logger.info(
                "adding maven-flatten-plugin execution with jgitver defaults to "
                    + GAV.from(model).toString());
            addFlattenPlugin(model);
            flag = true;
          }
        }
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    } else {
      logger.info("adding jgitver-maven-plugin execution to " + GAV.from(model).toString());
      addAttachPomMojo(model);
      flag = true;
    }
    return flag;
  }

  /**
   * Check if the project and the parent definition are enforced to use a same version : This is the
   * case if the project version is inherited from the parent (project version is not defined), or
   * if both use the same variable, or if parent definition uses ${project.version} as version
   * content.
   *
   * @param projectVersion
   * @param parentProjectVersion
   * @return true if sharing is enforced, false if not
   */
  private boolean isVersionShared(String projectVersion, String parentProjectVersion) {
    if (null == projectVersion
        || (isDefinedAsVariable(projectVersion) && projectVersion.equals(parentProjectVersion))
        || JGitverUtils.PROJECT_VERSION.equals(parentProjectVersion)) {
      return true;
    } else {
      return false;
    }
  }

  private boolean isDefinedAsVariable(String item) {
    if (null != item && item.startsWith("${") && item.endsWith("}")) {
      return true;
    } else {
      return false;
    }
  }

  private void addFlattenPlugin(Model model) {
    ensureBuildWithPluginsExistInModel(model);

    Plugin flattenPlugin = new Plugin();
    flattenPlugin.setGroupId(ORG_CODEHAUS_MOJO);
    flattenPlugin.setArtifactId(FLATTEN_MAVEN_PLUGIN);
    flattenPlugin.setVersion(System.getProperty("jgitver.flatten.version", "1.2.2"));

    PluginExecution flattenPluginExecution = new PluginExecution();
    flattenPluginExecution.setId("jgitver-flatten-pom");
    flattenPluginExecution.addGoal("flatten");
    flattenPluginExecution.setPhase(
        System.getProperty("jgitver.pom-replacement-phase", "validate"));

    flattenPlugin.getExecutions().add(flattenPluginExecution);

    Xpp3Dom executionConfiguration = buildFlattenPluginConfiguration();
    flattenPluginExecution.setConfiguration(executionConfiguration);
    model.getBuild().getPlugins().add(flattenPlugin);
  }

  private void ensureBuildWithPluginsExistInModel(Model model) {
    if (Objects.isNull(model.getBuild())) {
      model.setBuild(new Build());
    }

    if (Objects.isNull(model.getBuild().getPlugins())) {
      model.getBuild().setPlugins(new ArrayList<>());
    }
  }

  private Xpp3Dom buildFlattenPluginConfiguration() {
    Xpp3Dom configuration = new Xpp3Dom("configuration");

    Xpp3Dom flattenMode = new Xpp3Dom("flattenMode");
    flattenMode.setValue("defaults");

    Xpp3Dom updatePomFile = new Xpp3Dom("updatePomFile");
    updatePomFile.setValue("true");

    Xpp3Dom pomElements = new Xpp3Dom("pomElements");

    Xpp3Dom dependencyManagement = new Xpp3Dom("dependencyManagement");
    dependencyManagement.setValue("keep");
    pomElements.addChild(dependencyManagement);

    List<String> pomElementsName =
        Arrays.asList(
            "build",
            "ciManagement",
            "contributors",
            "dependencies",
            "description",
            "developers",
            "distributionManagement",
            "inceptionYear",
            "issueManagement",
            "mailingLists",
            "modules",
            "name",
            "organization",
            "parent",
            "pluginManagement",
            "pluginRepositories",
            "prerequisites",
            "profiles",
            "properties",
            "reporting",
            "repositories",
            "scm",
            "url",
            "version");

    pomElementsName.forEach(
        elementName -> {
          Xpp3Dom node = new Xpp3Dom(elementName);
          node.setValue("resolve");
          pomElements.addChild(node);
        });

    configuration.addChild(flattenMode);
    configuration.addChild(updatePomFile);
    configuration.addChild(pomElements);

    return configuration;
  }

  private boolean shouldSkipPomUpdate(Model model) throws IOException {
    try {
      return configurationProvider.getConfiguration().skipPomUpdate;
    } catch (MavenExecutionException mee) {
      throw new IOException("cannot load jgitver configuration", mee);
    }
  }

  private boolean isFlattenPluginDirectlyUsed(Model model) {
    Predicate<Plugin> isFlattenPlugin =
        p ->
            ORG_CODEHAUS_MOJO.equals(p.getGroupId())
                && FLATTEN_MAVEN_PLUGIN.equals(p.getArtifactId());

    List<Plugin> pluginList =
        Optional.ofNullable(model.getBuild())
            .map(Build::getPlugins)
            .orElse(Collections.emptyList());

    return pluginList.stream().filter(isFlattenPlugin).findAny().isPresent();
  }

  private void updateScmTag(JGitverInformationProvider calculator, Model model) {
    if (model.getScm() != null) {
      Scm scm = model.getScm();
      if (isVersionFromTag(calculator)) {
        scm.setTag(calculator.getVersion());
      } else {
        calculator.meta(Metadatas.GIT_SHA1_FULL).ifPresent(scm::setTag);
      }
    }
  }

  private boolean isVersionFromTag(JGitverInformationProvider calculator) {
    List<String> versionTagsOnHead =
        Arrays.asList(calculator.meta(Metadatas.HEAD_VERSION_ANNOTATED_TAGS).orElse("").split(","));
    String baseTag = calculator.meta(Metadatas.BASE_TAG).orElse("");
    return versionTagsOnHead.contains(baseTag);
  }

  private void addAttachPomMojo(Model model) {
    ensureBuildWithPluginsExistInModel(model);

    Optional<Plugin> pluginOptional =
        model.getBuild().getPlugins().stream()
            .filter(
                x ->
                    JGitverUtils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                        && JGitverUtils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
            .findFirst();

    StringBuilder pluginVersion = new StringBuilder();

    try (InputStream inputStream =
        getClass()
            .getResourceAsStream(
                "/META-INF/maven/"
                    + JGitverUtils.EXTENSION_GROUP_ID
                    + "/"
                    + JGitverUtils.EXTENSION_ARTIFACT_ID
                    + "/pom"
                    + ".properties")) {
      Properties properties = new Properties();
      properties.load(inputStream);
      pluginVersion.append(properties.getProperty("version"));
    } catch (IOException ignored) {
      // TODO we should not ignore in case we have to reuse it
      logger.warn(ignored.getMessage(), ignored);
    }

    Plugin plugin =
        pluginOptional.orElseGet(
            () -> {
              Plugin plugin2 = new Plugin();
              plugin2.setGroupId(JGitverUtils.EXTENSION_GROUP_ID);
              plugin2.setArtifactId(JGitverUtils.EXTENSION_ARTIFACT_ID);
              plugin2.setVersion(pluginVersion.toString());

              model.getBuild().getPlugins().add(0, plugin2);
              return plugin2;
            });

    if (Objects.isNull(plugin.getExecutions())) {
      plugin.setExecutions(new ArrayList<>());
    }

    String pluginRunPhase = System.getProperty("jgitver.pom-replacement-phase", "prepare-package");
    Optional<PluginExecution> pluginExecutionOptional =
        plugin.getExecutions().stream()
            .filter(x -> pluginRunPhase.equalsIgnoreCase(x.getPhase()))
            .findFirst();

    PluginExecution pluginExecution =
        pluginExecutionOptional.orElseGet(
            () -> {
              PluginExecution pluginExecution2 = new PluginExecution();
              pluginExecution2.setPhase(pluginRunPhase);

              plugin.getExecutions().add(pluginExecution2);
              return pluginExecution2;
            });

    if (Objects.isNull(pluginExecution.getGoals())) {
      pluginExecution.setGoals(new ArrayList<>());
    }

    if (!pluginExecution
        .getGoals()
        .contains(JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS)) {
      pluginExecution.getGoals().add(JGitverAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS);
    }

    if (Objects.isNull(plugin.getDependencies())) {
      plugin.setDependencies(new ArrayList<>());
    }

    Optional<Dependency> dependencyOptional =
        plugin.getDependencies().stream()
            .filter(
                x ->
                    JGitverUtils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                        && JGitverUtils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
            .findFirst();

    dependencyOptional.orElseGet(
        () -> {
          Dependency dependency = new Dependency();
          dependency.setGroupId(JGitverUtils.EXTENSION_GROUP_ID);
          dependency.setArtifactId(JGitverUtils.EXTENSION_ARTIFACT_ID);
          dependency.setVersion(pluginVersion.toString());

          plugin.getDependencies().add(dependency);
          return dependency;
        });
  }
}
