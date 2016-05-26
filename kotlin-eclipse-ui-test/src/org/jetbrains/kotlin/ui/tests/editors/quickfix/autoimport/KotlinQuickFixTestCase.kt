package org.jetbrains.kotlin.ui.tests.editors.quickfix.autoimport

import org.jetbrains.kotlin.testframework.editor.KotlinEditorWithAfterFileTestCase
import org.junit.Before
import org.jetbrains.kotlin.testframework.utils.KotlinTestUtils
import org.eclipse.ui.ide.IDE
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.jface.text.source.ISourceViewer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.ui.editors.annotations.DiagnosticAnnotation
import org.jetbrains.kotlin.ui.editors.annotations.endOffset
import org.eclipse.jface.text.source.IAnnotationModel
import org.jetbrains.kotlin.ui.editors.quickfix.KotlinMarkerResolutionGenerator
import org.jetbrains.kotlin.ui.editors.annotations.AnnotationManager
import org.jetbrains.kotlin.eclipse.ui.utils.getBindingContext
import org.jetbrains.kotlin.ui.editors.KotlinFileEditor
import org.jetbrains.kotlin.core.builder.KotlinPsiManager
import org.junit.Assert
import org.jetbrains.kotlin.testframework.utils.EditorTestUtils
import org.jetbrains.kotlin.eclipse.ui.utils.LineEndUtil
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.core.resources.IFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.ui.editors.quickfix.KotlinMarkerResolution
import org.eclipse.jface.text.IDocument
import org.jetbrains.kotlin.testframework.editor.TextEditorTest
import org.jetbrains.kotlin.ui.editors.findDiagnosticsBy
import org.eclipse.jface.text.source.TextInvocationContext

private val expectedQuickFixRegex = "\"(.*?)\"".toRegex()

abstract class KotlinQuickFixTestCase : KotlinEditorWithAfterFileTestCase() {
    
    @Before
    fun before() {
        configureProject();
    }
    
    override fun performTest(fileText: String, expectedFileText: String) {
        val firstLine = fileText.split("\n")[0]
        val splittedLine = expectedQuickFixRegex.find(firstLine)
        val expectedLabel = splittedLine!!.groups[1]!!.value
        
        val foundProposals = getProposals(testEditor)
        val resolution = foundProposals.find { it.label == expectedLabel }
        
        Assert.assertNotNull(
                "Expected proposal with label \"$expectedLabel\" wasn't found. Found proposals:\n${foundProposals.joinToString("\n") { it.label}}",
                resolution)
        
        resolution!!.apply(testEditor.getEditingFile())
        
        EditorTestUtils.assertByEditor(testEditor.getEditor(), expectedFileText);
    }
}

fun getProposals(testEditor: TextEditorTest): List<KotlinMarkerResolution> {
    val editor = testEditor.getEditor() as KotlinFileEditor
    val diagnostics = findDiagnosticsBy(TextInvocationContext(editor.viewer, testEditor.caretOffset, 0), editor)
    
    return KotlinMarkerResolutionGenerator.getResolutions(diagnostics)
}