/*******************************************************************************
* Copyright 2000-2015 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*******************************************************************************/
package org.jetbrains.kotlin.core.filesystem

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.Collections
import org.eclipse.core.filesystem.IFileInfo
import org.eclipse.core.filesystem.IFileStore
import org.eclipse.core.filesystem.provider.FileInfo
import org.eclipse.core.internal.filesystem.local.LocalFile
import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Status
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.compiler.util.Util
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.core.asJava.KotlinLightClassGeneration
import org.jetbrains.kotlin.core.log.KotlinLogger
import org.jetbrains.kotlin.core.model.KotlinAnalysisProjectCache
import org.jetbrains.kotlin.core.resolve.KotlinAnalyzer

public class KotlinFileStore(file: File) : LocalFile(file) {
	override public fun openInputStream(options: Int, monitor: IProgressMonitor?): InputStream {
		val javaProject = getJavaProject()
		if (javaProject == null) {
			throw CoreException(Status.CANCEL_STATUS)
		}

		val jetFiles = KotlinLightClassManager.getInstance(javaProject).getSourceFiles(file)
		if (jetFiles.isNotEmpty()) {
			val analysisResult = KotlinAnalysisProjectCache.getAnalysisResultIfCached(javaProject) ?:
			KotlinAnalyzer.analyzeFiles(javaProject, jetFiles).analysisResult

			val requestedClassName = Path(file.getAbsolutePath()).lastSegment()
			val state = KotlinLightClassGeneration.buildLightClasses(analysisResult, javaProject, jetFiles, requestedClassName)
			val generatedClass = state.factory.asList().find {
				val generatedClassName = Path(it.relativePath).lastSegment()
				requestedClassName == generatedClassName
			}

			if (generatedClass != null) return ByteArrayInputStream(generatedClass.asByteArray())
		}

		throw CoreException(Status.CANCEL_STATUS)
	}

	override public fun fetchInfo(options: Int, monitor: IProgressMonitor?): IFileInfo {
		val info = super.fetchInfo(options, monitor) as FileInfo
		if (Util.isClassFileName(getName())) {
			val workspaceFile = findFileInWorkspace()
			if (workspaceFile != null) {
				info.setExists(workspaceFile.exists())
			}
		} else {
			val workspaceContainer = findFolderInWorkspace()
			if (workspaceContainer != null) {
				info.setExists(workspaceContainer.exists())
				info.setDirectory(true)
			}
		}

		return info
	}

	override public fun childNames(options: Int, monitor: IProgressMonitor?): Array<String> {
		val folder = findFolderInWorkspace()
		if (folder != null && folder.exists()) {
			return folder.members()
			.map { it.getName() }
			.toTypedArray()
		}

		return emptyArray()
	}

	override public fun mkdir(options: Int, monitor: IProgressMonitor?): IFileStore = this

	override public fun openOutputStream(options: Int, monitor: IProgressMonitor?): OutputStream = ByteArrayOutputStream()

	override public fun getChild(name: String): IFileStore = KotlinFileStore(File(file, name))

	override public fun getChild(path: IPath): IFileStore = KotlinFileStore(File(file, path.toOSString()))

	override public fun getFileStore(path: IPath): IFileStore = KotlinFileStore(Path(file.getPath()).append(path).toFile())

	override public fun getParent(): IFileStore? = file.getParentFile()?.let { KotlinFileStore(it) }

	private fun findFileInWorkspace(): IFile? {
		val files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(file.toURI())
		return if (files != null && files.isNotEmpty()) {
			assert(files.size == 1, { "By ${file.toURI()} found more than one file" })
			files[0]
		} else {
			null
		}
	}

	private fun findFolderInWorkspace(): IContainer? {
		val containers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(file.toURI())
		return if (containers != null && containers.isNotEmpty()) {
			assert(containers.size == 1, { "By ${file.toURI()} found more than one file" })
			containers[0]
		} else {
			null
		}
	}

	private fun getJavaProject(): IJavaProject? = findFileInWorkspace()?.let { JavaCore.create(it.getProject()) }
}