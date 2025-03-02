/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.ajdt;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectUtils;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.m2e.jdt.IClasspathEntryDescriptor;
import org.eclipse.m2e.jdt.internal.AbstractJavaProjectConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Configures AJDT project according to aspectj-maven-plugin configuration from pom.xml. Work in progress, most of
 * aspectj-maven-plugin configuration parameters is not supported yet.
 *
 * @see "http://mojo.codehaus.org/aspectj-maven-plugin/compile-mojo.html"
 * @see "https://bugs.eclipse.org/bugs/show_bug.cgi?id=160393"
 * @author Igor Fedorenko
 * @author Eugene Kuleshov
 */
public class AjdtProjectConfigurator extends AbstractJavaProjectConfigurator {

  private static final String SRC_MAIN_ASPECT = "src/main/aspect";

  private static final Logger log = LoggerFactory.getLogger(AjdtProjectConfigurator.class);

  private static final String GOAL_COMPILE = "compile";

  private static final String GOAL_TESTCOMPILE = "testCompile";

  public static final String COMPILER_PLUGIN_ARTIFACT_ID = "aspectj-maven-plugin";

  public static final List<String> COMPILER_PLUGIN_GROUP_IDS = Arrays.asList("org.codehaus.mojo", "com.nickwongdev",
      "com.github.m50d", "se.haleby.aspectj", "io.starter", "dev.aspectj");

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
    IProject project = request.mavenProjectFacade().getProject(); 

    configureNature(project, monitor);
  }

  @Override
  public void configureClasspath(IMavenProjectFacade facade, IClasspathDescriptor classpath, IProgressMonitor monitor) {
    IProject project = facade.getProject();
    // TODO cache in facade.setSessionProperty
    AspectJPluginConfiguration config;
    try {
    config = AspectJPluginConfiguration.create( //
        facade.getMavenProject(monitor), project);
    }
    catch(CoreException ce) {
      throw new IllegalStateException(ce);
    }
    if(config != null) {
      for(IClasspathEntryDescriptor descriptor : classpath.getEntryDescriptors()) {
        String key = descriptor.getGroupId() + ":" + descriptor.getArtifactId();
        Set<String> aspectLibraries = config.getAspectLibraries(); // from pom.xml
        if(aspectLibraries != null && aspectLibraries.contains(key)) {
          //descriptor.addClasspathAttribute(AspectJCorePreferences.ASPECTPATH_ATTRIBUTE);
          descriptor.getClasspathAttributes().put(AspectJCorePreferences.ASPECTPATH_ATTRIBUTE_NAME,
              AspectJCorePreferences.ASPECTPATH_ATTRIBUTE_NAME);
          continue;
        }
        Set<String> inpathDependencies = config.getInpathDependencies();
        if(inpathDependencies != null && inpathDependencies.contains(key)) {
          //descriptor.addClasspathAttribute(AspectJCorePreferences.INPATH_ATTRIBUTE);
          descriptor.getClasspathAttributes().put(AspectJCorePreferences.INPATH_ATTRIBUTE_NAME,
              AspectJCorePreferences.INPATH_ATTRIBUTE_NAME);
        }
      }
    }
  }

  protected List<MojoExecution> getCompilerMojoExecutions(ProjectConfigurationRequest request, IProgressMonitor monitor)
      throws CoreException {
    List<MojoExecution> execs = new ArrayList<>();
    for(String groupId : COMPILER_PLUGIN_GROUP_IDS) {
      execs.addAll(request.mavenProjectFacade().getMojoExecutions(groupId, COMPILER_PLUGIN_ARTIFACT_ID, monitor,
          GOAL_COMPILE, GOAL_TESTCOMPILE));
    }
    return execs;
  }

  protected boolean isTestCompileExecution(MojoExecution execution) {
    return GOAL_TESTCOMPILE.equals(execution.getGoal());
  }

  protected boolean isCompileExecution(MojoExecution execution) {
    return GOAL_COMPILE.equals(execution.getGoal());
  }

  private IPath[] toPaths(String[] values) {
    if(values == null) {
      return new IPath[0];
    }
    IPath[] paths = new IPath[values.length];
    for(int i = 0; i < values.length; i++ ) {
      if(values[i] != null && !"".equals(values[i].trim())) {
        paths[i] = new Path(values[i]);
      }
    }
    return paths;
  }

  // copied from superclass, but uses AJ source filters
  @Override
  public void configureRawClasspath(ProjectConfigurationRequest request, IClasspathDescriptor classpath,
      IProgressMonitor monitor) throws CoreException {
    SubMonitor mon = SubMonitor.convert(monitor, 6);

    IMavenProjectFacade facade = request.mavenProjectFacade();

    IPath[] inclusion = new IPath[0];
    IPath[] exclusion = new IPath[0];

    // not handling test folders
//    IPath[] inclusionTest = new IPath[0];
//    IPath[] exclusionTest = new IPath[0];

    // not doing anything with encoding right now
//    String mainSourceEncoding = null;
//    String testSourceEncoding = null;

    MavenProject mproject = request.mavenProjectFacade().getMavenProject();

    List<MojoExecution> executions = getCompilerMojoExecutions(request, mon.newChild(1));
    for(MojoExecution compile : executions) {
      if(isCompileExecution(compile)) {
//        mainSourceEncoding = maven.getMojoParameterValue(mavenSession, compile, "encoding", String.class); //$NON-NLS-1$
        try {
          inclusion = toPaths(
              maven.getMojoParameterValue(mproject, compile, "includes", String[].class, mon)); //$NON-NLS-1$
        } catch(CoreException ex) {
          log.error("Failed to determine compiler inclusions, assuming defaults", ex);
        }
        try {
          exclusion = toPaths(
              maven.getMojoParameterValue(mproject, compile, "excludes", String[].class, mon)); //$NON-NLS-1$
        } catch(CoreException ex) {
          log.error("Failed to determine compiler exclusions, assuming defaults", ex);
        }
      }

      // we are not supporting test folders
//      if(isTestCompileExecution(compile)) {
//        testSourceEncoding = maven.getMojoParameterValue(mavenSession, compile, "encoding", String.class); //$NON-NLS-1$
//        try {
//          inclusionTest = toPaths(maven.getMojoParameterValue(request.getMavenSession(), compile,
//              "testIncludes", String[].class)); //$NON-NLS-1$
//        } catch(CoreException ex) {
//          log.error("Failed to determine compiler test inclusions, assuming defaults", ex);
//        }
//        try {
//          exclusionTest = toPaths(maven.getMojoParameterValue(request.getMavenSession(), compile,
//              "testExcludes", String[].class)); //$NON-NLS-1$
//        } catch(CoreException ex) {
//          log.error("Failed to determine compiler test exclusions, assuming defaults", ex);
//        }
//      }
    }

    assertHasNature(request.mavenProjectFacade().getProject(), JavaCore.NATURE_ID);

    for(MojoExecution mojoExecution : getMojoExecutions(request, monitor)) {
      File[] sources = getSourceFolders(request, mojoExecution, monitor);

      for(File source : sources) {
        IPath sourcePath = getFullPath(facade, source);

        if(sourcePath != null) {
          classpath.addSourceEntry(sourcePath, facade.getOutputLocation(), inclusion, exclusion, true);
        }
      }
    }
  }
  
  protected IPath getFullPath(IMavenProjectFacade facade, File file) {
    IProject project = facade.getProject();
    IPath path = MavenProjectUtils.getProjectRelativePath(project, file.getAbsolutePath());
    return project.getFullPath().append(path);
  }

  protected File[] getSourceFolders(ProjectConfigurationRequest request, MojoExecution mojoExecution,IProgressMonitor monitor)
      throws CoreException {

    // note: don't check for the aj nature here since this method may be called before the configure method.
    File[] sourceFolders = new File[0];
    File value = getParameterValue(request.mavenProject(), "aspectDirectory", File.class, mojoExecution, monitor);
    
    if(value != null) {
      IMavenProjectFacade facade = request.mavenProjectFacade();
      IPath path = getFullPath(facade, value);
      if(value.exists()) {
        log.info("Found aspect source folder " + path);
        sourceFolders = new File[] {value};
      } else {
        log.warn("File " + path + " does not exist yet. Create it and re-run configuration.");
      }
    } else {
      log.info("No aspect source folder found. Failing back to 'src/main/aspect'");
      value = new File(SRC_MAIN_ASPECT);
    }
    return sourceFolders;
  }

  static boolean isAjdtProject(IProject project) {
    try {
      return project != null && project.isAccessible() && project.hasNature(AspectJPlugin.ID_NATURE);
    } catch(CoreException e) {
      return false;
    }
  }

  private void configureNature(IProject project, IProgressMonitor monitor) throws CoreException {
    // Have to do this, since this may run before the jdt configurer
    if(!project.hasNature(JavaCore.NATURE_ID)) {
      addNature(project, JavaCore.NATURE_ID, monitor);
    }

    if(!project.hasNature(AspectJPlugin.ID_NATURE)) {
      addNature(project, AspectJPlugin.ID_NATURE, monitor);
    }
  }
}
